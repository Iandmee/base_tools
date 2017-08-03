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

package com.android.build.gradle.internal.scope;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import java.io.File;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.FileCollection;

/**
 * a class that can hold task outputs from the build.
 */
public interface TaskOutputHolder {

    /** Represents a type of output. */
    interface OutputType {
        String name();
    }

    /**
     * A type of output generated by a task.
     */
    enum TaskOutputType implements OutputType {
        // --- classes ---
        // These are direct task outputs. If you are looking for all the classes of a
        // module, use AnchorOutputType.ALL_CLASSES
        // Javac task output.
        JAVAC,

        // --- Published classes ---
        // Class-type task output for tasks that generate published classes.

        // Packaged classes for AAR intermediate publishing
        // This is for external usage. For usage inside a module use ALL_CLASSES
        LIBRARY_CLASSES,
        // the packaged classes published by APK modules.
        // This is for external usage. For usage inside a module use ALL_CLASSES
        APP_CLASSES,
        // the packaged classes published by feature modules.
        // This is for external usage. For usage inside a module use ALL_CLASSES
        FEATURE_CLASSES,

        // --- java res ---
        // java processing output
        JAVA_RES,
        // packaged java res for aar intermediate publishing
        LIBRARY_JAVA_RES,

        // Full jar with both classes and java res.
        FULL_JAR,

        // --- android res ---
        // R.TXT with platform attr values
        PLATFORM_R_TXT,
        // output of the resource merger ready for aapt.
        MERGED_RES,
        // output of the resource merger for unit tests and the resource shrinker.
        MERGED_NOT_COMPILED_RES,
        // Directory containing config file for unit testing with resources
        UNIT_TEST_CONFIG_DIRECTORY,
        // compiled resources (output of aapt)
        PROCESSED_RES,
        // package resources for aar publishing.
        PACKAGED_RES,
        // R.txt output
        SYMBOL_LIST,
        // Synthetic artifacts
        SYMBOL_LIST_WITH_PACKAGE_NAME,
        // public.txt output
        PUBLIC_RES,
        SHRUNK_PROCESSED_RES,
        DENSITY_OR_LANGUAGE_SPLIT_PROCESSED_RES,
        ABI_PROCESSED_SPLIT_RES,
        DENSITY_OR_LANGUAGE_PACKAGED_SPLIT,
        INSTANT_RUN_PACKAGED_RESOURCES,

        // --- JNI libs ---
        // packaged JNI for AAR Intermediate publishing
        LIBRARY_JNI,

        MERGED_ASSETS,
        MOCKABLE_JAR,

        // AIDL headers "packaged" by libraries for consumers.
        AIDL_PARCELABLE,
        // renderscript headers "packaged" by libraries for consumers.
        RENDERSCRIPT_HEADERS,

        COMPATIBLE_SCREEN_MANIFEST,
        MERGED_MANIFESTS,
        LIBRARY_MANIFEST,
        AAPT_FRIENDLY_MERGED_MANIFESTS,
        INSTANT_RUN_MERGED_MANIFESTS,
        MANIFEST_METADATA,

        // List of annotation processors for metrics.
        ANNOTATION_PROCESSOR_LIST,

        // the file that consumers of an AAR can use for additional proguard rules.
        CONSUMER_PROGUARD_FILE,

        // the data binding artifact for a library that gets published with the aar
        DATA_BINDING_ARTIFACT,
        // the merged data binding artifacts from all the dependencies
        DATA_BINDING_DEPENDENCY_ARTIFACTS,

        LINT_JAR,

        // the zip file output of the extract annotation class.
        ANNOTATIONS_ZIP,
        // the associated TypeDef file
        ANNOTATIONS_TYPEDEF_FILE,
        // the associated proguard file
        ANNOTATIONS_PROGUARD,

        ABI_PACKAGED_SPLIT,
        FULL_APK,
        APK,
        APK_MAPPING,
        AAR,
        INSTANTAPP_BUNDLE,
        SPLIT_LIST,

        FEATURE_IDS_DECLARATION,
        FEATURE_APPLICATION_ID_DECLARATION,
        FEATURE_RESOURCE_PKG,

        // Project metadata
        METADATA_FEATURE_DECLARATION,
        METADADA_FEATURE_MANIFEST,
        METADATA_APP_ID_DECLARATION,
    }

    /**
     * a Type of output that serves as an anchor for multiple tasks.
     *
     * <p>This ised when a single task consumes outputs (of the same type) coming from different
     * tasks, especially if the number of tasks generating this is be dynamic (either because some
     * tasks are optional based on some parameters or if the API allows for user-added tasks
     * generating the same content.)
     *
     * <p>This allows the consuming task to simply consume a single file collection rather than have
     * to deal with all the different tasks generating the content.
     */
    enum AnchorOutputType implements OutputType {
        GENERATED_RES,
        GENERATED_SRC,
        // anchor for a collection grouping all the generated bytecode
        ALL_CLASSES,
    }

    /**
     * Returns a {@link FileCollection} that contains the requested output type.
     *
     * <p>The collection can be used both as a collection of files and as task dependency to ensure
     * the producers of the files will run before the task consuming them.
     *
     * @param outputType the type of output
     * @return a FileCollection.
     * @throws MissingTaskOutputException if the output type has no associated output.
     * @see #hasOutput(OutputType) to check if there is an associated output.
     */
    @NonNull
    FileCollection getOutput(@NonNull OutputType outputType) throws MissingTaskOutputException;

    /**
     * Tests whether or not the scope contains an output {@link FileCollection} of the requested
     * output type.
     *
     * @param outputType the type of output
     * @return true if the scope contains such an output, false otherwise.
     */
    boolean hasOutput(@NonNull OutputType outputType);

    /**
     * Adds a new Task output.
     *
     * <p>To ensure that task wiring works for both direct and delayed task configuration, this must
     * be called outside of {@link TaskConfigAction}
     *
     * @param outputType the type of the output
     * @param file the output file, accepts any type accepted by {@link
     *     org.gradle.api.Project#files}
     * @param taskName the name of the task that generates the output file.
     * @return the {@link ConfigurableFileCollection} that contains both the file and the task
     *     dependency.
     * @throws TaskOutputAlreadyRegisteredException if the type was already registered.
     */
    ConfigurableFileCollection addTaskOutput(
            @NonNull TaskOutputType outputType, @NonNull Object file, @Nullable String taskName)
            throws TaskOutputAlreadyRegisteredException;

    /**
     * Creates a new anchor output.
     *
     * <p>Once this anchor is created, tasks can add their output (and dependency information) to
     * it.
     *
     * @param outputType the type of the output
     * @return the FileCollection that was created.
     * @throws TaskOutputAlreadyRegisteredException if the type was already registered.
     */
    @NonNull
    ConfigurableFileCollection createAnchorOutput(@NonNull AnchorOutputType outputType)
            throws TaskOutputAlreadyRegisteredException;

    /**
     * Adds a new output to an anchor output.
     *
     * To ensure that task wiring works for both direct and delayed task configuration, this must
     * be called outside of {@link TaskConfigAction}
     *
     * @param outputType the type of the output
     * @param file the output file
     * @param taskName the name of the task that generates the output file.
     */
    void addToAnchorOutput(
            @NonNull AnchorOutputType outputType,
            @NonNull File file,
            @NonNull String taskName);

    /**
     * Adds a new output to an anchor output.
     *
     * To ensure that task wiring works for both direct and delayed task configuration, this must
     * be called outside of {@link TaskConfigAction}
     *
     * @param outputType the type of the output
     * @param fileCollection a collection containing file and dependency information.
     */
    void addToAnchorOutput(
            @NonNull AnchorOutputType outputType,
            @NonNull FileCollection fileCollection);

}
