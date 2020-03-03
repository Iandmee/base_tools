/*
 * Copyright (C) 2012 The Android Open Source Project
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
package com.android.build.gradle.tasks

import com.android.SdkConstants
import com.android.build.api.variant.BuiltArtifact
import com.android.build.api.variant.BuiltArtifacts
import com.android.build.api.variant.impl.BuiltArtifactImpl
import com.android.build.api.variant.impl.BuiltArtifactsImpl
import com.android.build.api.variant.impl.BuiltArtifactsLoaderImpl
import com.android.build.api.variant.impl.BuiltArtifactsLoaderImpl.Companion.loadFromDirectory
import com.android.build.api.variant.impl.VariantOutputImpl
import com.android.build.api.variant.impl.dirName
import com.android.build.gradle.internal.LoggerWrapper
import com.android.build.gradle.internal.component.ApkCreationConfig
import com.android.build.gradle.internal.component.DynamicFeatureCreationConfig
import com.android.build.gradle.internal.dependency.ArtifactCollectionWithExtraArtifact.ExtraComponentIdentifier
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactScope
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ConsumedConfigType
import com.android.build.gradle.internal.scope.BuiltArtifactProperty
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.InternalArtifactType.COMPATIBLE_SCREEN_MANIFEST
import com.android.build.gradle.internal.scope.InternalArtifactType.MANIFEST_MERGE_REPORT
import com.android.build.gradle.internal.scope.InternalArtifactType.NAVIGATION_JSON
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.build.gradle.internal.tasks.manifest.mergeManifestsForApplication
import com.android.build.gradle.internal.utils.setDisallowChanges
import com.android.build.gradle.options.BooleanOption
import com.android.build.gradle.tasks.ProcessApplicationManifest.CreationAction.ManifestProviderImpl
import com.android.builder.dexing.DexingType
import com.android.manifmerger.ManifestMerger2
import com.android.manifmerger.ManifestMerger2.Invoker
import com.android.manifmerger.ManifestProvider
import com.android.manifmerger.MergingReport
import com.android.utils.FileUtils
import com.google.common.base.Preconditions
import org.gradle.api.GradleException
import org.gradle.api.InvalidUserDataException
import org.gradle.api.artifacts.ArtifactCollection
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.artifacts.result.ResolvedArtifactResult
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskProvider
import org.gradle.internal.component.local.model.OpaqueComponentArtifactIdentifier
import java.io.File
import java.io.IOException
import java.io.UncheckedIOException
import java.util.ArrayList
import java.util.EnumSet
import java.util.function.Consumer
import java.util.stream.Collectors

/** A task that processes the manifest  */
@CacheableTask
abstract class ProcessApplicationManifest : ManifestProcessorTask() {

    private var manifests: ArtifactCollection? = null
    private var featureManifests: ArtifactCollection? = null

    /** The merged Manifests files folder.  */
    @get:OutputDirectory
    abstract val mergedManifestOutputDirectory: DirectoryProperty

    @get:PathSensitive(PathSensitivity.RELATIVE)
    @get:Optional
    @get:InputFiles
    var dependencyFeatureNameArtifacts: FileCollection? = null
        private set

    @get:PathSensitive(PathSensitivity.RELATIVE)
    @get:Optional
    @get:InputFiles
    var microApkManifest: FileCollection? = null
        private set

    @get:Optional
    @get:Input
    abstract val baseModuleDebuggable: Property<Boolean>

    @get:Optional
    @get:Input
    abstract val baseModuleVersionCode: Property<Int?>

    @get:Optional
    @get:Input
    abstract val baseModuleVersionName: Property<String?>

    @get:Optional
    @get:Input
    abstract val packageOverride: Property<String>

    @get:PathSensitive(PathSensitivity.RELATIVE)
    @get:InputFiles
    abstract val manifestOverlays: ListProperty<File>

    @get:Optional
    @get:Input
    abstract val manifestPlaceholders: MapProperty<String, Any>

    private var isFeatureSplitVariantType = false
    private var buildTypeName: String? = null

    @get:PathSensitive(PathSensitivity.RELATIVE)
    @get:InputFiles
    @get:Optional
    var navigationJsons: FileCollection? = null
        private set

    @Throws(IOException::class)
    override fun doFullTaskAction() {
        // read the output of the compatible screen manifest.
        val compatibleScreenManifests =
            BuiltArtifactsLoaderImpl().load(compatibleScreensManifest!!)
                ?: throw RuntimeException(
                    "Cannot find generated compatible screen manifests, file a bug"
                )
        if (baseModuleDebuggable.isPresent) {
            val isDebuggable = optionalFeatures.get()
                .contains(Invoker.Feature.DEBUGGABLE)
            if (baseModuleDebuggable.get() != isDebuggable) {
                val errorMessage = String.format(
                    "Dynamic Feature '%1\$s' (build type '%2\$s') %3\$s debuggable,\n"
                            + "and the corresponding build type in the base "
                            + "application %4\$s debuggable.\n"
                            + "Recommendation: \n"
                            + "   in  %5\$s\n"
                            + "   set android.buildTypes.%2\$s.debuggable = %6\$s",
                    projectPath.get(),
                    buildTypeName,
                    if (isDebuggable) "is" else "is not",
                    if (baseModuleDebuggable.get()) "is" else "is not",
                    projectBuildFile.get().asFile,
                    if (baseModuleDebuggable.get()) "true" else "false"
                )
                throw InvalidUserDataException(errorMessage)
            }
        }
        var compatibleScreenManifestForSplit: BuiltArtifactImpl?
        val packagedManifestOutputs = mutableListOf<BuiltArtifactImpl>()
        val mergedManifestOutputs = mutableListOf<BuiltArtifactImpl>()
        val metadataFeatureMergedManifestOutputs = mutableListOf<BuiltArtifactImpl>()
        val instantAppManifestOutputs = mutableListOf<BuiltArtifactImpl>()
        val navJsons = navigationJsons?.files ?: setOf()

        // FIX ME : multi threading.
        for (variantOutput in variantOutputs.get()) {
            compatibleScreenManifestForSplit =
                compatibleScreenManifests.getBuiltArtifact(variantOutput)
            val dirName = variantOutput.dirName()
            val mergedManifestOutputFile = File(
                mergedManifestOutputDirectory.get().asFile,
                FileUtils.join(
                    dirName,
                    SdkConstants.ANDROID_MANIFEST_XML
                )
            )
            val packagedManifestOutputFile = File(
                packagedManifestOutputDirectory.get().asFile,
                FileUtils.join(
                    dirName,
                    SdkConstants.ANDROID_MANIFEST_XML
                )
            )
            val metadataFeatureManifestOutputFile = FileUtils.join(
                metadataFeatureManifestOutputDirectory.get().asFile,
                dirName,
                SdkConstants.ANDROID_MANIFEST_XML
            )
            val instantAppManifestOutputFile =
                if (instantAppManifestOutputDirectory.isPresent) FileUtils.join(
                    instantAppManifestOutputDirectory.get().asFile,
                    dirName,
                    SdkConstants.ANDROID_MANIFEST_XML
                ) else null
            val mergingReport = mergeManifestsForApplication(
                mainManifest.get(),
                manifestOverlays.get(),
                computeFullProviderList(compatibleScreenManifestForSplit),
                navJsons,
                featureName.orNull,
                packageOverride.get(),
                if (baseModuleVersionCode.isPresent) baseModuleVersionCode.orNull else variantOutput.versionCode.orNull,
                if (baseModuleVersionName.isPresent
                    && !baseModuleVersionName.get().isNullOrEmpty()
                ) baseModuleVersionName.orNull else variantOutput.versionName.orNull,
                minSdkVersion.orNull,
                targetSdkVersion.orNull,
                maxSdkVersion.orNull,
                mergedManifestOutputFile.absolutePath,
                packagedManifestOutputFile.absolutePath,  // no aapt friendly merged manifest file necessary for applications.
                null /* aaptFriendlyManifestOutputFile */,
                metadataFeatureManifestOutputFile.absolutePath,
                instantAppManifestOutputFile?.absolutePath,
                ManifestMerger2.MergeType.APPLICATION,
                manifestPlaceholders.get(),
                optionalFeatures.get(),
                dependencyFeatureNames,
                reportFile.get().asFile,
                LoggerWrapper.getLogger(ProcessApplicationManifest::class.java)
            )
            val mergedXmlDocument =
                mergingReport.getMergedXmlDocument(MergingReport.MergedManifestKind.PACKAGED)
            outputMergeBlameContents(mergingReport, mergeBlameFile.get().asFile)
            val properties =
                if (mergedXmlDocument != null) mapOf(
                    BuiltArtifactProperty.PACKAGE_ID to mergedXmlDocument.packageName,
                    BuiltArtifactProperty.SPLIT to mergedXmlDocument.splitName,
                    SdkConstants.ATTR_MIN_SDK_VERSION to mergedXmlDocument.minSdkVersion
                ) else mapOf()
            mergedManifestOutputs.add(
                variantOutput.toBuiltArtifact(mergedManifestOutputFile, properties)
            )
            packagedManifestOutputs.add(
                variantOutput.toBuiltArtifact(packagedManifestOutputFile, properties)
            )
            metadataFeatureMergedManifestOutputs.add(
                variantOutput.toBuiltArtifact(metadataFeatureManifestOutputFile, properties)
            )
            if (instantAppManifestOutputFile != null) {
                instantAppManifestOutputs.add(
                    variantOutput.toBuiltArtifact(instantAppManifestOutputFile, properties)
                )
            }
        }
        BuiltArtifactsImpl(
            artifactType = InternalArtifactType.MERGED_MANIFESTS,
            applicationId = applicationId.get(),
            variantName = variantName,
            elements = mergedManifestOutputs.toList()
        )
            .save(mergedManifestOutputDirectory.get())
        BuiltArtifactsImpl(
            artifactType = InternalArtifactType.PACKAGED_MANIFESTS,
            applicationId = applicationId.get(),
            variantName = variantName,
            elements = packagedManifestOutputs.toList()
        )
            .save(packagedManifestOutputDirectory.get())
        BuiltArtifactsImpl(
            artifactType = InternalArtifactType.METADATA_FEATURE_MANIFEST,
            applicationId = applicationId.get(),
            variantName = variantName,
            elements = metadataFeatureMergedManifestOutputs.toList()
        )
            .save(metadataFeatureManifestOutputDirectory.get())
        if (instantAppManifestOutputDirectory.isPresent) {
            BuiltArtifactsImpl(
                artifactType = InternalArtifactType.INSTANT_APP_MANIFEST,
                applicationId = applicationId.get(),
                variantName = variantName,
                elements = instantAppManifestOutputs.toList()
            )
                .save(instantAppManifestOutputDirectory.get())
        }
    }

    @get:Internal
    override val aaptFriendlyManifestOutputFile: File?
        get() = null

    @get:PathSensitive(PathSensitivity.RELATIVE)
    @get:InputFile
    abstract val mainManifest: Property<File>

    /**
     * Compute the final list of providers based on the manifest file collection and the other
     * providers.
     *
     * @return the list of providers.
     */
    private fun computeFullProviderList(
        compatibleScreenManifestForSplit: BuiltArtifact?
    ): List<ManifestProvider> {
        val artifacts = manifests!!.artifacts
        val providers = mutableListOf<ManifestProvider>()
        for (artifact in artifacts) {
            providers.add(
                ManifestProviderImpl(
                    artifact.file,
                    getArtifactName(artifact)
                )
            )
        }
        if (microApkManifest != null) {
            // this is now always present if embedding is enabled, but it doesn't mean
            // anything got embedded so the file may not run (the file path exists and is
            // returned by the FC but the file doesn't exist.
            val microManifest = microApkManifest!!.singleFile
            if (microManifest.isFile) {
                providers.add(
                    ManifestProviderImpl(
                        microManifest, "Wear App sub-manifest"
                    )
                )
            }
        }
        if (compatibleScreenManifestForSplit != null) {
            providers.add(
                ManifestProviderImpl(
                    File(compatibleScreenManifestForSplit.outputFile),
                    "Compatible-Screens sub-manifest"
                )
            )
        }
        if (featureManifests != null) {
            providers.addAll(computeProviders(featureManifests!!.artifacts))
        }
        return providers
    }

    // Only feature splits can have feature dependencies
    private val dependencyFeatureNames: List<String>
        get() {
            val list: MutableList<String> = ArrayList()
            return if (!isFeatureSplitVariantType) { // Only feature splits can have feature dependencies
                list
            } else try {
                for (file in dependencyFeatureNameArtifacts!!.files) {
                    list.add(org.apache.commons.io.FileUtils.readFileToString(file))
                }
                list
            } catch (e: IOException) {
                throw UncheckedIOException("Could not load feature declaration", e)
            }
        }

    @get:Input
    abstract val applicationId: Property<String>

    @get:Input
    abstract val variantType: Property<String?>

    @get:Optional
    @get:Input
    abstract val minSdkVersion: Property<String?>

    @get:Optional
    @get:Input
    abstract val targetSdkVersion: Property<String?>

    @get:Optional
    @get:Input
    abstract val maxSdkVersion: Property<Int?>

    /** Not an input, see [.getOptionalFeaturesString].  */
    @get:Internal
    abstract val optionalFeatures: SetProperty<Invoker.Feature>

    /** Synthetic input for [.getOptionalFeatures]  */
    @get:Input
    val optionalFeaturesString: List<String>
        get() = optionalFeatures
            .get()
            .stream()
            .map { obj: Invoker.Feature -> obj.toString() }
            .collect(Collectors.toList())

    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    fun getManifests(): FileCollection {
        return manifests!!.artifactFiles
    }

    @InputFiles
    @Optional
    @PathSensitive(PathSensitivity.RELATIVE)
    fun getFeatureManifests(): FileCollection? {
        return if (featureManifests == null) {
            null
        } else featureManifests!!.artifactFiles
    }

    @get:PathSensitive(PathSensitivity.RELATIVE)
    @get:Optional
    @get:InputFiles
    abstract val compatibleScreensManifest: DirectoryProperty?

    @get:Optional
    @get:Input
    abstract val featureName: Property<String>

    @get:Internal("only for task execution")
    abstract val projectPath: Property<String?>

    @get:Internal("only for task execution")
    abstract val projectBuildFile: RegularFileProperty

    @get:Nested
    abstract val variantOutputs: ListProperty<VariantOutputImpl>

    class CreationAction(
        creationConfig: ApkCreationConfig,
        // TODO : remove this variable and find ways to access it from scope.
        private val isAdvancedProfilingOn: Boolean
    ) : VariantTaskCreationAction<ProcessApplicationManifest, ApkCreationConfig>(creationConfig) {
        override val name: String
            get() = computeTaskName("process", "Manifest")

        override val type: Class<ProcessApplicationManifest>
            get() = ProcessApplicationManifest::class.java

        override fun preConfigure(
            taskName: String
        ) {
            super.preConfigure(taskName)
            val variantType = creationConfig.variantType
            Preconditions.checkState(!variantType.isTestComponent)
            val operations = creationConfig.operations
            operations.republish(
                InternalArtifactType.PACKAGED_MANIFESTS,
                InternalArtifactType.MANIFEST_METADATA
            )
        }

        override fun handleProvider(
            taskProvider: TaskProvider<out ProcessApplicationManifest>
        ) {
            super.handleProvider(taskProvider)
            creationConfig.taskContainer.processManifestTask = taskProvider
            val operations = creationConfig.operations
            operations.setInitialProvider(
                taskProvider,
                ProcessApplicationManifest::mergedManifestOutputDirectory
            ).on(InternalArtifactType.MERGED_MANIFESTS)
            operations.setInitialProvider(
                taskProvider,
                ManifestProcessorTask::packagedManifestOutputDirectory
            ).on(InternalArtifactType.PACKAGED_MANIFESTS)
            operations.setInitialProvider(
                taskProvider,
                ManifestProcessorTask::instantAppManifestOutputDirectory
            ).on(InternalArtifactType.INSTANT_APP_MANIFEST)
            operations.setInitialProvider(
                taskProvider,
                ManifestProcessorTask::mergeBlameFile
            )
                .withName("manifest-merger-blame-" + creationConfig.baseName + "-report.txt")
                .on(InternalArtifactType.MANIFEST_MERGE_BLAME_FILE)
            operations.setInitialProvider(
                taskProvider,
                ManifestProcessorTask::metadataFeatureManifestOutputDirectory
            ).withName("metadata-feature").on(InternalArtifactType.METADATA_FEATURE_MANIFEST)
            operations.setInitialProvider(
                taskProvider,
                ProcessApplicationManifest::reportFile
            )
                .atLocation(
                    FileUtils.join(
                        creationConfig.globalScope.outputsDir,
                        "logs"
                    )
                        .absolutePath
                )
                .withName("manifest-merger-" + creationConfig.baseName + "-report.txt")
                .on(MANIFEST_MERGE_REPORT)
        }

        override fun configure(
            task: ProcessApplicationManifest
        ) {
            super.configure(task)
            val variantSources = creationConfig.variantSources
            val globalScope =
                creationConfig.globalScope
            val variantType = creationConfig.variantType
            val project = globalScope.project
            // This includes the dependent libraries.
            task.manifests = creationConfig
                .variantDependencies
                .getArtifactCollection(
                    ConsumedConfigType.RUNTIME_CLASSPATH,
                    ArtifactScope.ALL,
                    AndroidArtifacts.ArtifactType.MANIFEST
                )
            // optional manifest files too.
            if (creationConfig.taskContainer.microApkTask != null
                && creationConfig.embedsMicroApp
            ) {
                task.microApkManifest = project.files(creationConfig.paths.microApkManifestFile)
            }
            creationConfig
                .operations
                .setTaskInputToFinalProduct(
                    COMPATIBLE_SCREEN_MANIFEST,
                    task.compatibleScreensManifest!!
                )
            task.applicationId.set(creationConfig.applicationId)
            task.applicationId.disallowChanges()
            task.variantType.set(creationConfig.variantType.toString())
            task.variantType.disallowChanges()
            task.minSdkVersion
                .set(project.provider { creationConfig.minSdkVersion.apiString })
            task.minSdkVersion.disallowChanges()
            task.targetSdkVersion
                .set(
                    project.provider {
                        val targetSdk =
                            creationConfig.targetSdkVersion
                        if (targetSdk.apiLevel < 1) null else targetSdk.apiString
                    }
                )
            task.targetSdkVersion.disallowChanges()
            task.maxSdkVersion
                .set(project.provider(creationConfig::maxSdkVersion))
            task.maxSdkVersion.disallowChanges()
            task.optionalFeatures
                .set(
                    project.provider {
                        getOptionalFeatures(
                            creationConfig, isAdvancedProfilingOn
                        )
                    }
                )
            task.optionalFeatures.disallowChanges()
            creationConfig
                .outputs
                .getEnabledVariantOutputs()
                .forEach(Consumer { t: VariantOutputImpl ->
                    task.variantOutputs.add(t)
                })
            task.variantOutputs.disallowChanges()
            // set optional inputs per module type
            if (variantType.isBaseModule) {
                task.featureManifests = creationConfig
                    .variantDependencies
                    .getArtifactCollection(
                        ConsumedConfigType.REVERSE_METADATA_VALUES,
                        ArtifactScope.PROJECT,
                        AndroidArtifacts.ArtifactType.REVERSE_METADATA_FEATURE_MANIFEST
                    )
            } else if (variantType.isDynamicFeature) {
                val dfCreationConfig =
                    creationConfig as DynamicFeatureCreationConfig
                task.featureName.setDisallowChanges(dfCreationConfig.featureName)
                task.baseModuleDebuggable.setDisallowChanges(dfCreationConfig.baseModuleDebuggable)
                task.baseModuleVersionCode.setDisallowChanges(dfCreationConfig.baseModuleVersionCode)
                task.baseModuleVersionName.setDisallowChanges(dfCreationConfig.baseModuleVersionName)
                task.dependencyFeatureNameArtifacts = creationConfig
                    .variantDependencies
                    .getArtifactFileCollection(
                        ConsumedConfigType.RUNTIME_CLASSPATH,
                        ArtifactScope.PROJECT,
                        AndroidArtifacts.ArtifactType.FEATURE_NAME
                    )
            }
            if (!globalScope.extension.aaptOptions.namespaced) {
                task.navigationJsons = project.files(
                    creationConfig.operations.get(NAVIGATION_JSON),
                    creationConfig
                        .variantDependencies
                        .getArtifactFileCollection(
                            ConsumedConfigType.RUNTIME_CLASSPATH,
                            ArtifactScope.ALL,
                            AndroidArtifacts.ArtifactType.NAVIGATION_JSON
                        )
                )
            }
            task.packageOverride.set(creationConfig.applicationId)
            task.packageOverride.disallowChanges()
            task.manifestPlaceholders.set(
                task.project.provider(
                    creationConfig::manifestPlaceholders
                )
            )
            task.manifestPlaceholders.disallowChanges()
            task.mainManifest
                .set(project.provider(variantSources::mainManifestFilePath))
            task.mainManifest.disallowChanges()
            task.manifestOverlays.set(
                task.project.provider(variantSources::manifestOverlays)
            )
            task.manifestOverlays.disallowChanges()
            task.isFeatureSplitVariantType = creationConfig.variantType.isDynamicFeature
            task.buildTypeName = creationConfig.buildType
            task.projectPath.setDisallowChanges(task.project.path)
            task.projectBuildFile.set(task.project.buildFile)
            task.projectBuildFile.disallowChanges()
            // TODO: here in the "else" block should be the code path for the namespaced pipeline
        }

        /**
         * Implementation of AndroidBundle that only contains a manifest.
         *
         * This is used to pass to the merger manifest snippet that needs to be added during
         * merge.
         */
        class ManifestProviderImpl(private val manifest: File, private val name: String) :
            ManifestProvider {
            override fun getManifest(): File {
                return manifest
            }

            override fun getName(): String {
                return name
            }

        }

    }

    companion object {
        private fun computeProviders(
            artifacts: Set<ResolvedArtifactResult>
        ): List<ManifestProvider> {
            val providers: MutableList<ManifestProvider> = mutableListOf()
            for (artifact in artifacts) {
                val directory = artifact.file
                val splitOutputs: BuiltArtifacts? =
                    loadFromDirectory(directory)
                if (splitOutputs == null || splitOutputs.elements.isEmpty()) {
                    throw GradleException("Could not load manifest from $directory")
                }
                providers.add(
                    ManifestProviderImpl(
                        File(splitOutputs.elements.iterator().next().outputFile),
                        getArtifactName(artifact)
                    )
                )
            }
            return providers
        }

        // TODO put somewhere else?
        @JvmStatic
        fun getArtifactName(artifact: ResolvedArtifactResult): String {
            return when(val id = artifact.id.componentIdentifier) {
                is ProjectComponentIdentifier -> id.projectPath
                is ModuleComponentIdentifier -> "${id.group}:${id.module}:${id.version}"
                is OpaqueComponentArtifactIdentifier -> id.displayName
                is ExtraComponentIdentifier -> id.displayName
                else -> throw RuntimeException("Unsupported type of ComponentIdentifier")
            }
        }

        private fun getOptionalFeatures(
            creationConfig: ApkCreationConfig, isAdvancedProfilingOn: Boolean
        ): EnumSet<Invoker.Feature> {
            val features: MutableList<Invoker.Feature> =
                ArrayList()
            val variantType = creationConfig.variantType
            if (variantType.isDynamicFeature) {
                features.add(Invoker.Feature.ADD_FEATURE_SPLIT_ATTRIBUTE)
                features.add(Invoker.Feature.CREATE_FEATURE_MANIFEST)
                features.add(Invoker.Feature.ADD_USES_SPLIT_DEPENDENCIES)
            }
            if (variantType.isDynamicFeature) {
                features.add(Invoker.Feature.STRIP_MIN_SDK_FROM_FEATURE_MANIFEST)
            }
            features.add(Invoker.Feature.ADD_INSTANT_APP_MANIFEST)
            if (variantType.isBaseModule || variantType.isDynamicFeature) {
                features.add(Invoker.Feature.CREATE_BUNDLETOOL_MANIFEST)
            }
            if (variantType.isDynamicFeature) {
                // create it for dynamic-features and base modules that are not hybrid base features.
                // hybrid features already contain the split name.
                features.add(Invoker.Feature.ADD_SPLIT_NAME_TO_BUNDLETOOL_MANIFEST)
            }
            if (creationConfig.testOnlyApk) {
                features.add(Invoker.Feature.TEST_ONLY)
            }
            if (creationConfig.debuggable) {
                features.add(Invoker.Feature.DEBUGGABLE)
                if (isAdvancedProfilingOn) {
                    features.add(Invoker.Feature.ADVANCED_PROFILING)
                }
            }
            if (creationConfig.dexingType === DexingType.LEGACY_MULTIDEX) {
                features.add(
                    if (creationConfig.services.projectOptions[BooleanOption.USE_ANDROID_X]) {
                        Invoker.Feature.ADD_ANDROIDX_MULTIDEX_APPLICATION_IF_NO_NAME
                    } else {
                        Invoker.Feature.ADD_SUPPORT_MULTIDEX_APPLICATION_IF_NO_NAME
                    })
            }
            if (creationConfig.services.projectOptions[BooleanOption.ENFORCE_UNIQUE_PACKAGE_NAMES]
            ) {
                features.add(Invoker.Feature.ENFORCE_UNIQUE_PACKAGE_NAME)
            }
            return if (features.isEmpty())
                EnumSet.noneOf(Invoker.Feature::class.java)
            else EnumSet.copyOf(features)
        }
    }
}