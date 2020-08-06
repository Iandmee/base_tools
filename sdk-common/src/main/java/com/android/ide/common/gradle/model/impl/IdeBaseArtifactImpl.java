/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.ide.common.gradle.model.impl;

import static com.android.builder.model.AndroidProject.ARTIFACT_ANDROID_TEST;
import static com.android.builder.model.AndroidProject.ARTIFACT_UNIT_TEST;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.builder.model.AndroidArtifact;
import com.android.builder.model.BaseArtifact;
import com.android.builder.model.SourceProvider;
import com.android.ide.common.gradle.model.IdeBaseArtifact;
import com.android.ide.common.gradle.model.IdeDependencies;
import com.android.ide.common.gradle.model.IdeSourceProvider;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import java.io.File;
import java.io.Serializable;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import org.jetbrains.annotations.NotNull;

/** Creates a deep copy of a {@link BaseArtifact}. */
public abstract class IdeBaseArtifactImpl implements IdeBaseArtifact, Serializable {
    private static final String[] TEST_ARTIFACT_NAMES = {ARTIFACT_UNIT_TEST, ARTIFACT_ANDROID_TEST};

    // Increase the value when adding/removing fields or when changing the serialization/deserialization mechanism.
    private static final long serialVersionUID = 6L;

    @NonNull private final String myName;
    @NonNull private final String myCompileTaskName;
    @NonNull private final String myPostAssembleModelFile;
    @NonNull private final String myAssembleTaskName;
    @NonNull private final File myClassesFolder;
    @NonNull private final Set<String> myIdeSetupTaskNames;
    @NonNull private final Set<File> myGeneratedSourceFolders;
    @NonNull private final Set<File> myAdditionalClassFolders;

    @NonNull private final IdeDependencies myLevel2Dependencies;

    @Nullable private final File myJavaResourcesFolder;
    @Nullable private final IdeSourceProvider myVariantSourceProvider;
    @Nullable private final IdeSourceProvider myMultiFlavorSourceProvider;
    private final int hashCode;

    // Used for serialization by the IDE.
    IdeBaseArtifactImpl() {
        myName = "";
        myCompileTaskName = "";
        myAssembleTaskName = "";
        myPostAssembleModelFile = "";
        //noinspection ConstantConditions
        myClassesFolder = null;
        myIdeSetupTaskNames = Collections.emptySet();
        myGeneratedSourceFolders = Collections.emptySet();
        myAdditionalClassFolders = Collections.emptySet();

        myLevel2Dependencies = new IdeDependenciesImpl();

        myJavaResourcesFolder = null;
        myVariantSourceProvider = null;
        myMultiFlavorSourceProvider = null;

        hashCode = 0;
    }

    protected IdeBaseArtifactImpl(
            @NotNull String name,
            @NotNull String compileTaskName,
            @NotNull String assembleTaskName,
            @NotNull String postAssembleModelFile,
            @NotNull File classesFolder,
            @Nullable File javaResourcesFolder,
            @NotNull ImmutableSet<String> ideSetupTaskNames,
            @NotNull LinkedHashSet<File> generatedSourceFolders,
            @Nullable IdeSourceProvider variantSourceProvider,
            @Nullable IdeSourceProvider multiFlavorSourceProvider,
            @NotNull Set<File> additionalClassFolders,
            @NotNull IdeDependencies level2Dependencies) {
        myName = name;
        myCompileTaskName = compileTaskName;
        myAssembleTaskName = assembleTaskName;
        myPostAssembleModelFile = postAssembleModelFile;
        myClassesFolder = classesFolder;
        myJavaResourcesFolder = javaResourcesFolder;

        myIdeSetupTaskNames = ideSetupTaskNames;
        myGeneratedSourceFolders = generatedSourceFolders;
        myVariantSourceProvider = variantSourceProvider;
        myMultiFlavorSourceProvider = multiFlavorSourceProvider;
        myAdditionalClassFolders = additionalClassFolders;
        myLevel2Dependencies = level2Dependencies;
        hashCode = calculateHashCode();
    }

    @NonNull
    public static Set<String> getIdeSetupTaskNames(@NonNull BaseArtifact artifact) {
        try {
            // This method was added in 1.1 - we have to handle the case when it's missing on the Gradle side.
            return ImmutableSet.copyOf(artifact.getIdeSetupTaskNames());
        } catch (NoSuchMethodError | UnsupportedOperationException e) {
            if (artifact instanceof AndroidArtifact) {
                return Collections.singleton(((AndroidArtifact) artifact).getSourceGenTaskName());
            }
        }
        return Collections.emptySet();
    }

    @NonNull
    public static Collection<File> getGeneratedSourceFolders(@NonNull BaseArtifact artifact) {
        try {
            Collection<File> folders = artifact.getGeneratedSourceFolders();
            // JavaArtifactImpl#getGeneratedSourceFolders returns null even though BaseArtifact#getGeneratedSourceFolders is marked as @NonNull.
            // See https://code.google.com/p/android/issues/detail?id=216236
            //noinspection ConstantConditions
            return folders != null ? folders : Collections.emptyList();
        } catch (UnsupportedOperationException e) {
            // Model older than 1.2.
        }
        return Collections.emptyList();
    }

    @Nullable
    public static IdeSourceProvider createSourceProvider(
            @NonNull ModelCache modelCache, @Nullable SourceProvider original) {
        return original != null
                ? modelCache.copyModel(
                        original,
                        provider ->
                                IdeSourceProviderImpl.createFrom(
                                        provider, modelCache::deduplicateString))
                : null;
    }

    @Override
    @NonNull
    public String getName() {
        return myName;
    }

    @Override
    @NonNull
    public String getCompileTaskName() {
        return myCompileTaskName;
    }

    @Override
    @NonNull
    public String getAssembleTaskName() {
        return myAssembleTaskName;
    }

    @NonNull
    @Override
    public String getAssembleTaskOutputListingFile() {
        return myPostAssembleModelFile;
    }

    @Override
    @NonNull
    public File getClassesFolder() {
        return myClassesFolder;
    }

    @Override
    @NonNull
    public File getJavaResourcesFolder() {
        if (myJavaResourcesFolder != null) {
            return myJavaResourcesFolder;
        }
        throw new UnsupportedOperationException(
                "Unsupported method: BaseArtifact.getJavaResourcesFolder");
    }

    @Override
    @NonNull
    public Set<String> getIdeSetupTaskNames() {
        return myIdeSetupTaskNames;
    }

    @Override
    @NonNull
    public IdeDependencies getLevel2Dependencies() {
        return myLevel2Dependencies;
    }

    @Override
    @NonNull
    public Collection<File> getGeneratedSourceFolders() {
        return ImmutableList.copyOf(myGeneratedSourceFolders);
    }

    @Override
    public void addGeneratedSourceFolder(@NonNull File generatedSourceFolder) {
        myGeneratedSourceFolders.add(generatedSourceFolder);
    }

    @Override
    @Nullable
    public IdeSourceProvider getVariantSourceProvider() {
        return myVariantSourceProvider;
    }

    @Override
    @Nullable
    public IdeSourceProvider getMultiFlavorSourceProvider() {
        return myMultiFlavorSourceProvider;
    }

    @Override
    public @NotNull Set<File> getAdditionalClassesFolders() {
        return myAdditionalClassFolders;
    }

    @Override
    public boolean isTestArtifact() {
        return Arrays.asList(TEST_ARTIFACT_NAMES).contains(myName);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof IdeBaseArtifactImpl)) {
            return false;
        }
        IdeBaseArtifactImpl artifact = (IdeBaseArtifactImpl) o;
        return artifact.canEquals(this)
                && Objects.equals(myName, artifact.myName)
                && Objects.equals(myCompileTaskName, artifact.myCompileTaskName)
                && Objects.equals(myAssembleTaskName, artifact.myAssembleTaskName)
                && Objects.equals(myPostAssembleModelFile, artifact.myPostAssembleModelFile)
                && Objects.equals(myClassesFolder, artifact.myClassesFolder)
                && Objects.equals(myAdditionalClassFolders, artifact.myAdditionalClassFolders)
                && Objects.equals(myJavaResourcesFolder, artifact.myJavaResourcesFolder)
                && Objects.equals(myLevel2Dependencies, artifact.myLevel2Dependencies)
                && Objects.equals(myIdeSetupTaskNames, artifact.myIdeSetupTaskNames)
                && Objects.equals(myGeneratedSourceFolders, artifact.myGeneratedSourceFolders)
                && Objects.equals(myVariantSourceProvider, artifact.myVariantSourceProvider)
                && Objects.equals(
                        myMultiFlavorSourceProvider, artifact.myMultiFlavorSourceProvider);
    }

    protected boolean canEquals(Object other) {
        return other instanceof IdeBaseArtifactImpl;
    }

    @Override
    public int hashCode() {
        return hashCode;
    }

    protected int calculateHashCode() {
        return Objects.hash(
                myName,
                myCompileTaskName,
                myAssembleTaskName,
                myPostAssembleModelFile,
                myClassesFolder,
                myJavaResourcesFolder,
                myLevel2Dependencies,
                myIdeSetupTaskNames,
                myGeneratedSourceFolders,
                myVariantSourceProvider,
                myMultiFlavorSourceProvider,
                myAdditionalClassFolders);
    }

    @Override
    public String toString() {
        return "myName='"
                + myName
                + '\''
                + ", myCompileTaskName='"
                + myCompileTaskName
                + '\''
                + ", myAssembleTaskName='"
                + myAssembleTaskName
                + '\''
                + ", myClassesFolder="
                + myClassesFolder
                + ", myJavaResourcesFolder="
                + myJavaResourcesFolder
                + ", myLevel2Dependencies"
                + myLevel2Dependencies
                + ", myIdeSetupTaskNames="
                + myIdeSetupTaskNames
                + ", myGeneratedSourceFolders="
                + myGeneratedSourceFolders
                + ", myVariantSourceProvider="
                + myVariantSourceProvider
                + ", myMultiFlavorSourceProvider="
                + myMultiFlavorSourceProvider;
    }
}
