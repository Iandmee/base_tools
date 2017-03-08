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

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.annotations.concurrency.Immutable;
import com.android.build.api.transform.DirectoryInput;
import com.android.build.api.transform.JarInput;
import com.android.build.api.transform.QualifiedContent;
import com.android.build.api.transform.QualifiedContent.ContentType;
import com.android.build.api.transform.QualifiedContent.Scope;
import com.android.build.api.transform.Status;
import com.android.build.api.transform.TransformInput;
import com.google.common.base.Charsets;
import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.hash.Hashing;
import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.gradle.api.Project;
import org.gradle.api.artifacts.ArtifactCollection;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.artifacts.result.ResolvedArtifactResult;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.FileCollection;

/**
 * Version of TransformStream handling input that is not generated by transforms.
 */
@Immutable
public class OriginalStream extends TransformStream {

    /** group id for local jars, including the ':' separating the groupId from artifactId */
    public static final String LOCAL_JAR_GROUPID = "android.local.jars:";

    @Nullable private final ArtifactCollection artifactCollection;

    public static Builder builder(Project project) {
        return new Builder(project);
    }

    public static final class Builder {
        private final Project project;
        private Set<ContentType> contentTypes = Sets.newHashSet();
        private QualifiedContent.ScopeType scope;
        private FileCollection fileCollection;
        private Supplier<Collection<File>> jarFiles;
        private Supplier<Collection<File>> folders;
        private ImmutableList<? extends Object> dependencies;
        private ArtifactCollection artifactCollection;

        public Builder(Project project) {
            this.project = project;
        }

        public OriginalStream build() {
            checkNotNull(scope);
            checkState(!contentTypes.isEmpty());

            FileCollection fc;

            if (fileCollection != null) {
                fc = fileCollection;

            } else if (artifactCollection != null) {
                fc = artifactCollection.getArtifactFiles();
            } else {
                // create a file collection with the files and the dependencies.
                ConfigurableFileCollection fc2 =
                        project.files(
                                (Callable<Object>)
                                        () -> {
                                            if (jarFiles != null && folders != null) {
                                                return ImmutableList.of(
                                                        jarFiles.get(), folders.get());
                                            }
                                            if (jarFiles != null) {
                                                return jarFiles.get();
                                            }

                                            if (folders != null) {
                                                return folders.get();
                                            }

                                            return ImmutableList.of();
                                        });

                if (dependencies != null) {
                    fc2.builtBy(dependencies.toArray());
                }

                fc = fc2;
            }

            return new OriginalStream(
                    ImmutableSet.copyOf(contentTypes), scope, artifactCollection, fc);
        }

        public Builder addContentTypes(@NonNull Set<ContentType> types) {
            this.contentTypes.addAll(types);
            return this;
        }

        public Builder addContentTypes(@NonNull ContentType... types) {
            this.contentTypes.addAll(Arrays.asList(types));
            return this;
        }

        public Builder addContentType(@NonNull ContentType type) {
            this.contentTypes.add(type);
            return this;
        }

        public Builder addScope(@NonNull QualifiedContent.ScopeType scope) {
            this.scope = scope;
            return this;
        }

        /** @Deprecated use {@link #setFileCollection(FileCollection)} */
        @Deprecated
        public Builder setJar(@NonNull final File jarFile) {
            Preconditions.checkState(
                    fileCollection == null && artifactCollection == null,
                    "Cannot set file collection, artifact collection and jars/folders at the same time");
            this.jarFiles = () -> ImmutableList.of(jarFile);
            return this;
        }

        /** @Deprecated use {@link #setFileCollection(FileCollection)} */
        @Deprecated
        public Builder setJars(@NonNull Supplier<Collection<File>> jarSupplier) {
            Preconditions.checkState(
                    fileCollection == null && artifactCollection == null,
                    "Cannot set file collection, artifact collection and jars/folders at the same time");
            this.jarFiles = jarSupplier;
            return this;
        }

        /** @Deprecated use {@link #setFileCollection(FileCollection)} */
        @Deprecated
        public Builder setFolder(@NonNull final File folder) {
            Preconditions.checkState(
                    fileCollection == null && artifactCollection == null,
                    "Cannot set file collection, artifact collection and jars/folders at the same time");
            this.folders = () -> ImmutableList.of(folder);
            return this;
        }

        /** @Deprecated use {@link #setFileCollection(FileCollection)} */
        @Deprecated
        public Builder setFolders(@NonNull Supplier<Collection<File>> folderSupplier) {
            Preconditions.checkState(
                    fileCollection == null && artifactCollection == null,
                    "Cannot set file collection, artifact collection and jars/folders at the same time");
            this.folders = folderSupplier;
            return this;
        }

        /** @Deprecated use {@link #setFileCollection(FileCollection)} */
        @Deprecated
        public Builder setDependencies(@NonNull List<? extends Object> dependencies) {
            Preconditions.checkState(
                    fileCollection == null && artifactCollection == null,
                    "Cannot set dependency when file collection or artifact collection is used");
            this.dependencies = ImmutableList.copyOf(dependencies);
            return this;
        }

        /** @Deprecated use {@link #setFileCollection(FileCollection)} */
        @Deprecated
        public Builder setDependency(@NonNull Object dependency) {
            Preconditions.checkState(
                    fileCollection == null && artifactCollection == null,
                    "Cannot set dependency when file collection or artifact collection is used");
            this.dependencies = ImmutableList.of(dependency);
            return this;
        }

        public Builder setFileCollection(@NonNull FileCollection fileCollection) {
            Preconditions.checkState(
                    jarFiles == null
                            && folders == null
                            && dependencies == null
                            && artifactCollection == null,
                    "Cannot set file collection, artifact collection and jars/folders at the same time");
            this.fileCollection = fileCollection;
            return this;
        }

        public Builder setArtifactCollection(@NonNull ArtifactCollection artifactCollection) {
            Preconditions.checkState(
                    jarFiles == null
                            && folders == null
                            && dependencies == null
                            && fileCollection == null,
                    "Cannot set file collection, artifact collection and jars/folders at the same time");
            this.artifactCollection = artifactCollection;
            return this;
        }
    }

    private OriginalStream(
            @NonNull Set<ContentType> contentTypes,
            @NonNull QualifiedContent.ScopeType scope,
            @Nullable ArtifactCollection artifactCollection,
            @NonNull FileCollection files) {
        super(contentTypes, ImmutableSet.of(scope), files);
        this.artifactCollection = artifactCollection;
    }

    private static class OriginalTransformInput extends IncrementalTransformInput {

        @Override
        protected boolean checkRemovedFolder(
                @NonNull Set<? super Scope> transformScopes,
                @NonNull Set<ContentType> transformInputTypes,
                @NonNull File file,
                @NonNull List<String> fileSegments) {
            // we can never detect if a random file was removed from this input.
            return false;
        }

        @Override
        boolean checkRemovedJarFile(
                @NonNull Set<? super Scope> transformScopes,
                @NonNull Set<ContentType> transformInputTypes,
                @NonNull File file,
                @NonNull List<String> fileSegments) {
            // we can never detect if a jar was removed from this input.
            return false;
        }
    }

    @NonNull
    @Override
    TransformInput asNonIncrementalInput() {
        Set<ContentType> contentTypes = getContentTypes();
        Set<? super Scope> scopes = getScopes();

        List<JarInput> jarInputs;
        List<DirectoryInput> directoryInputs;

        if (artifactCollection != null) {
            jarInputs = Lists.newArrayList();
            directoryInputs = Lists.newArrayList();

            final Set<ResolvedArtifactResult> artifacts = artifactCollection.getArtifacts();
            Map<ComponentIdentifier, Integer> duplicates = computeDuplicateList(artifacts);
            for (ResolvedArtifactResult result : artifacts) {
                File artifactFile = result.getFile();

                if (artifactFile.isFile()) {
                    jarInputs.add(
                            new ImmutableJarInput(
                                    getArtifactName(result, duplicates),
                                    artifactFile,
                                    Status.NOTCHANGED,
                                    contentTypes,
                                    scopes));
                } else {
                    directoryInputs.add(
                            new ImmutableDirectoryInput(
                                    getArtifactName(result, duplicates),
                                    artifactFile,
                                    contentTypes,
                                    scopes));
                }
            }
        } else {
            Set<File> files = getFileCollection().getFiles();

            jarInputs =
                    files.stream()
                            .filter(File::isFile)
                            .map(
                                    file ->
                                            new ImmutableJarInput(
                                                    getUniqueInputName(file),
                                                    file,
                                                    Status.NOTCHANGED,
                                                    contentTypes,
                                                    scopes))
                            .collect(Collectors.toList());

            directoryInputs =
                    files.stream()
                            .filter(File::isDirectory)
                            .map(
                                    file ->
                                            new ImmutableDirectoryInput(
                                                    getUniqueInputName(file),
                                                    file,
                                                    contentTypes,
                                                    scopes))
                            .collect(Collectors.toList());
        }

        return new ImmutableTransformInput(jarInputs, directoryInputs, null);
    }

    @NonNull
    @Override
    IncrementalTransformInput asIncrementalInput() {
        IncrementalTransformInput input = new OriginalTransformInput();

        Set<ContentType> contentTypes = getContentTypes();
        Set<? super Scope> scopes = getScopes();

        if (artifactCollection != null) {
            final Set<ResolvedArtifactResult> artifacts = artifactCollection.getArtifacts();
            Map<ComponentIdentifier, Integer> duplicates = computeDuplicateList(artifacts);
            for (ResolvedArtifactResult result : artifacts) {
                File artifactFile = result.getFile();

                if (artifactFile.isDirectory()) {
                    input.addFolderInput(
                            new MutableDirectoryInput(
                                    getArtifactName(result, duplicates),
                                    artifactFile,
                                    contentTypes,
                                    scopes));
                } else if (artifactFile.isFile()) {
                    input.addJarInput(
                            new QualifiedContentImpl(
                                    getArtifactName(result, duplicates),
                                    artifactFile,
                                    contentTypes,
                                    scopes));
                }
            }

        } else {
            getFileCollection()
                    .getFiles()
                    .forEach(
                            file -> {
                                if (file.isDirectory()) {
                                    input.addFolderInput(
                                            new MutableDirectoryInput(
                                                    getUniqueInputName(file),
                                                    file,
                                                    contentTypes,
                                                    scopes));
                                } else if (file.isFile()) {
                                    input.addJarInput(
                                            new QualifiedContentImpl(
                                                    getUniqueInputName(file),
                                                    file,
                                                    contentTypes,
                                                    scopes));
                                }
                            });
        }

        return input;
    }

    private static Map<ComponentIdentifier, Integer> computeDuplicateList(
            @NonNull Collection<ResolvedArtifactResult> artifacts) {
        Set<ComponentIdentifier> found = Sets.newHashSetWithExpectedSize(artifacts.size());
        Set<ComponentIdentifier> duplicates = Sets.newHashSet();
        for (ResolvedArtifactResult artifact : artifacts) {
            ComponentIdentifier id = artifact.getId().getComponentIdentifier();
            if (found.contains(id)) {
                duplicates.add(id);
            } else {
                found.add(id);
            }
        }

        Map<ComponentIdentifier, Integer> result =
                Maps.newHashMapWithExpectedSize(duplicates.size());
        Integer zero = 0;
        for (ComponentIdentifier duplicate : duplicates) {
            result.put(duplicate, zero);
        }

        return result;
    }

    @NonNull
    private static String getArtifactName(
            @NonNull ResolvedArtifactResult artifactResult,
            @NonNull Map<ComponentIdentifier, Integer> deduplicationMap) {
        ComponentIdentifier id = artifactResult.getId().getComponentIdentifier();

        String baseName;

        if (id instanceof ProjectComponentIdentifier) {
            baseName = ((ProjectComponentIdentifier) id).getProjectPath();
        } else if (id instanceof ModuleComponentIdentifier) {
            baseName = id.getDisplayName();
        } else {
            // this is a local jar
            File artifactFile = artifactResult.getFile();

            baseName =
                    LOCAL_JAR_GROUPID
                            + artifactFile.getName()
                            + ":"
                            + Hashing.sha1()
                                    .hashString(artifactFile.getPath(), Charsets.UTF_16LE)
                                    .toString();
        }

        // loop for duplicates. This can happen for instance in case of an AAR with local Jars,
        // since all the jars will have the same coordinates.
        Integer i =
                deduplicationMap.compute(
                        id,
                        (componentIdentifier, value) -> {
                            if (value == null) {
                                return null;
                            }

                            return value + 1;
                        });
        if (i != null) {
            return baseName + "::" + i;
        }

        return baseName;
    }

    @NonNull
    private static String getUniqueInputName(@NonNull File file) {
        return Hashing.sha1().hashString(file.getPath(), Charsets.UTF_16LE).toString();
    }

    @NonNull
    @Override
    TransformStream makeRestrictedCopy(
            @NonNull Set<ContentType> types,
            @NonNull Set<? super Scope> scopes) {
        if (!scopes.equals(getScopes())) {
            // since the content itself (jars and folders) don't have they own notion of scopes
            // we cannot do a restricted stream. However, since this stream is always created
            // with a single stream, this shouldn't happen.
            throw new UnsupportedOperationException("Cannot do a scope-restricted OriginalStream");
        }
        return new OriginalStream(
                types,
                (QualifiedContent.ScopeType) Iterables.getOnlyElement(scopes),
                artifactCollection,
                getFileCollection());
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("scopes", getScopes())
                .add("contentTypes", getContentTypes())
                .add("fileCollection", getFileCollection())
                .toString();
    }
}
