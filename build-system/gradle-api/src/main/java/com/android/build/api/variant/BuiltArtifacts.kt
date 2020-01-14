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

package com.android.build.api.variant

import com.android.build.api.artifact.ArtifactType
import org.gradle.api.Incubating
import org.gradle.api.file.Directory
import org.gradle.api.file.RegularFile
import org.gradle.workers.WorkAction
import org.gradle.workers.WorkParameters
import org.gradle.workers.WorkQueue
import java.io.File
import java.io.Serializable
import java.util.ServiceLoader
import java.util.function.Supplier

/**
 * Represents a [Collection] of [BuiltArtifact] produced by a [org.gradle.api.Task].
 *
 * Tasks in Android Gradle Plugin can produce more than one file in the output folder so any
 * [ArtifactType] with a [ArtifactType.kind] of type
 * [com.android.build.api.artifact.ArtifactKind.DIRECTORY] can actually contain several produced
 * [File]. For instance, when dealing with multi-apk, there will be several manifest files or APKs
 * produced by the Android Gradle Plugin.
 *
 * Each produced file can be identified with unique metadata like the list of filters and/or
 * version code or version name. This instance will allow producer and consumer
 * [org.gradle.api.Task]s to easily match produced files with this metadata without relying on
 * name mangling or other custom solutions.
 *
 * Simple use of this facility can look like :
 *
 * <pre><code>
 * abstract class MyTask @inject constructor(val objectFactory: ObjectFactory): DefaultTask() {
 *     @InputDirectory
 *     abstract val input: DirectoryProperty
 *     @OutputDirectory
 *     abstract val output: DirectoryProperty
 *
 *     @TaskAction
 *     fun taskAction() {
 *          val builtArtifacts= BuiltArtifacts.Loader.loadFromFolder(
 *               objectFactory, input.get())
 *
 *          val newBuiltArtifacts = builtArtifacts.transform(PublicArtifactType.APK) {
 *              ... transform input into a new file...
 *          }
 *
 *          newBuiltArtifacts.save(output.get()))
 *     }
 * }
 * </code></pre>
 *
 * This [BuiltArtifacts] will abstract access to these produced files and provided some metadata
 * associated with each file to be able to identify filters, version code or version name.
 */
@Incubating
interface BuiltArtifacts {

    @Incubating
    companion object {
        /**
         * Current version of the metadata file.
         */
        const val METADATA_FILE_VERSION = 2

        /**
         * Provides an implementation of [BuiltArtifactsLoader]
         */
        @JvmStatic
        val Loader: BuiltArtifactsLoader by lazy {
            var loadedServices = ServiceLoader.load(
                BuiltArtifactsLoader::class.java,
                BuiltArtifactsLoader::class.java.classLoader
            )
            if (!loadedServices.iterator().hasNext()) {
                loadedServices = ServiceLoader.load(BuiltArtifactsLoader::class.java)
            }
            loadedServices.first()
        }
    }

    /**
     * Identifies the [ArtifactType] for this [Collection] of [BuiltArtifact], all [BuiltArtifact]
     * are the same type of artifact.
     *
     * @return the [ArtifactType] for all the [BuiltArtifact] instances.
     */
    val artifactType: ArtifactType<*>

    /**
     * Returns the application ID for these [BuiltArtifact] instances.
     *
     * @return the application ID.
     */
    val applicationId: String

    /**
     * Identifies the variant name for these [BuiltArtifact]
     */
    val variantName: String

    /**
     * Returns the [Collection] of [BuiltArtifact].
     */
    val elements: Collection<BuiltArtifact>

    /**
     * Transforms this [Collection] of [BuiltArtifact] into a new instance of newly produced
     * [BuiltArtifact] with a new [ArtifactType].
     *
     * This convenience method can be used by [org.gradle.api.Task] implementation to easily
     * transforms input [BuiltArtifacts] into a new [Collection] of [BuiltArtifact]. The new
     * [BuiltArtifacts] instance can be used to save the metadata associated with the new produced
     * files.
     *
     * @param newArtifactType the new [ArtifactType] that identifies the new produced files.
     * @param transformer the lambda that transforms each element of [BuiltArtifacts.elements] into
     * a new file. All the metadata associated with the input like filters, versions will be
     * automatically transferred to create a new instance of [BuiltArtifact] that will be added
     * to the returned [BuiltArtifacts] instance.
     * @return a new instance of [BuiltArtifacts] with updated artifact type and elements as
     * provided by the [transformer] lambda.
     */
    fun transform(newArtifactType: ArtifactType<Directory>,
        transformer: (input: BuiltArtifact) -> File): BuiltArtifacts

    /**
     * Transforms this [Collection] of [BuiltArtifact] into a new instance of newly produced
     * [BuiltArtifact] with a new [ArtifactType].
     *
     * This convenience method can be used by [org.gradle.api.Task] implementation to easily
     * transforms input [BuiltArtifacts] into a new [Collection] of [BuiltArtifact] using a
     * [WorkQueue]. Each transformation will be [WorkQueue.submit]ed with the instances of
     * [transformRunnableClass], with parameters of type [ParamT] injected and available through the
     * [WorkAction.getParameters] method.
     *
     * Instances of [ParamT] are created by Gradle and configured by the method
     * [parameterConfigurator]. This method will be provided with one of the element in [elements]
     * must configure that passed instance of [ParamT] to contains all the necessary inputs for
     * the [transformRunnableClass] action.
     *
     * Once the work item is completed, the result is expected to be stored into the
     * [TransformParams.output] field.
     *
     * A [Supplier] of [BuiltArtifacts] is returned and callers can block on all work items
     * completion by doing [Supplier.get] if needed.
     *
     * The new [BuiltArtifacts] instance can be used to save the metadata associated with the
     * new produced files.
     *
     * @param newArtifactType the new [ArtifactType] that identifies the new produced files.
     * @param workers a Gradle [WorkQueue] that can be used to submit work items.
     * @param transformRunnableClass [WorkAction] subclass that implements the work item.
     * @param parameterConfigurator a factory lambda to create instances of [ParamT] provided with an
     * input [BuiltArtifact].
     */
    fun <ParamT : TransformParams> transform(
        newArtifactType: ArtifactType<Directory>,
        workers: WorkQueue,
        transformRunnableClass: Class<out WorkAction<ParamT>>,
        parameterConfigurator: (builtArtifact: BuiltArtifact, parameters: ParamT) -> Unit
        ) : Supplier<BuiltArtifacts>

    /**
     * Saves the metadata associated with this instance into a folder.
     * @param out the [Directory] that can be used to save the metadata using a standard file
     * name.
     */
    fun save(out: Directory)

    /**
     * Specialized version  of Gradle's [WorkParameters] so we can retrieve the output file
     * generated when transforming an instance of [BuiltArtifacts] into a new one.
     */
    @Incubating
    interface TransformParams: WorkParameters, Serializable {
        /**
         * Result of the work item submission must be made available through this field.
         */
        val output: File
    }
}