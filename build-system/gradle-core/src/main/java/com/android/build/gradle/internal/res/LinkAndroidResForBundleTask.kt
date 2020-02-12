/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.build.gradle.internal.res

import com.android.build.api.component.impl.ComponentPropertiesImpl
import com.android.build.api.variant.impl.BuiltArtifactsLoaderImpl
import com.android.build.api.variant.impl.VariantOutputImpl
import com.android.build.gradle.internal.LoggerWrapper
import com.android.build.gradle.internal.component.DynamicFeatureCreationConfig
import com.android.build.gradle.internal.dsl.convert
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactScope.PROJECT
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType.FEATURE_RESOURCE_PKG
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ConsumedConfigType.COMPILE_CLASSPATH
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.services.Aapt2DaemonBuildService
import com.android.build.gradle.internal.services.getAapt2DaemonBuildService
import com.android.build.gradle.internal.tasks.NonIncrementalTask
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.build.gradle.internal.utils.setDisallowChanges
import com.android.build.gradle.internal.utils.toImmutableList
import com.android.build.gradle.options.BooleanOption
import com.android.build.gradle.options.StringOption
import com.android.build.gradle.options.SyncOptions
import com.android.builder.core.VariantTypeImpl
import com.android.builder.internal.aapt.AaptOptions
import com.android.builder.internal.aapt.AaptPackageConfig
import com.android.sdklib.AndroidVersion
import com.android.utils.FileUtils
import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableSet
import org.gradle.api.artifacts.ArtifactCollection
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileCollection
import org.gradle.api.file.RegularFile
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskProvider
import java.io.File
import java.io.IOException

/**
 * Task to link app resources into a proto format so that it can be consumed by the bundle tool.
 */
@CacheableTask
abstract class LinkAndroidResForBundleTask : NonIncrementalTask() {
    @get:Input
    abstract val debuggable: Property<Boolean>

    private lateinit var aaptOptions: AaptOptions

    private lateinit var errorFormatMode: SyncOptions.ErrorFormatMode

    private var mergeBlameLogFolder: File? = null

    // Not an input as it is only used to rewrite exception and doesn't affect task output
    private lateinit var manifestMergeBlameFile: Provider<RegularFile>

    private var buildTargetDensity: String? = null

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    lateinit var androidJar: Provider<File>
        private set

    @get:OutputFile
    abstract val bundledResFile: RegularFileProperty

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    lateinit var featureResourcePackages: FileCollection
        private set

    @get:Input
    @get:Optional
    abstract val resOffset: Property<Int>

    @get:OutputDirectory
    lateinit var incrementalFolder: File
        private set

    @get:Nested
    lateinit var mainSplit: VariantOutputImpl
        private set

    @get:Input
    lateinit var aapt2Version: String
        private set

    @get:Internal
    abstract val aapt2FromMaven: ConfigurableFileCollection

    @get:Input
    abstract val excludeResSourcesForReleaseBundles: Property<Boolean>

    @get:Internal
    abstract val aapt2DaemonBuildService: Property<Aapt2DaemonBuildService>

    private var compiledDependenciesResources: ArtifactCollection? = null

    override fun doTaskAction() {

        val manifestFile =
            BuiltArtifactsLoaderImpl().load(manifestFiles)
                ?.getBuiltArtifact(mainSplit)
                ?.outputFile
                ?: throw RuntimeException("Cannot find merged manifest file")

        val outputFile = bundledResFile.get().asFile
        FileUtils.mkdirs(outputFile.parentFile)

        val featurePackagesBuilder = ImmutableList.builder<File>()
        for (featurePackage in featureResourcePackages) {
            val buildElements = BuiltArtifactsLoaderImpl.loadFromDirectory(featurePackage)
            if (buildElements?.elements?.size != 1) {
                throw IOException("Found more than one PROCESSED_RES output at $featurePackage")
            }

            featurePackagesBuilder.add(File(buildElements.elements.first().outputFile))
        }

        val compiledDependenciesResourcesDirs =
            getCompiledDependenciesResources()?.reversed()?.toImmutableList() ?: emptyList<File>()

        val config = AaptPackageConfig(
            androidJarPath = androidJar.get().absolutePath,
            generateProtos = true,
            manifestFile = File(manifestFile),
            options = aaptOptions,
            resourceOutputApk = outputFile,
            variantType = VariantTypeImpl.BASE_APK,
            packageId = resOffset.orNull,
            allowReservedPackageId = minSdkVersion < AndroidVersion.VersionCodes.O,
            dependentFeatures = featurePackagesBuilder.build(),
            resourceDirs = ImmutableList.Builder<File>().addAll(compiledDependenciesResourcesDirs)
                .add(
                    checkNotNull(getInputResourcesDir().orNull?.asFile)
                ).build(),
            resourceConfigs = ImmutableSet.copyOf(resConfig),
            // We only want to exclude res sources for release builds and when the flag is turned on
            // This will result in smaller release bundles
            excludeSources = excludeResSourcesForReleaseBundles.get() && debuggable.get().not()
        )
        if (logger.isInfoEnabled) {
            logger.info("Aapt output file {}", outputFile.absolutePath)
        }

        val aapt2ServiceKey = aapt2DaemonBuildService.get().registerAaptService(
            aapt2FromMaven = aapt2FromMaven,
            logger = LoggerWrapper(logger)
        )
        getWorkerFacadeWithWorkers().use {
            it.submit(
                Aapt2ProcessResourcesRunnable::class.java,
                Aapt2ProcessResourcesRunnable.Params(
                    aapt2ServiceKey,
                    config,
                    errorFormatMode,
                    mergeBlameLogFolder,
                    manifestMergeBlameFile.orNull?.asFile
                )
            )
        }
    }

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val manifestFiles: DirectoryProperty

    @InputFiles
    @Optional
    @PathSensitive(PathSensitivity.RELATIVE)
    abstract fun getInputResourcesDir(): DirectoryProperty

    @get:Input
    lateinit var resConfig: Collection<String> private set

    @Nested
    fun getAaptOptionsInput(): LinkingTaskInputAaptOptions {
        return LinkingTaskInputAaptOptions(aaptOptions)
    }

    /**
     * Returns a file collection of the directories containing the compiled remote libraries
     * resource files.
     */
    @Optional
    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    fun getCompiledDependenciesResources(): FileCollection? {
        return compiledDependenciesResources?.artifactFiles
    }

    @get:Input
    var minSdkVersion: Int = 1
        private set

    class CreationAction(componentProperties: ComponentPropertiesImpl) :
        VariantTaskCreationAction<LinkAndroidResForBundleTask, ComponentPropertiesImpl>(
            componentProperties
        ) {

        override val name: String
            get() = computeTaskName("bundle", "Resources")
        override val type: Class<LinkAndroidResForBundleTask>
            get() = LinkAndroidResForBundleTask::class.java

        override fun handleProvider(
            taskProvider: TaskProvider<out LinkAndroidResForBundleTask>
        ) {
            super.handleProvider(taskProvider)
            creationConfig.artifacts.producesFile(
                InternalArtifactType.LINKED_RES_FOR_BUNDLE,
                taskProvider,
                LinkAndroidResForBundleTask::bundledResFile,
                "bundled-res.ap_"
            )
        }

        override fun configure(
            task: LinkAndroidResForBundleTask
        ) {
            super.configure(task)

            val variantScope = creationConfig.variantScope
            val projectOptions = creationConfig.globalScope.projectOptions

            task.incrementalFolder = creationConfig.paths.getIncrementalDir(name)

            task.mainSplit = creationConfig.outputs.getMainSplit()

            creationConfig.operations.setTaskInputToFinalProduct(
                InternalArtifactType.BUNDLE_MANIFEST,
                task.manifestFiles)

            creationConfig.operations.setTaskInputToFinalProduct(
                InternalArtifactType.MERGED_RES,
                task.getInputResourcesDir()
            )

            task.featureResourcePackages = creationConfig.variantDependencies.getArtifactFileCollection(
                COMPILE_CLASSPATH, PROJECT, FEATURE_RESOURCE_PKG)

            if (creationConfig.variantType.isDynamicFeature && creationConfig is DynamicFeatureCreationConfig) {
                task.resOffset.set(creationConfig.resOffset)
                task.resOffset.disallowChanges()
            }

            task.debuggable.setDisallowChanges(creationConfig.variantDslInfo.isDebuggable)
            task.aaptOptions = creationConfig.globalScope.extension.aaptOptions.convert()

            task.excludeResSourcesForReleaseBundles
                .setDisallowChanges(
                    projectOptions.get(BooleanOption.EXCLUDE_RES_SOURCES_FOR_RELEASE_BUNDLES)
                )

            task.buildTargetDensity =
                    projectOptions.get(StringOption.IDE_BUILD_TARGET_DENSITY)

            task.mergeBlameLogFolder = creationConfig.paths.resourceBlameLogDir
            val (aapt2FromMaven, aapt2Version) = getAapt2FromMavenAndVersion(creationConfig.globalScope)
            task.aapt2FromMaven.from(aapt2FromMaven)
            task.aapt2Version = aapt2Version
            task.minSdkVersion = creationConfig.minSdkVersion.apiLevel

            task.resConfig = creationConfig.variantDslInfo.resourceConfigurations

            task.androidJar = creationConfig.globalScope.sdkComponents.androidJarProvider

            task.errorFormatMode = SyncOptions.getErrorFormatMode(
                creationConfig.globalScope.projectOptions
            )

            task.manifestMergeBlameFile = creationConfig.artifacts.getFinalProduct(
                InternalArtifactType.MANIFEST_MERGE_BLAME_FILE
            )

            if (variantScope.isPrecompileDependenciesResourcesEnabled) {
                task.compiledDependenciesResources = creationConfig.variantDependencies.getArtifactCollection(
                    AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH,
                    AndroidArtifacts.ArtifactScope.ALL,
                    AndroidArtifacts.ArtifactType.COMPILED_DEPENDENCIES_RESOURCES
                )
            }
            task.aapt2DaemonBuildService.set(getAapt2DaemonBuildService(task.project))
        }
    }
}
