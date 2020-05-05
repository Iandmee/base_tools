/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.build.api.artifact.impl

import com.android.build.api.artifact.AppendRequest
import com.android.build.api.artifact.ArtifactKind
import com.android.build.api.artifact.ArtifactType
import com.android.build.api.artifact.MultipleTransformRequest
import com.android.build.api.artifact.Operations
import com.android.build.api.artifact.ReplaceRequest
import com.android.build.api.artifact.TransformRequest
import com.android.build.api.variant.BuiltArtifactsLoader
import com.android.build.api.variant.impl.BuiltArtifactsLoaderImpl
import com.android.build.gradle.internal.scope.AnchorOutputType
import com.android.build.gradle.internal.scope.getOutputPath
import com.android.build.gradle.internal.utils.setDisallowChanges
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileCollection
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.file.FileSystemLocationProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskProvider
import java.io.File

/**
 * Implementation of the [Operations] Variant API interface.
 *
 * Implementation will delegate most of its [FileSystemLocationProperty] handling to
 * [ArtifactContainer] only handling the [TaskProvider] relationships.
 *
 * This call will also contains some AGP private methods for added services to support AGP
 * specific services like setting initial AGP providers.
 */
class OperationsImpl(
    project: Project,
    private val identifier: String
): Operations {

    private val storageProvider = StorageProviderImpl()
    private val objects= project.objects
    private val buildDirectory = project.layout.buildDirectory

    override fun getBuiltArtifactsLoader(): BuiltArtifactsLoader {
        return BuiltArtifactsLoaderImpl()
    }

    override fun <FILE_TYPE : FileSystemLocation, ARTIFACT_TYPE> get(type: ARTIFACT_TYPE): Provider<FILE_TYPE>
            where ARTIFACT_TYPE : ArtifactType<out FILE_TYPE>, ARTIFACT_TYPE : ArtifactType.Single
            = getArtifactContainer(type).get()

    override fun <FILE_TYPE : FileSystemLocation, ARTIFACT_TYPE> getAll(type: ARTIFACT_TYPE): Provider<List<FILE_TYPE>>
            where ARTIFACT_TYPE : ArtifactType<FILE_TYPE>,
                  ARTIFACT_TYPE : ArtifactType.Multiple
            = getArtifactContainer(type).get()

    override fun <TASK : Task, FILE_TYPE : FileSystemLocation> append(
        taskProvider: TaskProvider<TASK>,
        with: (TASK) -> FileSystemLocationProperty<FILE_TYPE>
    ): AppendRequest<FILE_TYPE> = AppendRequestImpl(this, taskProvider, with)

    override fun <TASK : Task, FILE_TYPE : FileSystemLocation> transform(
        taskProvider: TaskProvider<TASK>,
        from: (TASK) -> FileSystemLocationProperty<FILE_TYPE>,
        into: (TASK) -> FileSystemLocationProperty<FILE_TYPE>
    ): TransformRequest<FILE_TYPE> = TransformRequestImpl(this, taskProvider, from, into)

    override fun <TASK: Task, FILE_TYPE: FileSystemLocation> transformAll(
        taskProvider: TaskProvider<TASK>,
        from: (TASK)-> ListProperty<FILE_TYPE>,
        into: (TASK) -> FileSystemLocationProperty<FILE_TYPE>
    ): MultipleTransformRequest<FILE_TYPE> =
        MultipleTransformRequestImpl(this, objects, taskProvider, from, into)

    override fun <TASK : Task, FILE_TYPE : FileSystemLocation> replace(
        taskProvider: TaskProvider<TASK>,
        with: (TASK) -> FileSystemLocationProperty<FILE_TYPE>
    ): ReplaceRequest<FILE_TYPE> = ReplaceRequestImpl(this, taskProvider, with)

    override fun <TASK : Task> use(taskProvider: TaskProvider<TASK>): TaskBasedOperationsImpl<TASK> {
        return TaskBasedOperationsImpl(this, taskProvider)
    }

    // End of public API implementation, start of private AGP services.

    /**
     * Returns a [File] representing the artifact type location (could be a directory or regular
     * file).
     */
    internal fun <T: FileSystemLocation> getOutputPath(type: ArtifactType<T>, vararg paths: String)=
        type.getOutputPath(buildDirectory, identifier, *paths)

    /**
     * Returns the [ArtifactContainer] for the passed [type]. The instance may be allocated as part
     * of the call if there is not [ArtifactContainer] for this [type] registered yet.
     *
     * @param type requested artifact type
     * @return the [ArtifactContainer] for the passed type
     */
    internal fun <ARTIFACT_TYPE, FILE_TYPE> getArtifactContainer(type: ARTIFACT_TYPE): SingleArtifactContainer<FILE_TYPE> where
            ARTIFACT_TYPE : ArtifactType.Single,
            ARTIFACT_TYPE : ArtifactType<out FILE_TYPE>,
            FILE_TYPE : FileSystemLocation {

        return storageProvider.getStorage(type.kind).getArtifact(objects, type)
    }

    /**
     * Returns the [ArtifactContainer] for the passed [type]. The instance may be allocated as part
     * of the call if there is not [ArtifactContainer] for this [type] registered yet.
     *
     * @param type requested artifact type
     * @return the [ArtifactContainer] for the passed type
     */
    internal fun <ARTIFACT_TYPE, FILE_TYPE> getArtifactContainer(type: ARTIFACT_TYPE): MultipleArtifactContainer<FILE_TYPE> where
            ARTIFACT_TYPE : ArtifactType.Multiple,
            ARTIFACT_TYPE : ArtifactType<FILE_TYPE>,
            FILE_TYPE : FileSystemLocation {

        return storageProvider.getStorage(type.kind).getArtifact(objects, type)
    }

    fun <T: FileSystemLocation, ARTIFACT_TYPE, ARTIFACT_TYPE2> republish(source: ARTIFACT_TYPE, target: ARTIFACT_TYPE2)
        where ARTIFACT_TYPE: ArtifactType<T>, ARTIFACT_TYPE: ArtifactType.Single,
              ARTIFACT_TYPE2: ArtifactType<T>, ARTIFACT_TYPE2: ArtifactType.Single {

        storageProvider.getStorage(target.kind).copy(target, getArtifactContainer(source))
    }

    /**
     * Sets the Android Gradle Plugin producer. Although conceptually the AGP producers are first
     * to process/consume artifacts, we want to register them last after all custom code has had
     * opportunities to transform or replace it.
     *
     * Therefore, we cannot rely on the usual append/replace pattern but instead use this API to
     * be artificially put first in the list of producers.
     *
     * @param taskProvider the [TaskProvider] for the task producing the artifact
     * @param property: the field reference to retrieve the output from the task
     */
    @JvmName("setInitialProvider")
    internal fun <FILE_TYPE, TASK> setInitialProvider(
        taskProvider: TaskProvider<TASK>,
        property: (TASK) -> FileSystemLocationProperty<FILE_TYPE>
    ): SingleInitialProviderRequestImpl<TASK, FILE_TYPE>
            where FILE_TYPE : FileSystemLocation,
                  TASK: Task
            = SingleInitialProviderRequestImpl(this, taskProvider, property)

    /**
     * Adds an Android Gradle Plugin producer.
     *
     * The passed [type] must be a [ArtifactType.Multiple] that accepts more than one producer.
     *
     * Although conceptually the AGP producers are first to produce artifacts, we want to register
     * them last after all custom code had the opportunity to transform or replace it.
     *
     * Therefore, we cannot rely on the usual append/replace pattern but instead use this API to
     * be artificially put first in the list of producers for the passed [type]
     *
     * @param type the [ArtifactType.Multiple] artifact type being produced
     * @param taskProvider the [TaskProvider] for the task producing the artifact
     * @param property: the field reference to retrieve the output from the task
     */
    internal fun <ARTIFACT_TYPE, FILE_TYPE, TASK> addInitialProvider(
        type: ARTIFACT_TYPE,
        taskProvider: TaskProvider<TASK>,
        property: (TASK) -> FileSystemLocationProperty<FILE_TYPE>
    ) where ARTIFACT_TYPE : ArtifactType.Multiple,
            ARTIFACT_TYPE : ArtifactType<FILE_TYPE>,
            FILE_TYPE : FileSystemLocation,
            TASK: Task {

        val artifactContainer = getArtifactContainer(type)
        taskProvider.configure {
            // since the taskProvider will execute, resolve its output path, and since there can
            // be multiple ones, just put the task name at all times.
            property(it).set(type.getOutputPath(buildDirectory, identifier, taskProvider.name))
        }
        artifactContainer.addInitialProvider(taskProvider.flatMap { property(it) })
    }

    /**
     * Sets a [Property] value to the final producer for the given artifact type.
     *
     * If there are more than one producer appending artifacts for the passed type, calling this
     * method will generate an error.
     *
     * The simplest way to use the mechanism is as follow :
     * <pre>
     *     abstract class MyTask: Task() {
     *          @InputFile
     *          abstract val inputFile: RegularFileProperty
     *     }
     *
     *     val myTaskProvider = taskFactory.register("myTask", MyTask::class.java) {
     *          scope.operations.setTaskInputToFinalProduct(InternalArtifactTYpe.SOME_ID, it.inputFile)
     *     }
     * </pre>
     *
     * @param artifactType requested artifact type
     * @param taskInputProperty the [Property] to set the final producer on.
     */
    fun <T: FileSystemLocation, ARTIFACT_TYPE> setTaskInputToFinalProduct(
        artifactType: ARTIFACT_TYPE, taskInputProperty: Property<T>
    ) where ARTIFACT_TYPE: ArtifactType<T>, ARTIFACT_TYPE: ArtifactType.Single {
        val finalProduct = get(artifactType)
        taskInputProperty.setDisallowChanges(finalProduct)
    }

    fun <ARTIFACT_TYPE, FILE_TYPE> copy(
        artifactType: ARTIFACT_TYPE,
        from: OperationsImpl
    ) where ARTIFACT_TYPE: ArtifactType<FILE_TYPE>,
            ARTIFACT_TYPE: ArtifactType.Single,
            FILE_TYPE: FileSystemLocation {

        val artifactContainer = from.getArtifactContainer(artifactType)
        storageProvider.getStorage(artifactType.kind).copy(artifactType, artifactContainer)
    }

    /**
     * Backward compatibility section
     */

    // because of existing public APIs, we cannot move [AnchorOutputType.ALL_CLASSES] to Provider<>
    private val allClasses= project.files()

    /**
     * Appends a [FileCollection] to the [AnchorOutputType.ALL_CLASSES] artifact.
     *
     * @param files the [FileCollection] to add.
     */
    fun appendToAllClasses(files: FileCollection) {
        synchronized(allClasses) {
            allClasses.from(files)
        }
    }

    /**
     * Returns the current [FileCollection] for [AnchorOutputType.ALL_CLASSES] as of now.
     * The returned file collection is final but its content can change.
     */
    fun getAllClasses(): FileCollection = allClasses
}

/**
 * Specialization of the [TransformRequest] public API with added services private to AGP.
 */
internal class TransformRequestImpl<TASK : Task, FILE_TYPE : FileSystemLocation>(
    private val operationsImpl: OperationsImpl,
    private val taskProvider: TaskProvider<TASK>,
    private val from: (TASK) -> FileSystemLocationProperty<FILE_TYPE>,
    private val into: (TASK) -> FileSystemLocationProperty<FILE_TYPE>
) : TransformRequest<FILE_TYPE> {

    override fun <ARTIFACT_TYPE> on(type: ARTIFACT_TYPE)
            where ARTIFACT_TYPE : ArtifactType<FILE_TYPE>,
                  ARTIFACT_TYPE : ArtifactType.Transformable,
                  ARTIFACT_TYPE : ArtifactType.Single {

        val artifactContainer = operationsImpl.getArtifactContainer(type)
        val currentProvider =  artifactContainer.transform(taskProvider.flatMap { into(it) })
        taskProvider.configure {
            from(it).set(currentProvider)
            // since the task will now execute, resolve its output path.
            into(it).set(
                operationsImpl.getOutputPath(type,
                    taskProvider.name,
                    type.getFileSystemLocationName()
                )
            )
        }
    }
}

/**
 * Specialization of the [ReplaceRequest] public API with added services private to AGP.
 */
internal class ReplaceRequestImpl<TASK: Task, FILE_TYPE: FileSystemLocation>(
    private val operationsImpl: OperationsImpl,
    private val taskProvider: TaskProvider<TASK>,
    private val with: (TASK) -> FileSystemLocationProperty<FILE_TYPE>
): ReplaceRequest<FILE_TYPE> {
    override fun <ARTIFACT_TYPE> on(type: ARTIFACT_TYPE)
            where ARTIFACT_TYPE : ArtifactType<FILE_TYPE>,
                  ARTIFACT_TYPE : ArtifactType.Replaceable {

        val artifactContainer = operationsImpl.getArtifactContainer(type)
        taskProvider.configure {
            with(it).set(operationsImpl.getOutputPath(type, taskProvider.name))
        }
        artifactContainer.replace(taskProvider.flatMap { with(it) })
    }
}

/**
 * Implementation of the [AppendRequest] public API.
 */
internal class AppendRequestImpl<TASK: Task, FILE_TYPE: FileSystemLocation>(
    private val operationsImpl: OperationsImpl,
    private val taskProvider: TaskProvider<TASK>,
    private val with: (TASK) -> FileSystemLocationProperty<FILE_TYPE>
): AppendRequest<FILE_TYPE> {

    override fun <ARTIFACT_TYPE> on(type: ARTIFACT_TYPE): AppendRequestImpl<TASK, FILE_TYPE>
            where ARTIFACT_TYPE : ArtifactType<FILE_TYPE>,
                  ARTIFACT_TYPE : ArtifactType.Appendable {

        val artifactContainer = operationsImpl.getArtifactContainer(type)
        taskProvider.configure {
            with(it).set(operationsImpl.getOutputPath(type, taskProvider.name))
        }
        // all producers of a multiple artifact type are added to the initial list (just like
        // the AGP producers) since the transforms always operate on the complete list of added
        // providers.
        artifactContainer.addInitialProvider(taskProvider.flatMap { with(it) })
        return this
    }
}

internal class MultipleTransformRequestImpl<TASK: Task, FILE_TYPE: FileSystemLocation>(
    private val operationsImpl: OperationsImpl,
    private val objects: ObjectFactory,
    private val taskProvider: TaskProvider<TASK>,
    private val from: (TASK) -> ListProperty<FILE_TYPE>,
    private val into: (TASK) -> FileSystemLocationProperty<FILE_TYPE>
):
        MultipleTransformRequest<FILE_TYPE> {

    var fileName: String? = null

    fun withName(name: String): MultipleTransformRequestImpl<TASK, FILE_TYPE> {
        fileName = name
        return this
    }

    override fun <ARTIFACT_TYPE> on(type: ARTIFACT_TYPE)
            where ARTIFACT_TYPE : ArtifactType<FILE_TYPE>,
                  ARTIFACT_TYPE : ArtifactType.Transformable,
                  ARTIFACT_TYPE : ArtifactType.Multiple {
        val artifactContainer = operationsImpl.getArtifactContainer(type)
        val newList = objects.listProperty(type.kind.dataType().java)
        val currentProviders= artifactContainer.transform(taskProvider.flatMap { newList })
        taskProvider.configure {
            newList.add(into(it))
            into(it).set(operationsImpl.getOutputPath(type, taskProvider.name))
            from(it).set(currentProviders)
        }
    }
}

internal class SingleInitialProviderRequestImpl<TASK: Task, FILE_TYPE: FileSystemLocation>(
    private val operationsImpl: OperationsImpl,
    private val taskProvider: TaskProvider<TASK>,
    private val from: (TASK) -> FileSystemLocationProperty<FILE_TYPE>
) {
    var fileName: String? = null
    private var buildOutputLocation: String? = null
    private var buildOutputLocationResolver: ((TASK) -> DirectoryProperty)? = null

    /**
     * Internal API to set the location of the directory where the produced [FILE_TYPE] should
     * be located in.
     *
     * @param location a directory absolute path
     */
    fun atLocation(location: String?): SingleInitialProviderRequestImpl<TASK, FILE_TYPE> {
        buildOutputLocation = location
        return this
    }

    /**
     * Internal API to set the location of the directory where the produced [FILE_TYPE] should be
     * located in.
     *
     * @param location a method reference on the [TASK] to return a [DirectoryProperty]
     */
    fun atLocation(location: (TASK) -> DirectoryProperty)
            : SingleInitialProviderRequestImpl<TASK, FILE_TYPE> {
        buildOutputLocationResolver = location
        return this
    }

    fun withName(name: String): SingleInitialProviderRequestImpl<TASK, FILE_TYPE> {
        fileName = name
        return this
    }

    fun <ARTIFACT_TYPE> on(type: ARTIFACT_TYPE)
        where ARTIFACT_TYPE : ArtifactType<FILE_TYPE>,
              ARTIFACT_TYPE : ArtifactType.Single {

        val artifactContainer = operationsImpl.getArtifactContainer(type)
        taskProvider.configure {
            // Regular-file artifacts require a file name (see bug 151076862)
            if (type.kind is ArtifactKind.FILE && fileName == null) {
                fileName = DEFAULT_FILE_NAME_OF_REGULAR_FILE_ARTIFACTS
            }
            val outputAbsolutePath = when {
                buildOutputLocation != null -> {
                    File(buildOutputLocation, fileName.orEmpty())
                }
                buildOutputLocationResolver != null -> {
                    val resolver = buildOutputLocationResolver!!
                    File(resolver.invoke(it).get().asFile, fileName.orEmpty())
                }
                else -> {
                    operationsImpl.getOutputPath(type,
                        if (artifactContainer.hasCustomProviders()) taskProvider.name else "",
                        fileName.orEmpty())
                }
            }
            // since the taskProvider will execute, resolve its output path.
            from(it).set(outputAbsolutePath)
        }
        artifactContainer.setInitialProvider(taskProvider.flatMap { from(it) })
    }
}

const val DEFAULT_FILE_NAME_OF_REGULAR_FILE_ARTIFACTS = "out"