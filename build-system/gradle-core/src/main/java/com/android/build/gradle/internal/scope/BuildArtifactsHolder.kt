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

package com.android.build.gradle.internal.scope

import com.android.build.api.artifact.ArtifactType
import com.android.build.api.artifact.BuildableArtifact
import com.android.build.gradle.internal.api.artifact.BuildableArtifactImpl
import com.android.build.gradle.internal.api.artifact.toArtifactType
import com.android.utils.FileUtils
import com.android.utils.appendCapitalized
import com.google.common.base.Joiner
import com.google.gson.JsonParser
import com.google.gson.annotations.SerializedName
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileCollection
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.file.RegularFile
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskDependency
import org.gradle.api.tasks.TaskProvider
import java.io.File
import java.io.FileReader
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.logging.Level
import java.util.logging.Logger

typealias Report = Map<ArtifactType, BuildArtifactsHolder.ProducersData>

/**
 * Buildable artifact holder.
 *
 * This class manages buildable artifacts, allowing users to transform [ArtifactType].
 *
 * @param project the Gradle [Project]
 * @param rootOutputDir a supplier for the intermediate directories to place output files.
 * @parma dslScope the scope for dsl parsing issue raising.
 */
abstract class BuildArtifactsHolder(
    private val project: Project,
    private val rootOutputDir: () -> File
) {

    // delete those 2 maps once use of BuildableArtifact has been eradicated.
    private val artifactRecordMap = ConcurrentHashMap<ArtifactType, ArtifactRecord>()
    private val finalArtifactsMap = ConcurrentHashMap<ArtifactType, BuildableArtifact>()

    private val fileProducersMap = ProducersMap<RegularFile>(
        project.objects,
        project.layout.buildDirectory,
        this::getIdentifier)
    private val directoryProducersMap = ProducersMap<Directory>(
        project.objects,
        project.layout.buildDirectory,
        this::getIdentifier)

    /**
     * Types of operation on [BuildableArtifact] as indicated by tasks producing the artifact.
     */
    enum class OperationType {
        /**
         * Initial producer of the artifact
         */
        INITIAL,
        /**
         * Producer appends more files/directories to artifact
         */
        APPEND,
        /**
         * Producer transforms (replace) existing artifact files/directories with new ones.
         */
        TRANSFORM
    }

    // ArtifactRecord and BuildableProducer structures are deprecated and will be removed once
    // usage of BuildableArtifacts had been removed from Tasks implementation.

    /**
     * Internal private class for storing [BuildableArtifact] created for a [ArtifactType]
     */
    @Deprecated("use Producers instead")
    private class ArtifactRecord {

        // A list of all BuildableFiles for the artifact.  Technically, only the last
        // BuildableFiles are needed.  Storing the all BuildableFiles allows better error
        // messages to be generated in the future.
        val history: MutableList<BuildableArtifact> = mutableListOf()

        // last registered task that produced a [BuildableArtifact] for this artifact type record.
        var lastProducer: BuildableProducer? = null

        /** The latest [BuildableArtifact] created for this artifact */
        var last : BuildableArtifact
            get() = history.last()
            set(value) {
                add(value, null)
            }

        // adds a new [BuildableArtifact] with the producer information
        fun add(buildableArtifact: BuildableArtifact, producer: BuildableProducer?) {
            history.add(buildableArtifact)
            this.lastProducer = producer
        }

        val size : Int
            get() = history.size
    }

    // unless a [BuildableArtifact] or [FileCollection] was used to initialize a new instance of
    // [BuildableArtifact], the task as well as the Provider<> producing the file or folder
    // associated with the new BuildableArtifact will be recorded here.
    private data class BuildableProducer(
        val fileOrDirProperty: Property<in FileSystemLocation>?,
        val name: String,
        val fileName: String)

    private class FinalBuildableArtifact(
        val artifactType: ArtifactType,
        val artifacts: BuildArtifactsHolder) : BuildableArtifact {
        override val files: Set<File>
            get() = final().files
        override fun isEmpty(): Boolean = final().isEmpty()
        override fun iterator(): Iterator<File> = final().iterator()
        override fun getBuildDependencies(): TaskDependency = final().buildDependencies
        override fun get(): FileCollection = final().get()
        private fun final() : BuildableArtifact = artifacts.getArtifactRecord(artifactType).last
        override fun toString(): String = "FinalBuildableArtifact($artifactType, $artifacts, $files)"
    }

    /**
     * Republishes an [ArtifactType] under a different type. This is useful when a level of
     * indirection is used.
     *
     * @param sourceType the original [ArtifactType] for the built artifacts.
     * @param targetType the supplemental [ArtifactType] the same built artifacts will also be
     * published under.
     */
    fun republish(sourceType: ArtifactType, targetType: ArtifactType) {
        getProducerMap(sourceType).republish(sourceType, targetType)
    }

    /**
     * Copies a published [ArtifactType] from another instance of [BuildArtifactsHolder] to this
     * instance.
     * This does not remove the original elements from the source [BuildArtifactsHolder].
     *
     * @param artifactType artifact type to copy to this holder.
     * @param from source [BuildArtifactsHolder] to copy the produced artifacts from.
     */
    fun copy(artifactType: ArtifactType, from: BuildArtifactsHolder) {
        copy(artifactType, from, artifactType)
    }

    /**
     * Copies a published [ArtifactType] from another instance of [BuildArtifactsHolder] to this
     * instance.
     * This does not remove the original elements from the source [BuildArtifactsHolder].
     *
     * @param artifactType artifact type to copy to this holder.
     * @param from source [BuildArtifactsHolder] to copy the produced artifacts from.
     * @param originalArtifactType artifact type under which the producers are registered in the
     * source [BuildArtifactsHolder], by default is the same [artifactType]
     */
    fun copy(artifactType: ArtifactType, from: BuildArtifactsHolder, originalArtifactType: ArtifactType = artifactType) {
        getProducerMap(artifactType).copy(artifactType,
            from.getProducerMap(originalArtifactType).getProducers(originalArtifactType))
    }

    /**
     * Registers a new [RegularFile] producer for a particular [ArtifactType]. The producer is
     * identified by a [TaskProvider] to avoid configuring the task until the produced [RegularFile]
     * is required by another [Task].
     *
     * The simplest way to use the mechanism is as follow :
     * <pre>
     *     open class MyTask(objectFactory: ObjectFactory): Task() {
     *          val outputFile = objectFactory.fileProperty()
     *     }
     *
     *     val myTaskProvider = taskFactory.register("myTask", MyTask::class.java)
     *
     *     scope.artifacts.producesFile(InternalArtifactType.SOME_ID,
     *            OperationType.INITIAL,
     *            myTaskProvider,
     *            myTaskProvider.map { it -> it.outputFile }
     *            "some_file_name")
     *
     * </pre>
     *
     * Consumers should use [getFinalProduct] or [getFinalProducts] to get a [Provider] instance
     * for registered products which ensures that [Task]s don't get initialized until the
     * [Provider.get] method is invoked during a consumer task configuration execution for instance.
     *
     * @param artifactType the produced artifact type
     * @param operationType the expected type of production, there can be only one
     * [OperationType.INITIAL] but many [OperationType.APPEND] or [OperationType.TRANSFORM]
     * @param taskProvider the [TaskProvider] for the task ultimately responsible for producing the
     * artifact.
     * @param productProvider the [Provider] of the artifact [RegularFile]
     * @param buildDirectory the destination directory of the produced artifact or not provided if
     * using the default location.
     * @param fileName the desired file name, must be provided.
     */
    fun <T: Task> producesFile(
        artifactType: ArtifactType,
        operationType: OperationType,
        taskProvider: TaskProvider<out T>,
        productProvider: (T) -> RegularFileProperty,
        buildDirectory: String? = null,
        fileName: String
    ) {

        val settableProperty = project.objects.fileProperty()
        produces(artifactType,
            fileProducersMap,
            operationType,
            taskProvider,
            productProvider,
            settableProperty,
            fileName,
            buildDirectory)
    }

    /**
     * Registers a new [Directory] producer for a particular [ArtifactType]. The producer is
     * identified by a [TaskProvider] to avoid configuring the task until the produced [Directory]
     * is required by another [Task].
     *
     * The simplest way to use the mechanism is as follow :
     * <pre>
     *     open class MyTask(objectFactory: ObjectFactory): Task() {
     *          val outputFile = objectFactory.directoryProperty()
     *     }
     *
     *     val myTaskProvider = taskFactory.register("myTask", MyTask::class.java)
     *
     *     scope.artifacts.producesDir(InternalArtifactType.SOME_ID,
     *            OperationType.INITIAL,
     *            myTaskProvider,
     *            myTaskProvider.map { it -> it.outputFile }
     *            "some_file_name")
     *
     * </pre>
     *
     * Consumers should use [getFinalProduct] or [getFinalProducts] to get a [Provider] instance
     * for registered products which ensures that [Task]s don't get initialized until the
     * [Provider.get] method is invoked during a consumer task configuration execution for instance.
     *
     * @param artifactType the produced artifact type
     * @param operationType the expected type of production, there can be only one
     * [OperationType.INITIAL] but many [OperationType.APPEND] or [OperationType.TRANSFORM]
     * @param taskProvider the [TaskProvider] for the task ultimately responsible for producing the
     * artifact.
     * @param productProvider the [Provider] of the artifact [Directory]
     * @param buildDirectory the destination directory of the produced artifact or not provided if
     * using the default location.
     * @param fileName the desired directory name or empty string if no sub-directory should be
     * used.
     */
    fun <T: Task> producesDir(
        artifactType: ArtifactType,
        operationType: OperationType,
        taskProvider: TaskProvider<out T>,
        productProvider: (T) -> DirectoryProperty,
        buildDirectory: String? = null,
        fileName: String = "out"
    ) {

        produces(artifactType,
            directoryProducersMap,
            operationType,
            taskProvider,
            productProvider,
            project.objects.directoryProperty(),
            fileName,
            buildDirectory)
    }

    // TODO : remove these 2 APIs once all java tasks stopped using those after Kotlin translation.
    fun <T: Task> producesFile(
        artifactType: ArtifactType,
        operationType: OperationType,
        taskProvider: TaskProvider<out T>,
        productProvider: (T) -> RegularFileProperty,
        fileName: String = "out"
    )= producesFile(artifactType, operationType, taskProvider, productProvider, null, fileName)


    fun <T: Task> producesDir(
        artifactType: ArtifactType,
        operationType: OperationType,
        taskProvider: TaskProvider<out T>,
        propertyProvider: (T) -> DirectoryProperty,
        fileName: String = "out"
    )= producesDir(artifactType, operationType, taskProvider, propertyProvider, null, fileName)

    private val dummyTask by lazy {

        project.tasks.register("dummy" + getIdentifier(), DummyTask::class.java)
    }

    abstract class DummyTask: DefaultTask() {
        abstract val emptyFileProperty: RegularFileProperty
    }

    fun emptyFile(artifactType: ArtifactType) {
        produces<RegularFile, DummyTask>(artifactType,
            fileProducersMap,
            OperationType.INITIAL,
            dummyTask,
            DummyTask::emptyFileProperty,
            project.objects.fileProperty(),
            "out"
        )
    }


    private fun <T : FileSystemLocation, U: Task> produces(artifactType: ArtifactType,
        producersMap: ProducersMap<T>,
        operationType: OperationType,
        taskProvider: TaskProvider<out U>,
        productProvider: (U) -> Property<T>,
        settableFileLocation: Property<T>,
        fileName: String,
        buildDirectory: String? = null) {

        val producers = producersMap.getProducers(artifactType, buildDirectory)
        val product= taskProvider.map { productProvider(it) }

        when(operationType) {
            OperationType.INITIAL -> {
                if (!producers.isEmpty()) {
                    val plural = producers.hasMultipleProducers()
                    throw RuntimeException(
                        """|Task ${taskProvider.name} is expecting to be the initial producer of
                                |$artifactType, but the following ${if (plural) "tasks" else "task"} : ${Joiner.on(',').join(producers.map { it.taskName})}
                                |${if (plural) "are" else "is"} already registered as ${if (plural) "producers" else "producer"}"""
                            .trimMargin()
                    )
                }
            }
            OperationType.APPEND -> {
            }
            OperationType.TRANSFORM -> {
                producers.clear()
            }
        }
        producers.add(settableFileLocation, product, taskProvider.name, fileName)

        // note that this configuration block may be called immediately in case the task has
        // already been initialized.
        taskProvider.configure {

            product.get().set(settableFileLocation)

            // add a new configuration action to make sure the producers are configured even
            // if no one injects the result. The task is being configured so it will be executed
            // and output folders must be set correctly.
            // this can happen when users request an intermediary task execution (instead of
            // assemble for instance).
            producers.resolveAllAndReturnLast()
        }
    }

    private fun getProducerMap(artifactType: ArtifactType): ProducersMap<out FileSystemLocation> {

        return when(artifactType.kind()) {
            ArtifactType.Kind.FILE -> fileProducersMap
            ArtifactType.Kind.DIRECTORY -> directoryProducersMap
        }
    }

    /**
     * Returns the current produced version of an [ArtifactType]
     *
     * @param artifactType the identifier for the built artifact.
     */
    fun getCurrentProduct(artifactType: ArtifactType) =
        getProducerMap(artifactType).getProducers(artifactType).getCurrent()

    /**
     * Returns true if there is at least one producer for the passed [ArtifactType]
     *
     * @param artifactType the identifier for the built artifact.
     */
    fun hasFinalProduct(artifactType: ArtifactType) = getProducerMap(artifactType).hasProducers(artifactType)

    /**
     * Returns a [Provider] of either a [Directory] or a [RegularFile] depending on the passed
     * [ArtifactType.Kind]. The [Provider] will represent the final value of the built artifact
     * irrespective of when this call is made.
     *
     * If there are more than one producer appending artifacts for the passed type, calling this
     * method will generate an error and [getFinalProducts] should be used instead.
     *
     * The simplest way to use the mechanism is as follow :
     * <pre>
     *     open class MyTask(objectFactory: ObjectFactory): Task() {
     *          val inputFile: Provider<RegularFile>
     *     }
     *
     *     val myTaskProvider = taskFactory.register("myTask", MyTask::class.java) {
     *          it.inputFile = scope.artifacts.getFinalProduct(InternalArtifactTYpe.SOME_ID)
     *     }
     * </pre>
     *
     * @param artifactType the identifier for the built artifact.
     */
    fun <T: FileSystemLocation> getFinalProduct(artifactType: ArtifactType): Provider<T> {
        val producers = getProducerMap(artifactType).getProducers(artifactType)
        if (producers.size > 1) {
            throw java.lang.RuntimeException(
                """A single producer of $artifactType was requested, but the following tasks
                    |produce it: ${Joiner.on(',').join(
                    producers.map { it.taskName})}""".trimMargin())
        }
        return producers.injectable as Provider<T>
    }

    /**
     * Sets a [Property] value to the final producer for the given artifact type.
     *
     * If there are more than one producer appending artifacts for the passed type, calling this
     * method will generate an error and [setFinalProducts] should be used instead.
     *
     * The simplest way to use the mechanism is as follow :
     * <pre>
     *     abstract class MyTask: Task() {
     *          @InputFile
     *          abstract val inputFile: RegularFileProperty
     *     }
     *
     *     val myTaskProvider = taskFactory.register("myTask", MyTask::class.java) {
     *          scope.artifacts.setTaskInputToFinalProduct(InternalArtifactTYpe.SOME_ID, it.inputFile)
     *     }
     * </pre>
     *
     * @param artifactType requested artifact type
     * @param taskInputProperty the [Property] to set the final producer on.
     */
    fun <T: FileSystemLocation> setTaskInputToFinalProduct(artifactType: ArtifactType, taskInputProperty: Property<T>) {
        val finalProduct = getFinalProduct<T>(artifactType)
        taskInputProperty.set(finalProduct)
    }

    /**
     * Sets a [ListProperty] value to all producers for the given artifact type.
     *
     * The simplest way to use the mechanism is as follow :
     * <pre>
     *     abstract class MyTask: Task() {
     *          @InputFiles
     *          abstract val inputFiles: ListProperty<RegularFile>
     *     }
     *
     *     val myTaskProvider = taskFactory.register("myTask", MyTask::class.java) {
     *          scope.artifacts.setTaskInputToFinalProducts(InternalArtifactTYpe.SOME_ID, it.inputFiles)
     *     }
     * </pre>
     *
     * @param artifactType requested artifact type
     * @param taskInputProperty the [ListProperty] to set the producers on.
     */
    fun <T: FileSystemLocation> setTaskInputToFinalProducts(artifactType: ArtifactType, taskInputProperty: ListProperty<T>) {
        val finalProducts = getFinalProducts<T>(artifactType)
        taskInputProperty.set(finalProducts)
    }

    /**
     * Sets a [ListProperty] value to all the produces for the given artifact type.
     *
     * The simplest way to use the mechanism is as follow :
     * <pre>
     *     abstract class MyTask: Task() {
     *          @InputFiles
     *          abstract val inputFiles: ListProperty<RegularFile>
     *     }
     *
     *     val myTaskProvider = taskFactory.register("myTask", MyTask::class.java) {
     *          scope.artifacts.setFinalProducts(InternalArtifactTYpe.SOME_ID, it.inputFiles)
     *     }
     * </pre>
     *
     * @param artifactType requested artifact type
     * @param receiver the [ListProperty] to set the producers on.
     */
    fun <T: FileSystemLocation> setFinalProducts(artifactType: ArtifactType, receiver: ListProperty<T>) {
        val finalProduct = getFinalProducts<T>(artifactType)
        receiver.set(finalProduct)
    }

    /**
     * See [getFinalProducts] API.
     *
     * On top of returning the [Provider] of [Directory] or [RegularFile], also returns a
     * [Provider] of [String] which represents the task name of the final producer task for the
     * passed artifact type.
     *
     * @param artifactType the identifier for the built artifact.
     * @return a [Pair] of [Provider] for the [FileSystemLocation] and task name producing it.
     */
    fun <T: FileSystemLocation> getFinalProductWithTaskName(artifactType: ArtifactType): Pair<Provider<String>, Provider<T>> {
        return Pair(getProducerMap(artifactType).getProducers(artifactType).lastProducerTaskName,
            getFinalProduct(artifactType))
    }

    /**
     * Returns a [ListProperty]s of either [Directory] or [RegularFile] depending on the
     * passed [ArtifactType.Kind]. This possibly empty list will contain the final
     * values of the built artifacts irrespective of when this call is made.
     *
     * @param artifactType the identifier for the built artifact.
     */
    fun <T: FileSystemLocation> getFinalProducts(artifactType: ArtifactType): ListProperty<T> {
        val producers = getProducerMap(artifactType).getProducers(artifactType)
        return producers.getAllProducers() as ListProperty<T>;
    }

    /**
     * Returns an appropriate task name for the variant with the given prefix.
     */
    fun getTaskName(prefix : String) : String {
        return prefix.appendCapitalized(getIdentifier())
    }

    /**
     * Returns a identifier that will uniquely identify this artifacts holder against other holders.
     * This can be used to create unique folder or provide unique task name for this context.
     *
     * @return a unique [String]
     */
    abstract fun getIdentifier(): String

    // MOST OF THE APIs below should be removed once BuildableArtifact are not directly used by
    // tasks any longer.

    /**
     * Returns the current [BuildableArtifact] associated with the artifactType.
     * When a chain of tasks are registered to append or replace the artifact, this API will return
     * the current (possibly intermediary) version of the BuildableArtifact at the time of this
     * call.
     *
     * @param artifactType the requested artifact type.
     * @return the possibly empty [BuildableArtifact] for this artifact type.
     */
    fun getArtifactFiles(artifactType: ArtifactType): BuildableArtifact {
        return getArtifactRecord(artifactType).last
    }

    /**
     * Returns the final [BuildableArtifact] associated with the artifactType.
     * Irrespective of the timing of this method call, it will always return the final version of
     * the [BuildableArtifact] for the passed artifact type.
     *
     * This should not be used to transform further the artifact type.
     *
     * @param artifactType the requested [BuildableArtifact] artifact type.
     * @return the possibly empty final [BuildableArtifact] for this artifact type.
     */
    fun getFinalArtifactFiles(artifactType: ArtifactType) : BuildableArtifact {
        val artifact = artifactRecordMap[artifactType]
        artifact?.lastProducer?.let {
            if (it.fileOrDirProperty == null) {
                Logger.getLogger(javaClass.name).log(Level.WARNING,
                    "Wrong API use for artifact type $artifactType")
            }
        }
        return finalArtifactsMap.computeIfAbsent(artifactType) {
            FinalBuildableArtifact(artifactType, this)
        }
    }

    /**
     * Returns whether the artifactType exists in the holder.
     */
    fun hasArtifact(artifactType: ArtifactType) : Boolean {
        return artifactRecordMap.containsKey(artifactType)
    }

    /**
     * Replaces the output of the specified artifactType.
     *
     * This method allows files associated with the artifactType to be replaced such that subsequent
     * call to [getArtifactFiles] will return the newly created files.
     * The path of File are created from the supplied filenames and the name of the Task that will
     * generate these files.
     *
     * @param artifactType artifactType to be replaced.
     * @param newFiles names of the new files.
     * @param taskName [Task] name that will create the new files.
     * @return BuildableFiles containing files that the specified task should create.
     */
    @JvmOverloads
    fun createBuildableArtifact(
            artifactType: ArtifactType,
            operationType: OperationType,
            newFiles : Any,
            taskName : String? = null) {
        val collection = createFileCollection(artifactType, operationType, newFiles, taskName)
        val files = BuildableArtifactImpl(collection)
        createOutput(artifactType, files)
    }

    /**
     * Append output to the specified artifactType. The [newFiles] will be added after any
     * existing content.
     *
     * After invoking this method, [getArtifactFiles] will return a [BuildableArtifact] that
     * contains both the new files and the original files.
     * The path of File are created from the supplied filenames and the name of the Task that will
     * generate these files.
     *
     * @param artifactType [ArtifactType] the new files will be classified under.
     * @param newFiles names of the new files.
     * @param taskName the name of the task responsible for producing or updating the new files.
     * @return BuildableFiles containing files that the specified task should create.
     */
    @Deprecated("Use createBuildableArtifact/createDirectory/createArtifactFile APIs")
    fun appendArtifact(
        artifactType: ArtifactType,
        newFiles : Collection<Any>,
        taskName: String) : BuildableArtifact {
        return doAppendArtifact(artifactType,
            createFileCollection(
                artifactType,
                OperationType.APPEND,
                newFiles,
                taskName))
    }

    /**
     * Append existing files to a specified artifact type. The [existingFiles] will be added after
     * any existing content.
     *
     * This should only be called when files already exists during configuration time (which usually
     * is the case with source directory files) or when dependency information is embedded inside
     * the file collection.
     *
     * @param artifactType [ArtifactType] for the existing files
     * @param existingFiles existing files' [FileCollection] with
     */
    @Deprecated("Use createBuildableArtifact/createDirectory/createArtifactFile APIs")
    fun appendArtifact(artifactType: ArtifactType, existingFiles: FileCollection) {
        doAppendArtifact(artifactType,
            createFileCollection(
                artifactType,
                OperationType.APPEND,
                existingFiles))
    }

    /**
     * Create a [Provider] of [RegularFile] that can be used as a task output.
     *
     * @param artifactType the intended artifact type stored in the directory.
     * @param operationType type of output (appending, replacing or initial version)
     * @param taskName name of the producer task.
     * @param fileName fileName for the file.
     */
    fun createArtifactFile(
        artifactType: ArtifactType,
        operationType: OperationType,
        taskName: String,
        fileName: String) : Provider<RegularFile> = createArtifactFile(
            artifactType,
            operationType,
            taskName,
            File(
                FileUtils.join(
                    artifactType.getOutputPath(),
                    artifactType.name().toLowerCase(Locale.US),
                    getIdentifier(),
                    fileName
                )
            )
        )

    /**
     * Create a [Provider] of [RegularFile] that can be used as a task output.
     *
     * @param artifactType the intended artifact type stored in the directory.
     * @param operationType type of output (appending, replacing or initial version)
     * @param taskName name of the producer task.
     * @param requestedFileLocation file location to use, relative to the project build output.
     */
    private fun createArtifactFile(
        artifactType: ArtifactType,
        operationType: OperationType,
        taskName: String,
        requestedFileLocation: File) : Provider<RegularFile> {

        if (artifactType.kind() != ArtifactType.Kind.FILE) {
            throw RuntimeException(
                "appendArtifactFile called with $artifactType which is an ArtifactType" +
                        " with kind set to DIRECTORY."
            )
        }

        return createFileOrDirectory(artifactType,
            operationType,
            taskName,
            requestedFileLocation) {
            createFileProperty<RegularFileProperty>(artifactType, it)
        }
    }

    /**
     * Create a [Provider] of [Directory] that can be used as a task output.
     *
     * @param artifactType the intended artifact type stored in the directory.
     * @param operationType type of output (appending, replacing or initial version)
     * @param taskName name of the producer task.
     * @param fileName name of the directory or "out" by default.
     */
    fun createDirectory(artifactType: ArtifactType,
        operationType: OperationType,
        taskName: String,
        fileName: String = "out"): Provider<Directory> =

        createDirectory(artifactType, operationType, taskName, File(FileUtils.join(
            artifactType.getOutputPath(),
            artifactType.name().toLowerCase(Locale.US),
            getIdentifier(),
            fileName)))

    /**
     * Create a [Provider] of [Directory] that can be used as a task output.
     *
     * @param artifactType the intended artifact type stored in the directory.
     * @param operationType type of output (appending, replacing or initial version)
     * @param taskName name of the producer task.
     * @param requestFileLocation location of the output file, relative the project build directory.
     * @param requestFileLocation location of the output file, relative the project build directory.
     */
    fun createDirectory(artifactType: ArtifactType,
        operationType: OperationType,
        taskName: String,
        requestFileLocation: File): Provider<Directory> {

        if (artifactType.kind() != ArtifactType.Kind.DIRECTORY) {
            throw RuntimeException("createDirectory called with $artifactType which is an " +
                    "ArtifactType with kind set to File.")
        }

        return createFileOrDirectory(
            artifactType,
            operationType,
            taskName,
            requestFileLocation) {
            createFileProperty<DirectoryProperty>(artifactType, it)
        }
    }

    private fun <T: FileSystemLocation> createFileOrDirectory(
        artifactType: ArtifactType,
        operationType: OperationType,
        taskName: String,
        requestedFileLocation: File,
        propertyCreator: (path: String)->Property<T> ) : Provider<T> {

        val artifactRecord = artifactRecordMap[artifactType]
        val intermediatesOutput = InternalArtifactType.Category.INTERMEDIATES.outputPath

        val output = propertyCreator(
            if (artifactRecord == null || artifactType.getOutputPath() != intermediatesOutput) {
                requestedFileLocation.path
            } else {
                FileUtils.join(
                    intermediatesOutput,
                    artifactType.name().toLowerCase(Locale.US),
                    getIdentifier(),
                    taskName,
                    requestedFileLocation.name)
            })

        val fileCollection =
            createFileCollection(
                artifactType,
                operationType,
                output,
                taskName)

        doAppendArtifact(
            artifactType,
            fileCollection,
            @Suppress("UNCHECKED_CAST")
            BuildableProducer(
                output as Property<in FileSystemLocation>,
                taskName,
                requestedFileLocation.name))
        return output
    }

    private inline fun <reified T> createFileProperty(
        artifactType: ArtifactType,
        path: String): T {

        val artifactRecord = artifactRecordMap[artifactType]
        val intermediatesOutput = InternalArtifactType.Category.INTERMEDIATES.outputPath

        // reset lastProducer's output location if present.
        val lastProducer = artifactRecord?.lastProducer
        if (lastProducer != null) {
            val lastProducerNewLocation = FileUtils.join(
                intermediatesOutput,
                artifactType.name().toLowerCase(Locale.US),
                getIdentifier(),
                lastProducer.name,
                lastProducer.fileName
            )

            lastProducer.fileOrDirProperty!!.set(
                when (T::class) {
                    RegularFileProperty::class -> project.layout.buildDirectory.file(
                        lastProducerNewLocation
                    )
                    DirectoryProperty::class -> project.layout.buildDirectory.dir(
                        lastProducerNewLocation
                    )
                    else -> throw RuntimeException("setLastProducer called with unsupported type ${T::class}")
                }
            )
        }

        return when(T::class) {
            RegularFileProperty::class -> {
                val prop = project.objects.fileProperty()
                prop.set(project.layout.buildDirectory.file(path))
                prop as T
            }
            DirectoryProperty::class -> {
                val prop = project.objects.directoryProperty()
                prop.set(project.layout.buildDirectory.dir(path))
                prop as T
            }
            else -> throw RuntimeException("createFileOrDirectory called with unsupported type ${T::class}")
        }
    }

    private fun createFileCollection(
        artifactType: ArtifactType,
        operationType: OperationType,
        newFiles: Any,
        builtBy: String? = null): FileCollection {

        val artifactRecord = artifactRecordMap[artifactType]
        @Suppress("REDUNDANT_ELSE_IN_WHEN")
        val fileCollection = when(operationType) {
            OperationType.INITIAL -> {
                if (artifactRecord!= null && artifactRecord.size > 0) {
                    val lastProducer = artifactRecord.lastProducer
                    throw RuntimeException("Task $builtBy is expecting to be the initial producer of $artifactType, but "
                        + "${lastProducer?.name} already registered itself as a producer at these locations : "
                        + Joiner.on('\n').join(artifactRecord.last.files.map { it.absolutePath }))
                }
                project.files(newFiles)
            }
            OperationType.TRANSFORM -> {
                project.files(newFiles)
            }
            OperationType.APPEND -> {
                if (artifactRecord != null) {
                    project.files(artifactRecord.last, newFiles)
                } else {
                    project.files(newFiles)
                }
            }
            else -> {
                throw RuntimeException("Unhandled OperationType $operationType")
            }
        }

        if (builtBy != null) {
            fileCollection.builtBy(builtBy)
        }
        return fileCollection
    }

    private fun doAppendArtifact(type: ArtifactType,
        files: FileCollection,
        producer: BuildableProducer? = null) : BuildableArtifact {

        val newBuildableArtifact = BuildableArtifactImpl(files)
        createOutput(type, newBuildableArtifact, producer)
        return newBuildableArtifact
    }

    private fun createOutput(
        type: ArtifactType,
        artifact: BuildableArtifact,
        producer: BuildableProducer? = null) : ArtifactRecord {

        synchronized(artifactRecordMap) {
            val output = artifactRecordMap.computeIfAbsent(type) { ArtifactRecord() }
            output.add(artifact, producer)
            return output
        }
    }

    private fun getArtifactRecord(artifactType : ArtifactType) : ArtifactRecord {
        return artifactRecordMap[artifactType] ?:
        createOutput(artifactType, BuildableArtifactImpl(project.files()))
    }

    fun createReport(): Report =
            fileProducersMap.entrySet().associate {artifactRecordMap
                it.key to it.value.toProducersData()
            }

    /**
     * Create a File for a task and artifact type.
     * @param task the task the file should be created for.
     * @param artifactType artifact type that will be associated with the file.
     * @param filename desired file name.
     */
    internal fun createFile(taskName: String, artifactType: ArtifactType, filename: String) =
            FileUtils.join(artifactType.getOutputDir(rootOutputDir()),
                getIdentifier(),
                taskName,
                filename)


    /**
     * Return history of all [BuildableArtifact] for an [ArtifactType].
     */
    internal fun getHistory(artifactType: ArtifactType) : List<BuildableArtifact> {
        val record = getArtifactRecord(artifactType)
        return record.history
    }

    /** A data class for use with GSON. */
    data class ProducerData(
        @SerializedName("files")
        val files: List<String>,
        @SerializedName("builtBy")
        val builtBy : String
    )

    data class ProducersData(
        @SerializedName("producer")
        val producers: List<ProducerData>
    )

    companion object {

        fun parseReport(file : File) : Report {
            val result = mutableMapOf<ArtifactType, ProducersData>()
            val parser = JsonParser()
            FileReader(file).use { reader ->
                for ((key, value) in parser.parse(reader).asJsonObject.entrySet()) {
                    val obj = value.asJsonObject
                    val producers = obj.getAsJsonArray("producer").map {
                        ProducerData(
                            it.asJsonObject.getAsJsonArray("files").map {
                                it.asString
                            },
                            it.asJsonObject.get("builtBy").asString
                        )
                    }

                    result[key.toArtifactType()] = ProducersData(producers)
                }
            }
            return result
        }
    }
}
