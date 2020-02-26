/*
 * Copyright (C) 2014 The Android Open Source Project
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
import com.android.build.api.variant.impl.BuiltArtifactsImpl
import com.android.build.api.variant.impl.BuiltArtifactsLoaderImpl.Companion.loadFromDirectory
import com.android.build.api.variant.impl.VariantOutputImpl
import com.android.build.api.variant.impl.dirName
import com.android.build.gradle.internal.LoggerWrapper
import com.android.build.gradle.internal.component.BaseCreationConfig
import com.android.build.gradle.internal.core.VariantDslInfo
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactScope
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ConsumedConfigType
import com.android.build.gradle.internal.scope.BuiltArtifactProperty
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.InternalArtifactType.PACKAGED_MANIFESTS
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.build.gradle.tasks.ProcessApplicationManifest.Companion.getArtifactName
import com.android.build.gradle.tasks.ProcessApplicationManifest.CreationAction.ManifestProviderImpl
import com.android.builder.internal.TestManifestGenerator
import com.android.manifmerger.ManifestMerger2
import com.android.manifmerger.ManifestMerger2.MergeFailureException
import com.android.manifmerger.ManifestProvider
import com.android.manifmerger.ManifestSystemProperty
import com.android.manifmerger.MergingReport
import com.android.manifmerger.PlaceholderHandler
import com.android.utils.FileUtils
import com.android.utils.ILogger
import com.google.common.base.Charsets
import com.google.common.base.Preconditions
import com.google.common.base.Strings
import com.google.common.io.Files
import org.gradle.api.artifacts.ArtifactCollection
import org.gradle.api.file.FileCollection
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskProvider
import java.io.File
import java.io.IOException

/**
 * A task that processes the manifest for test modules and tests in androidTest.
 *
 *
 * For both test modules and tests in androidTest process is the same, except for how the tested
 * application id is extracted.
 *
 *
 * Tests in androidTest get that info from the [VariantDslInfo.testedApplicationId],
 * while the test modules get the info from the published intermediate manifest with type
 * TYPE_METADATA of the tested app.
 */
abstract class ProcessTestManifest : ManifestProcessorTask() {

    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    @get:InputFiles
    lateinit var testTargetMetadata: FileCollection
        private set

    /** Whether there's just a single APK with both test and tested code.  */
    private var onlyTestApk = false
    @get:Internal
    var tmpDir: File? = null
    private var manifests: ArtifactCollection? = null

    @get:Nested
    abstract val apkData: Property<VariantOutputImpl>

    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    @get:InputFiles
    var navigationJsons: FileCollection? = null
        private set

    override fun doFullTaskAction() {
        if (testedApplicationId.orNull == null && testTargetMetadata.isEmpty) {
            throw RuntimeException("testedApplicationId and testTargetMetadata are null")
        }
        var testedApplicationId = testedApplicationId.orNull
        if (!onlyTestApk) {
            val manifestOutputs =
                loadFromDirectory(testTargetMetadata.singleFile)
            if (manifestOutputs == null || manifestOutputs.elements.isEmpty()) {
                throw RuntimeException("Cannot find merged manifest, please file a bug.")
            }
            val mainSplit: BuiltArtifact = manifestOutputs.elements.first()
            testedApplicationId = mainSplit.properties[BuiltArtifactProperty.PACKAGE_ID]
        }
        val dirName = apkData.get().variantOutputConfiguration.dirName()
        val manifestOutputFolder =
            if (Strings.isNullOrEmpty(dirName)) packagedManifestOutputDirectory.get().asFile
            else packagedManifestOutputDirectory.get().file(dirName).asFile
        FileUtils.mkdirs(manifestOutputFolder)
        val manifestOutputFile = File(manifestOutputFolder, SdkConstants.ANDROID_MANIFEST_XML)
        val navJsons = navigationJsons?.files ?: listOf<File>()

        mergeManifestsForTestVariant(
            testApplicationId.get(),
            minSdkVersion.get(),
            targetSdkVersion.get(),
            testedApplicationId!!,
            instrumentationRunner.get(),
            handleProfiling.get(),
            functionalTest.get(),
            testLabel.orNull,
            testManifestFile.orNull,
            computeProviders(),
            placeholdersValues.get(),
            navJsons,
            manifestOutputFile,
            tmpDir!!
        )
        BuiltArtifactsImpl(
            BuiltArtifacts.METADATA_FILE_VERSION,
            PACKAGED_MANIFESTS,
            testApplicationId.get(),
            variantName,
            listOf(
                apkData.get().toBuiltArtifact(
                    manifestOutputFile,
                    mapOf()
                )
            )
        )
            .saveToDirectory(packagedManifestOutputDirectory.get().asFile)
    }

    /**
     * Creates the manifest for a test variant
     *
     * @param testApplicationId the application id of the test application
     * @param minSdkVersion the minSdkVersion of the test application
     * @param targetSdkVersion the targetSdkVersion of the test application
     * @param testedApplicationId the application id of the tested application
     * @param instrumentationRunner the name of the instrumentation runner
     * @param handleProfiling whether or not the Instrumentation object will turn profiling on and
     * off
     * @param functionalTest whether or not the Instrumentation class should run as a functional
     * test
     * @param testLabel the label for the tests
     * @param testManifestFile optionally user provided AndroidManifest.xml for testing application
     * @param manifestProviders the manifest providers
     * @param manifestPlaceholders used placeholders in the manifest
     * @param navigationJsons the list of navigation JSON files
     * @param outManifest the output location for the merged manifest
     * @param tmpDir temporary dir used for processing
     */
    private fun mergeManifestsForTestVariant(
        testApplicationId: String,
        minSdkVersion: String,
        targetSdkVersion: String,
        testedApplicationId: String,
        instrumentationRunner: String,
        handleProfiling: Boolean,
        functionalTest: Boolean,
        testLabel: String?,
        testManifestFile: File?,
        manifestProviders: List<ManifestProvider?>,
        manifestPlaceholders: Map<String?, Any?>,
        navigationJsons: Collection<File>,
        outManifest: File,
        tmpDir: File
    ) {
        Preconditions.checkNotNull(
            testApplicationId,
            "testApplicationId cannot be null."
        )
        Preconditions.checkNotNull(
            testedApplicationId,
            "testedApplicationId cannot be null."
        )
        Preconditions.checkNotNull(
            instrumentationRunner,
            "instrumentationRunner cannot be null."
        )
        Preconditions.checkNotNull(
            handleProfiling,
            "handleProfiling cannot be null."
        )
        Preconditions.checkNotNull(
            functionalTest,
            "functionalTest cannot be null."
        )
        Preconditions.checkNotNull(
            manifestProviders,
            "manifestProviders cannot be null."
        )
        Preconditions.checkNotNull(
            outManifest,
            "outManifestLocation cannot be null."
        )
        val logger: ILogger =
            LoggerWrapper(logger)
        // These temp files are only need in the middle of processing manifests; delete
        // them when they're done. We're not relying on File#deleteOnExit for this
        // since in the Gradle daemon for example that would leave the files around much
        // longer than we want.
        var tempFile1: File? = null
        var tempFile2: File? = null
        try {
            FileUtils.mkdirs(tmpDir)
            var generatedTestManifest =
                if (manifestProviders.isEmpty() && testManifestFile == null) outManifest else File.createTempFile(
                    "manifestMerger",
                    ".xml",
                    tmpDir
                ).also { tempFile1 = it }
            // we are generating the manifest and if there is an existing one,
            // it will be overlaid with the generated one
            logger.verbose("Generating in %1\$s", generatedTestManifest!!.absolutePath)
            generateTestManifest(
                testApplicationId,
                minSdkVersion,
                if (targetSdkVersion == "-1") null else targetSdkVersion,
                testedApplicationId,
                instrumentationRunner,
                handleProfiling,
                functionalTest,
                generatedTestManifest
            )
            if (testManifestFile != null && testManifestFile.exists()) {
                val invoker = ManifestMerger2.newMerger(
                    testManifestFile,
                    logger,
                    ManifestMerger2.MergeType.APPLICATION
                )
                    .setPlaceHolderValues(manifestPlaceholders)
                    .setPlaceHolderValue(
                        PlaceholderHandler.INSTRUMENTATION_RUNNER,
                        instrumentationRunner
                    )
                    .addLibraryManifest(generatedTestManifest)
                    .addNavigationJsons(navigationJsons)
                // we override these properties
                invoker.setOverride(ManifestSystemProperty.PACKAGE, testApplicationId)
                invoker.setOverride(ManifestSystemProperty.MIN_SDK_VERSION, minSdkVersion)
                invoker.setOverride(ManifestSystemProperty.NAME, instrumentationRunner)
                invoker.setOverride(ManifestSystemProperty.TARGET_PACKAGE, testedApplicationId)
                invoker.setOverride(
                    ManifestSystemProperty.FUNCTIONAL_TEST, functionalTest.toString()
                )
                invoker.setOverride(
                    ManifestSystemProperty.HANDLE_PROFILING, handleProfiling.toString()
                )
                if (testLabel != null) {
                    invoker.setOverride(ManifestSystemProperty.LABEL, testLabel)
                }
                if (targetSdkVersion != "-1") {
                    invoker.setOverride(
                        ManifestSystemProperty.TARGET_SDK_VERSION, targetSdkVersion
                    )
                }
                val mergingReport = invoker.merge()
                if (manifestProviders.isEmpty()) {
                    handleMergingResult(mergingReport, outManifest, logger)
                } else {
                    tempFile2 = File.createTempFile("manifestMerger", ".xml", tmpDir)
                    handleMergingResult(mergingReport, tempFile2, logger)
                    generatedTestManifest = tempFile2
                }
            }
            if (manifestProviders.isNotEmpty()) {
                val mergingReport = ManifestMerger2.newMerger(
                    generatedTestManifest,
                    logger,
                    ManifestMerger2.MergeType.APPLICATION
                )
                    .withFeatures(
                        ManifestMerger2.Invoker.Feature.REMOVE_TOOLS_DECLARATIONS
                    )
                    .setOverride(ManifestSystemProperty.PACKAGE, testApplicationId)
                    .addManifestProviders(manifestProviders)
                    .setPlaceHolderValues(manifestPlaceholders)
                    .addNavigationJsons(navigationJsons)
                    .merge()
                handleMergingResult(mergingReport, outManifest, logger)
            }
        } catch (e: IOException) {
            throw RuntimeException("Unable to create the temporary file", e)
        } catch (e: MergeFailureException) {
            throw RuntimeException("Manifest merging exception", e)
        } finally {
            try {
                if (tempFile1 != null) {
                    FileUtils.delete(tempFile1!!)
                }
                if (tempFile2 != null) {
                    FileUtils.delete(tempFile2)
                }
            } catch (e: IOException) {
                // just log this, so we do not mask the initial exception if there is any
                logger.error(e, "Unable to clean up the temporary files.")
            }
        }
    }

    @Throws(IOException::class)
    private fun handleMergingResult(
        mergingReport: MergingReport, outFile: File, logger: ILogger
    ) {
        outputMergeBlameContents(
            mergingReport,
            mergeBlameFile.get().asFile
        )
        if (mergingReport.result == MergingReport.Result.ERROR) {
            mergingReport.log(logger)
            throw RuntimeException(mergingReport.reportString)
        }
        if (mergingReport.result == MergingReport.Result.WARNING) {
            mergingReport.log(logger)
        }

        try {
            val annotatedDocument =
                mergingReport.getMergedDocument(MergingReport.MergedManifestKind.BLAME)
            logger.verbose(annotatedDocument
                ?: "No blaming records from manifest merger")
        } catch (e: Exception) {
            logger.error(e, "cannot print resulting xml")
        }
        val finalMergedDocument =
            mergingReport.getMergedDocument(MergingReport.MergedManifestKind.MERGED)
                ?: throw RuntimeException("No result from manifest merger")
        try {
            Files.asCharSink(outFile, Charsets.UTF_8).write(finalMergedDocument)
        } catch (e: IOException) {
            logger.error(e, "Cannot write resulting xml")
            throw RuntimeException(e)
        }
        logger.verbose("Merged manifest saved to $outFile")
    }

    override val aaptFriendlyManifestOutputFile: File?
        get() = null

    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    @get:InputFile
    abstract val testManifestFile: Property<File?>

    @get:Input
    abstract val testApplicationId: Property<String>

    @get:Optional
    @get:Input
    abstract val testedApplicationId: Property<String>

    @get:Input
    abstract val minSdkVersion: Property<String>

    @get:Input
    abstract val targetSdkVersion: Property<String>

    @get:Input
    abstract val instrumentationRunner: Property<String>

    @get:Input
    abstract val handleProfiling: Property<Boolean>

    @get:Input
    abstract val functionalTest: Property<Boolean>

    @get:Input
    abstract val variantType: Property<String>

    @get:Optional
    @get:Input
    abstract val testLabel: Property<String>

    @get:Input
    abstract val placeholdersValues: MapProperty<String, Any>

    /**
     * Compute the final list of providers based on the manifest file collection.
     * @return the list of providers.
     */
    private fun computeProviders(): List<ManifestProvider?> {
        return manifests!!.artifacts.map { ManifestProviderImpl(it.file, getArtifactName(it)) }
    }

    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    fun getManifests(): FileCollection {
        return manifests!!.artifactFiles
    }

    class CreationAction(
        creationConfig: BaseCreationConfig,
        private val testTargetMetadata: FileCollection
    ) : VariantTaskCreationAction<ProcessTestManifest, BaseCreationConfig>(creationConfig) {
        override val name = computeTaskName("process", "Manifest")
        override val type = ProcessTestManifest::class.java

        override fun preConfigure(taskName: String) {
            super.preConfigure(taskName)
            creationConfig
                .operations
                .republish(
                    PACKAGED_MANIFESTS,
                    InternalArtifactType.MANIFEST_METADATA
                )
        }

        override fun handleProvider(
            taskProvider: TaskProvider<out ProcessTestManifest>
        ) {
            super.handleProvider(taskProvider)
            creationConfig.taskContainer.processManifestTask = taskProvider
            creationConfig.operations.setInitialProvider(
                taskProvider,
                ManifestProcessorTask::packagedManifestOutputDirectory
            ).on(PACKAGED_MANIFESTS)
            creationConfig.operations.setInitialProvider(
                taskProvider,
                ProcessTestManifest::mergeBlameFile
            ).withName("manifest-merger-blame-" + creationConfig.baseName + "-report.txt")
                .on(InternalArtifactType.MANIFEST_MERGE_BLAME_FILE)
        }

        override fun configure(
            task: ProcessTestManifest
        ) {
            super.configure(task)
            val project = task.project
            val variantDslInfo = creationConfig.variantDslInfo
            val variantSources = creationConfig.variantSources
            // Use getMainManifestIfExists() instead of getMainManifestFilePath() because this task
            // accepts either a non-null file that exists or a null file, it does not accept a
            // non-null file that does not exist.
            task.testManifestFile
                .set(project.provider(variantSources::mainManifestIfExists))
            task.testManifestFile.disallowChanges()
            task.apkData.set(creationConfig.outputs.getMainSplit())
            task.variantType.set(creationConfig.variantType.toString())
            task.variantType.disallowChanges()
            task.tmpDir = FileUtils.join(
                creationConfig.paths.intermediatesDir,
                "tmp",
                "manifest",
                creationConfig.dirName
            )
            task.minSdkVersion
                .set(project.provider { creationConfig.minSdkVersion.apiString })
            task.minSdkVersion.disallowChanges()
            task.targetSdkVersion
                .set(
                    project.provider { variantDslInfo.targetSdkVersion.apiString }
                )
            task.targetSdkVersion.disallowChanges()
            task.testTargetMetadata = testTargetMetadata
            task.testApplicationId
                .set(project.provider(variantDslInfo::testApplicationId))
            task.testApplicationId.disallowChanges()
            // will only be used if testTargetMetadata is null.
            task.testedApplicationId
                .set(project.provider(variantDslInfo::testedApplicationId))
            task.testedApplicationId.disallowChanges()
            val testedConfig = variantDslInfo.testedVariant
            task.onlyTestApk = testedConfig != null && testedConfig.variantType.isAar
            task.instrumentationRunner
                .set(project.provider(variantDslInfo::instrumentationRunner))
            task.instrumentationRunner.disallowChanges()
            task.handleProfiling
                .set(project.provider(variantDslInfo::handleProfiling))
            task.handleProfiling.disallowChanges()
            task.functionalTest
                .set(project.provider(variantDslInfo::functionalTest))
            task.functionalTest.disallowChanges()
            task.testLabel.set(project.provider(variantDslInfo::testLabel))
            task.testLabel.disallowChanges()
            task.manifests = creationConfig
                .variantDependencies
                .getArtifactCollection(
                    ConsumedConfigType.RUNTIME_CLASSPATH,
                    ArtifactScope.ALL,
                    AndroidArtifacts.ArtifactType.MANIFEST
                )
            task.placeholdersValues
                .set(
                    project.provider<Map<String, Any>>(
                        variantDslInfo::manifestPlaceholders
                    )
                )
            task.placeholdersValues.disallowChanges()
            if (!creationConfig.globalScope.extension.aaptOptions.namespaced) {
                task.navigationJsons = project.files(
                    creationConfig
                        .variantDependencies
                        .getArtifactFileCollection(
                            ConsumedConfigType.RUNTIME_CLASSPATH,
                            ArtifactScope.ALL,
                            AndroidArtifacts.ArtifactType.NAVIGATION_JSON
                        )
                )
            }
        }

    }

    companion object {
        private fun generateTestManifest(
            testApplicationId: String,
            minSdkVersion: String?,
            targetSdkVersion: String?,
            testedApplicationId: String,
            instrumentationRunner: String,
            handleProfiling: Boolean,
            functionalTest: Boolean,
            outManifestLocation: File
        ) {
            val generator = TestManifestGenerator(
                outManifestLocation,
                testApplicationId,
                minSdkVersion,
                targetSdkVersion,
                testedApplicationId,
                instrumentationRunner,
                handleProfiling,
                functionalTest
            )
            try {
                generator.generate()
            } catch (e: IOException) {
                throw RuntimeException(e)
            }
        }
    }
}