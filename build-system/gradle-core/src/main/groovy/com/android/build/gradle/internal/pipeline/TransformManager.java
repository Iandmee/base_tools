/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.build.gradle.internal.pipeline;

import static com.android.build.transform.api.QualifiedContent.ContentType.CLASSES;
import static com.android.build.transform.api.QualifiedContent.ContentType.DEX;
import static com.android.build.transform.api.QualifiedContent.ContentType.NATIVE_LIBS;
import static com.android.build.transform.api.QualifiedContent.ContentType.RESOURCES;
import static com.android.utils.StringHelper.capitalize;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.internal.TaskFactory;
import com.android.build.gradle.internal.scope.AndroidTask;
import com.android.build.gradle.internal.scope.AndroidTaskRegistry;
import com.android.build.gradle.internal.scope.BaseScope;
import com.android.build.transform.api.QualifiedContent.ContentType;
import com.android.build.transform.api.QualifiedContent.Scope;
import com.android.build.transform.api.Transform;
import com.android.builder.model.AndroidProject;
import com.android.utils.FileUtils;
import com.android.utils.StringHelper;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

import java.io.File;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Manages the transforms for a variant.
 *
 * Th actual execution is handled by Gradle through the tasks.
 * Instead it's a mean to more easily configure a series of transforms that consume each other's
 * inputs when several of these transform are optional.
 */
public class TransformManager extends FilterableStreamCollection {

    private static final boolean DEBUG = false;

    private static final String FD_TRANSFORMS = "transforms";

    public static final Set<Scope> EMPTY_SCOPES = ImmutableSet.of();

    public static final Set<ContentType> CONTENT_CLASS = Sets.immutableEnumSet(CLASSES);
    public static final Set<ContentType> CONTENT_JARS = Sets.immutableEnumSet(CLASSES, RESOURCES);
    public static final Set<ContentType> CONTENT_RESOURCES = Sets.immutableEnumSet(RESOURCES);
    public static final Set<ContentType> CONTENT_NATIVE_LIBS = Sets.immutableEnumSet(NATIVE_LIBS);
    public static final Set<ContentType> CONTENT_DEX = Sets.immutableEnumSet(DEX);
    public static final Set<Scope> SCOPE_FULL_PROJECT = Sets.immutableEnumSet(
            Scope.PROJECT,
            Scope.PROJECT_LOCAL_DEPS,
            Scope.SUB_PROJECTS,
            Scope.SUB_PROJECTS_LOCAL_DEPS,
            Scope.EXTERNAL_LIBRARIES);
    public static final Set<Scope> SCOPE_FULL_LIBRARY = Sets.immutableEnumSet(
            Scope.PROJECT,
            Scope.PROJECT_LOCAL_DEPS);

    @NonNull
    private final AndroidTaskRegistry taskRegistry;

    /**
     * These are the streams that are available for new Transforms to consume.
     *
     * Once a new transform is added, the streams that it consumes are removed from this list,
     * and the streams it produces are put instead.
     *
     * When all the transforms have been added, the remaining streams should be consumed by
     * standard Tasks somehow.
     *
     * @see #getStreams(StreamFilter)
     */
    @NonNull
    private final List<TransformStream> streams = Lists.newArrayList();

    private final List<Transform> transforms = Lists.newArrayList();

    public TransformManager(@NonNull AndroidTaskRegistry taskRegistry) {
        this.taskRegistry = taskRegistry;
    }

    @NonNull
    public AndroidTaskRegistry getTaskRegistry() {
        return taskRegistry;
    }

    public void addStream(@NonNull TransformStream stream) {
        streams.add(stream);
    }

    public <T extends Transform> AndroidTask<TransformTask> addTransform(
            @NonNull TaskFactory taskFactory,
            @NonNull BaseScope variantScope,
            @NonNull T transform) {
        return addTransform(taskFactory, variantScope, transform, null /*callback*/);
    }

    /**
     * Adds a Transform.
     *
     * This makes the current transform consumes whatever Streams are currently available and
     * creates new ones for the transform output.
     *
     * This also creates a {@link TransformTask} to run the transform and wire it up with the
     * dependencies of the consumed streams.
     *
     * @param taskFactory the task factory
     * @param scope the current scope
     * @param transform the transform to add
     * @param callback a callback that is run when the task is actually configured
     * @param <T> the type of the transform
     * @return the AndroidTask for the given transform task.
     */
    public <T extends Transform> AndroidTask<TransformTask> addTransform(
            @NonNull TaskFactory taskFactory,
            @NonNull BaseScope scope,
            @NonNull T transform,
            @Nullable TransformTask.ConfigActionCallback<T> callback) {

        List<TransformStream> inputStreams = Lists.newArrayList();
        String taskName = scope.getTaskName(getTaskNamePrefix(transform));

        // get referenced-only streams
        List<TransformStream> referencedStreams = grabReferencedStreams(transform);

        // find input streams, and compute output streams for the transform.
        IntermediateStream outputStream = findTransformStreams(
                transform,
                scope,
                inputStreams,
                taskName,
                scope.getGlobalScope().getBuildDir());

        if (inputStreams.isEmpty() && referencedStreams.isEmpty()) {
            // didn't find any match. Means there is a broken order somewhere in the streams.
            throw new RuntimeException(String.format(
                    "Unable to add Transform '%s' on variant '%s': requested streams not available: %s+%s / %s",
                    transform.getName(), scope.getVariantConfiguration().getFullName(),
                    transform.getScopes(), transform.getReferencedScopes(), transform.getInputTypes()));
        }

        if (DEBUG) {
            System.out.println(
                    "ADDED TRANSFORM(" + scope.getVariantConfiguration().getFullName()
                            + "):");
            System.out.println("\tName: " + transform.getName());
            System.out.println("\tTask: " + taskName);
            for (TransformStream sd : inputStreams) {
                System.out.println("\tInputStream: " + sd);
            }
            for (TransformStream sd : referencedStreams) {
                System.out.println("\tRef'edStream: " + sd);
            }
            if (outputStream != null) {
                System.out.println("\tOutputStream: " + outputStream);
            }
        }

        transforms.add(transform);

        // create the task...
        AndroidTask<TransformTask> task = taskRegistry.create(
                taskFactory,
                new TransformTask.ConfigAction<T>(
                        scope.getVariantConfiguration().getFullName(),
                        taskName,
                        transform,
                        inputStreams,
                        referencedStreams,
                        outputStream,
                        callback));

        for (TransformStream s : inputStreams) {
            task.dependsOn(taskFactory, s.getDependencies());
        }
        for (TransformStream s : referencedStreams) {
            task.dependsOn(taskFactory, s.getDependencies());
        }

        return task;
    }

    @Override
    @NonNull
    public List<TransformStream> getStreams() {
        return streams;
    }



    @NonNull
    private static String getTaskNamePrefix(@NonNull Transform transform) {
        StringBuilder sb = new StringBuilder(100);
        sb.append("transform");

        Iterator<ContentType> iterator = transform.getInputTypes().iterator();
        // there's always at least one
        sb.append(capitalize(iterator.next().name().toLowerCase(Locale.getDefault())));
        while (iterator.hasNext()) {
            sb.append("And").append(capitalize(
                    iterator.next().name().toLowerCase(Locale.getDefault())));
        }

        sb.append("With").append(capitalize(transform.getName())).append("For");

        return sb.toString();
    }

    /**
     * Finds the stream the transform consumes, and return them.
     *
     * This also removes them from the instance list. They will be replaced with the output
     * stream(s) from the transform.
     *
     * This returns an optional output stream.
     *
     * @param transform the transform.
     * @param scope the scope the transform is applied to.
     * @param inputStreams the out list of input streams for the transform.
     * @param taskName the name of the task that will run the transform
     * @param buildDir the build dir of the project.
     * @return the output stream if any.
     */
    @Nullable
    private IntermediateStream findTransformStreams(
            @NonNull Transform transform,
            @NonNull BaseScope scope,
            @NonNull List<TransformStream> inputStreams,
            @NonNull String taskName,
            @NonNull File buildDir) {

        Set<Scope> requestedScopes = transform.getScopes();
        if (requestedScopes.isEmpty()) {
            // this is a no-op transform.
            return null;
        }

        Set<ContentType> requestedTypes = transform.getInputTypes();

        // list to hold the list of unused streams in the manager after everything is done.
        // they'll be put back in the streams collection, along with the new outputs.
        List<TransformStream> oldStreams = Lists.newArrayListWithExpectedSize(streams.size());

        for (TransformStream stream : streams) {
            // streams may contain more than we need. In this case, we'll make a copy of the stream
            // with the remaining types/scopes. It'll be up to the TransformTask to make
            // sure that the content of the stream is usable (for instance when a stream
            // may contain two scopes, these scopes could be combined or not, impacting consumption)
            Set<ContentType> availableTypes = stream.getContentTypes();
            Set<Scope> availableScopes = stream.getScopes();

            Set<ContentType> commonTypes = Sets.intersection(requestedTypes,
                    availableTypes);
            Set<Scope> commonScopes = Sets.intersection(requestedScopes, availableScopes);
            if (!commonTypes.isEmpty() && !commonScopes.isEmpty()) {

                // check if we need to make another stream from this one with less scopes/types.
                if (!commonScopes.equals(availableScopes) || !commonTypes.equals(availableTypes)) {
                    // first the stream that gets consumed. It consumes only the common types/scopes
                    inputStreams.add(stream.makeRestrictedCopy(commonTypes, commonScopes));

                    // now we'll have a second stream, that's left for consumption later on.
                    // compute remaining scopes/types.
                    Sets.SetView<ContentType> remainingTypes = Sets.difference(availableTypes, commonTypes);
                    Sets.SetView<Scope> remainingScopes = Sets.difference(availableScopes, commonScopes);

                    oldStreams.add(stream.makeRestrictedCopy(
                            remainingTypes.isEmpty() ? availableTypes : remainingTypes.immutableCopy(),
                            remainingScopes.isEmpty() ? availableScopes : remainingScopes.immutableCopy()));
                } else {
                    // stream is an exact match (or at least subset) for the request,
                    // so we add it as it.
                    inputStreams.add(stream);
                }
            } else {
                // stream is not used, keep it around.
                oldStreams.add(stream);
            }
        }

        // create the output stream.
        // create single combined output stream for all types and scopes
        Set<ContentType> outputTypes = transform.getOutputTypes();

        File outRootFolder = FileUtils.join(buildDir, StringHelper.toStrings(
                AndroidProject.FD_INTERMEDIATES,
                FD_TRANSFORMS,
                transform.getName(),
                scope.getDirectorySegments()));

        // update the list of available streams.
        streams.clear();
        streams.addAll(oldStreams);

        // create the output
        IntermediateStream outputStream = IntermediateStream.builder()
                .addContentTypes(outputTypes)
                .addScopes(requestedScopes)
                .setRootLocation(outRootFolder)
                .setDependency(taskName)
                .build();
        // and add it to the list of available streams for next transforms.
        streams.add(outputStream);

        return outputStream;
    }

    @NonNull
    private List<TransformStream> grabReferencedStreams(@NonNull Transform transform) {
        Set<Scope> requestedScopes = transform.getReferencedScopes();
        if (requestedScopes.isEmpty()) {
            return ImmutableList.of();
        }

        List<TransformStream> streamMatches = Lists.newArrayListWithExpectedSize(streams.size());

        Set<ContentType> requestedTypes = transform.getInputTypes();
        for (TransformStream stream : streams) {
            // streams may contain more than we need. In this case, we'll provide the whole
            // stream as-is since it's not actually consumed.
            // It'll be up to the TransformTask to make sure that the content of the stream is
            // usable (for instance when a stream
            // may contain two scopes, these scopes could be combined or not, impacting consumption)
            Set<ContentType> availableTypes = stream.getContentTypes();
            Set<Scope> availableScopes = stream.getScopes();

            Set<ContentType> commonTypes = Sets.intersection(requestedTypes,
                    availableTypes);
            Set<Scope> commonScopes = Sets.intersection(requestedScopes, availableScopes);

            if (!commonTypes.isEmpty() && !commonScopes.isEmpty()) {
                streamMatches.add(stream);
            }
        }

        return streamMatches;
    }
}
