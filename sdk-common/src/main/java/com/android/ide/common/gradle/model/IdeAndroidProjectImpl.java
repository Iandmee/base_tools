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
package com.android.ide.common.gradle.model;


import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.builder.model.AaptOptions;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.BuildTypeContainer;
import com.android.builder.model.JavaCompileOptions;
import com.android.builder.model.NativeToolchain;
import com.android.builder.model.ProductFlavorContainer;
import com.android.builder.model.ProjectSyncIssues;
import com.android.builder.model.SigningConfig;
import com.android.builder.model.SyncIssue;
import com.android.builder.model.Variant;
import com.android.builder.model.ViewBindingOptions;
import com.android.ide.common.gradle.model.level2.IdeDependenciesFactory;
import com.android.ide.common.repository.GradleVersion;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;
import java.io.File;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

/** Creates a deep copy of an {@link AndroidProject}. */
public final class IdeAndroidProjectImpl implements IdeAndroidProject, Serializable {
    // Increase the value when adding/removing fields or when changing the
    // serialization/deserialization mechanism.
    private static final long serialVersionUID = 7L;

    @NonNull private final String myModelVersion;
    @NonNull private final String myName;
    @NonNull private final ProductFlavorContainer myDefaultConfig;
    @NonNull private final Collection<BuildTypeContainer> myBuildTypes;
    @NonNull private final Collection<ProductFlavorContainer> myProductFlavors;
    @NonNull private final Collection<SyncIssue> mySyncIssues;
    @NonNull private final Collection<Variant> myVariants;
    @NonNull private final Collection<String> myVariantNames;
    @Nullable private final String myDefaultVariant;
    @NonNull private final Collection<String> myFlavorDimensions;
    @NonNull private final String myCompileTarget;
    @NonNull private final Collection<String> myBootClassPath;
    @NonNull private final Collection<NativeToolchain> myNativeToolchains;
    @NonNull private final Collection<SigningConfig> mySigningConfigs;
    @NonNull private final IdeLintOptions myLintOptions;
    @NonNull private final Collection<String> myUnresolvedDependencies;
    @NonNull private final JavaCompileOptions myJavaCompileOptions;
    @NonNull private final AaptOptions myAaptOptions;
    @NonNull private final File myBuildFolder;
    @NonNull private final Collection<String> myDynamicFeatures;
    @Nullable private final ViewBindingOptions myViewBindingOptions;
    @Nullable private final GradleVersion myParsedModelVersion;
    @Nullable private final String myBuildToolsVersion;
    @Nullable private final String myResourcePrefix;
    @Nullable private final String myGroupId;
    private final boolean mySupportsPluginGeneration;
    private final int myApiVersion;
    private final int myProjectType;
    private final boolean myBaseSplit;
    private final int myHashCode;

    public static IdeAndroidProjectImpl create(
            @NonNull AndroidProject project,
            @NonNull IdeDependenciesFactory dependenciesFactory,
            @Nullable Collection<Variant> variants,
            @Nullable ProjectSyncIssues syncIssues) {
        return create(project, new ModelCache(), dependenciesFactory, variants, syncIssues);
    }

    @VisibleForTesting
    public static IdeAndroidProjectImpl create(
            @NonNull AndroidProject project,
            @NonNull ModelCache modelCache,
            @NonNull IdeDependenciesFactory dependenciesFactory,
            @Nullable Collection<Variant> variants,
            @Nullable ProjectSyncIssues syncIssues) {
        // Old plugin versions do not return model version.
        GradleVersion parsedModelVersion = GradleVersion.tryParse(project.getModelVersion());

        ProductFlavorContainer defaultConfigCopy =
                modelCache.computeIfAbsent(
                        project.getDefaultConfig(),
                        container -> new IdeProductFlavorContainer(container, modelCache));

        Collection<BuildTypeContainer> buildTypesCopy =
                IdeModel.copy(
                        project.getBuildTypes(),
                        modelCache,
                        container -> new IdeBuildTypeContainer(container, modelCache));

        Collection<ProductFlavorContainer> productFlavorCopy =
                IdeModel.copy(
                        project.getProductFlavors(),
                        modelCache,
                        container -> new IdeProductFlavorContainer(container, modelCache));

        // If we have a ProjectSyncIssues model then use the sync issues contained in that, otherwise fallback to the
        // SyncIssues that are stored within the AndroidProject. This is needed to support plugins < 3.6 which do not produce a
        // ProjectSyncIssues model.
        //noinspection deprecation
        Collection<SyncIssue> syncIssuesCopy =
                new ArrayList<>(
                        IdeModel.copy(
                                (syncIssues == null)
                                        ? project.getSyncIssues()
                                        : syncIssues.getSyncIssues(),
                                modelCache,
                                issue -> new IdeSyncIssue(issue)));

        Collection<Variant> variantsCopy =
                new ArrayList<>(
                        IdeModel.copy(
                                (variants == null) ? project.getVariants() : variants,
                                modelCache,
                                variant ->
                                        new IdeVariantImpl(
                                                variant,
                                                modelCache,
                                                dependenciesFactory,
                                                parsedModelVersion)));

        Collection<String> variantNamesCopy =
                Objects.requireNonNull(
                        IdeModel.copyNewPropertyWithDefault(
                                () -> ImmutableList.copyOf(project.getVariantNames()),
                                () -> computeVariantNames(variantsCopy)));

        String defaultVariantCopy =
                IdeModel.copyNewPropertyWithDefault(
                        project::getDefaultVariant, () -> getDefaultVariant(variantNamesCopy));

        Collection<String> flavorDimensionCopy =
                IdeModel.copyNewPropertyNonNull(
                        () -> ImmutableList.copyOf(project.getFlavorDimensions()),
                        Collections.emptyList());

        Collection<String> bootClasspathCopy = ImmutableList.copyOf(project.getBootClasspath());

        Collection<NativeToolchain> nativeToolchainsCopy =
                IdeModel.copy(
                        project.getNativeToolchains(),
                        modelCache,
                        toolChain -> new IdeNativeToolchain(toolChain));

        Collection<SigningConfig> signingConfigsCopy =
                IdeModel.copy(
                        project.getSigningConfigs(),
                        modelCache,
                        config -> new IdeSigningConfig(config));

        IdeLintOptions lintOptionsCopy =
                modelCache.computeIfAbsent(
                        project.getLintOptions(),
                        options -> new IdeLintOptions(options, parsedModelVersion));

        // We need to use the unresolved dependencies to support older versions of the Android Gradle Plugin.
        //noinspection deprecation
        Collection<String> unresolvedDependenciesCopy =
                ImmutableSet.copyOf(project.getUnresolvedDependencies());

        IdeJavaCompileOptions javaCompileOptionsCopy =
                modelCache.computeIfAbsent(
                        project.getJavaCompileOptions(),
                        options -> new IdeJavaCompileOptions(options));

        IdeAaptOptions aaptOptionsCopy =
                modelCache.computeIfAbsent(
                        project.getAaptOptions(), options -> new IdeAaptOptions(options));

        Collection<String> dynamicFeaturesCopy =
                ImmutableList.copyOf(
                        IdeModel.copyNewPropertyNonNull(
                                project::getDynamicFeatures, ImmutableList.of()));

        IdeViewBindingOptions viewBindingOptionsCopy =
                IdeModel.copyNewProperty(
                        () -> new IdeViewBindingOptions(project.getViewBindingOptions()), null);

        String buildToolsVersionCopy =
                IdeModel.copyNewProperty(project::getBuildToolsVersion, null);

        String groupId = null;
        if (parsedModelVersion != null
                && parsedModelVersion.isAtLeast(3, 6, 0, "alpha", 5, false)) {
            groupId = project.getGroupId();
        }

        // AndroidProject#isBaseSplit is always non null.
        //noinspection ConstantConditions
        boolean isBaseSplit = IdeModel.copyNewProperty(project::isBaseSplit, false);

        return new IdeAndroidProjectImpl(
                project.getModelVersion(),
                parsedModelVersion,
                project.getName(),
                defaultConfigCopy,
                buildTypesCopy,
                productFlavorCopy,
                syncIssuesCopy,
                variantsCopy,
                variantNamesCopy,
                defaultVariantCopy,
                flavorDimensionCopy,
                project.getCompileTarget(),
                bootClasspathCopy,
                nativeToolchainsCopy,
                signingConfigsCopy,
                lintOptionsCopy,
                unresolvedDependenciesCopy,
                javaCompileOptionsCopy,
                aaptOptionsCopy,
                project.getBuildFolder(),
                dynamicFeaturesCopy,
                viewBindingOptionsCopy,
                buildToolsVersionCopy,
                project.getResourcePrefix(),
                groupId,
                IdeModel.copyNewProperty(project::getPluginGeneration, null) != null,
                project.getApiVersion(),
                getProjectType(project, parsedModelVersion),
                isBaseSplit);
    }

    private IdeAndroidProjectImpl(
            @NonNull String modelVersion,
            @Nullable GradleVersion parsedModelVersion,
            @NonNull String name,
            @NonNull ProductFlavorContainer defaultConfig,
            @NonNull Collection<BuildTypeContainer> buildTypes,
            @NonNull Collection<ProductFlavorContainer> productFlavors,
            @NonNull Collection<SyncIssue> syncIssues,
            @NonNull Collection<Variant> variants,
            @NonNull Collection<String> variantNames,
            @Nullable String defaultVariant,
            @NonNull Collection<String> flavorDimensions,
            @NonNull String compileTarget,
            @NonNull Collection<String> bootClassPath,
            @NonNull Collection<NativeToolchain> nativeToolchains,
            @NonNull Collection<SigningConfig> signingConfigs,
            @NonNull IdeLintOptions lintOptions,
            @NonNull Collection<String> unresolvedDependencies,
            @NonNull JavaCompileOptions javaCompileOptions,
            @NonNull AaptOptions aaptOptions,
            @NonNull File buildFolder,
            @NonNull Collection<String> dynamicFeatures,
            @Nullable ViewBindingOptions viewBindingOptions,
            @Nullable String buildToolsVersion,
            @Nullable String resourcePrefix,
            @Nullable String groupId,
            boolean supportsPluginGeneration,
            int apiVersion,
            int projectType,
            boolean baseSplit) {
        myModelVersion = modelVersion;
        myParsedModelVersion = parsedModelVersion;
        myName = name;
        myDefaultConfig = defaultConfig;
        myBuildTypes = buildTypes;
        myProductFlavors = productFlavors;
        mySyncIssues = syncIssues;
        myVariants = variants;
        myVariantNames = variantNames;
        myDefaultVariant = defaultVariant;
        myFlavorDimensions = flavorDimensions;
        myCompileTarget = compileTarget;
        myBootClassPath = bootClassPath;
        myNativeToolchains = nativeToolchains;
        mySigningConfigs = signingConfigs;
        myLintOptions = lintOptions;
        myUnresolvedDependencies = unresolvedDependencies;
        myJavaCompileOptions = javaCompileOptions;
        myAaptOptions = aaptOptions;
        myBuildFolder = buildFolder;
        myDynamicFeatures = dynamicFeatures;
        myViewBindingOptions = viewBindingOptions;
        myBuildToolsVersion = buildToolsVersion;
        myResourcePrefix = resourcePrefix;
        myGroupId = groupId;
        mySupportsPluginGeneration = supportsPluginGeneration;
        myApiVersion = apiVersion;
        myProjectType = projectType;
        myBaseSplit = baseSplit;

        myHashCode = calculateHashCode();
    }

    @NonNull
    private static ImmutableList<String> computeVariantNames(Collection<Variant> variants) {
        return variants.stream().map(Variant::getName).collect(ImmutableList.toImmutableList());
    }

    private static int getProjectType(
            @NonNull AndroidProject project, @Nullable GradleVersion modelVersion) {
        if (modelVersion != null && modelVersion.isAtLeast(2, 3, 0)) {
            return project.getProjectType();
        }
        // Support for old Android Gradle Plugins must be maintained.
        //noinspection deprecation
        return project.isLibrary() ? PROJECT_TYPE_LIBRARY : PROJECT_TYPE_APP;
    }

    /** For older AGP versions pick a variant name based on a heuristic */
    @VisibleForTesting
    @Nullable
    static String getDefaultVariant(Collection<String> variantNames) {
        // Corner case of variant filter accidentally removing all variants.
        if (variantNames.isEmpty()) {
            return null;
        }

        // Favor the debug variant
        if (variantNames.contains("debug")) {
            return "debug";
        }
        // Otherwise the first alphabetically that has debug as a build type.
        ImmutableSortedSet<String> sortedNames = ImmutableSortedSet.copyOf(variantNames);
        for (String variantName : sortedNames) {
            if (variantName.endsWith("Debug")) {
                return variantName;
            }
        }
        // Otherwise fall back to the first alphabetically
        return sortedNames.first();
    }

    @Override
    @Nullable
    public GradleVersion getParsedModelVersion() {
        return myParsedModelVersion;
    }

    @Override
    @NonNull
    public String getModelVersion() {
        return myModelVersion;
    }

    @Override
    @NonNull
    public String getName() {
        return myName;
    }

    @Override
    @NonNull
    public ProductFlavorContainer getDefaultConfig() {
        return myDefaultConfig;
    }

    @Override
    @NonNull
    public Collection<BuildTypeContainer> getBuildTypes() {
        return myBuildTypes;
    }

    @Override
    @NonNull
    public Collection<ProductFlavorContainer> getProductFlavors() {
        return myProductFlavors;
    }

    @Override
    @NonNull
    public String getBuildToolsVersion() {
        if (myBuildToolsVersion != null) {
            return myBuildToolsVersion;
        }
        throw new UnsupportedOperationException(
                "Unsupported method: AndroidProject.getBuildToolsVersion()");
    }

    @Override
    @NonNull
    public Collection<SyncIssue> getSyncIssues() {
        return ImmutableList.copyOf(mySyncIssues);
    }

    @Override
    @NonNull
    public Collection<Variant> getVariants() {
        return ImmutableList.copyOf(myVariants);
    }

    @Override
    @NonNull
    public Collection<String> getVariantNames() {
        return myVariantNames;
    }

    @Nullable
    @Override
    public String getDefaultVariant() {
        return myDefaultVariant;
    }

    @Override
    @NonNull
    public Collection<String> getFlavorDimensions() {
        return myFlavorDimensions;
    }

    @Override
    @NonNull
    public String getCompileTarget() {
        return myCompileTarget;
    }

    @Override
    @NonNull
    public Collection<String> getBootClasspath() {
        return myBootClassPath;
    }

    @Override
    @NonNull
    public AaptOptions getAaptOptions() {
        return myAaptOptions;
    }

    @Override
    @NonNull
    public Collection<SigningConfig> getSigningConfigs() {
        return mySigningConfigs;
    }

    @Override
    @NonNull
    public IdeLintOptions getLintOptions() {
        return myLintOptions;
    }

    @Deprecated
    @Override
    @NonNull
    public Collection<String> getUnresolvedDependencies() {
        return myUnresolvedDependencies;
    }

    @Override
    @NonNull
    public JavaCompileOptions getJavaCompileOptions() {
        return myJavaCompileOptions;
    }

    @Override
    @NonNull
    public File getBuildFolder() {
        return myBuildFolder;
    }

    @Override
    @Nullable
    public String getResourcePrefix() {
        return myResourcePrefix;
    }

    @Override
    public int getApiVersion() {
        return myApiVersion;
    }

    @Override
    public int getProjectType() {
        return myProjectType;
    }

    @Override
    public boolean isBaseSplit() {
        return myBaseSplit;
    }

    @NonNull
    @Override
    public Collection<String> getDynamicFeatures() {
        return myDynamicFeatures;
    }

    @Nullable
    @Override
    public ViewBindingOptions getViewBindingOptions() {
        return myViewBindingOptions;
    }

    @Nullable
    @Override
    public String getGroupId() {
        return myGroupId;
    }

    @Override
    public void forEachVariant(@NonNull Consumer<IdeVariant> action) {
        for (Variant variant : myVariants) {
            action.accept((IdeVariant) variant);
        }
    }

    @Override
    public void addVariants(
            @NonNull Collection<Variant> variants, @NonNull IdeDependenciesFactory factory) {
        ModelCache modelCache = new ModelCache();
        for (Variant variant : variants) {
            myVariants.add(new IdeVariantImpl(variant, modelCache, factory, myParsedModelVersion));
        }
    }

    @Override
    public void addSyncIssues(@NonNull Collection<SyncIssue> syncIssues) {
        Set<SyncIssue> currentSyncIssues = new HashSet<>(mySyncIssues);
        for (SyncIssue issue : syncIssues) {
            // Only add the sync issues that are not seen from previous sync.
            IdeSyncIssue newSyncIssue = new IdeSyncIssue(issue);
            if (!currentSyncIssues.contains(newSyncIssue)) {
                mySyncIssues.add(newSyncIssue);
            }
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof IdeAndroidProjectImpl)) {
            return false;
        }
        IdeAndroidProjectImpl project = (IdeAndroidProjectImpl) o;
        return myApiVersion == project.myApiVersion
                && myProjectType == project.myProjectType
                && myBaseSplit == project.myBaseSplit
                && mySupportsPluginGeneration == project.mySupportsPluginGeneration
                && Objects.equals(myModelVersion, project.myModelVersion)
                && Objects.equals(myParsedModelVersion, project.myParsedModelVersion)
                && Objects.equals(myName, project.myName)
                && Objects.equals(myDefaultConfig, project.myDefaultConfig)
                && Objects.equals(myBuildTypes, project.myBuildTypes)
                && Objects.equals(myProductFlavors, project.myProductFlavors)
                && Objects.equals(myBuildToolsVersion, project.myBuildToolsVersion)
                && Objects.equals(mySyncIssues, project.mySyncIssues)
                && Objects.equals(myVariants, project.myVariants)
                && Objects.equals(myVariantNames, project.myVariantNames)
                && Objects.equals(myDefaultVariant, project.myDefaultVariant)
                && Objects.equals(myFlavorDimensions, project.myFlavorDimensions)
                && Objects.equals(myCompileTarget, project.myCompileTarget)
                && Objects.equals(myBootClassPath, project.myBootClassPath)
                && Objects.equals(myNativeToolchains, project.myNativeToolchains)
                && Objects.equals(mySigningConfigs, project.mySigningConfigs)
                && Objects.equals(myLintOptions, project.myLintOptions)
                && Objects.equals(myUnresolvedDependencies, project.myUnresolvedDependencies)
                && Objects.equals(myJavaCompileOptions, project.myJavaCompileOptions)
                && Objects.equals(myAaptOptions, project.myAaptOptions)
                && Objects.equals(myBuildFolder, project.myBuildFolder)
                && Objects.equals(myResourcePrefix, project.myResourcePrefix)
                && Objects.equals(myDynamicFeatures, project.myDynamicFeatures)
                && Objects.equals(myViewBindingOptions, project.myViewBindingOptions)
                && Objects.equals(myGroupId, project.myGroupId);
    }

    @Override
    public int hashCode() {
        return myHashCode;
    }

    private int calculateHashCode() {
        return Objects.hash(
                myModelVersion,
                myParsedModelVersion,
                myName,
                myDefaultConfig,
                myBuildTypes,
                myProductFlavors,
                myBuildToolsVersion,
                mySyncIssues,
                myVariants,
                myVariantNames,
                myDefaultVariant,
                myFlavorDimensions,
                myCompileTarget,
                myBootClassPath,
                myNativeToolchains,
                mySigningConfigs,
                myLintOptions,
                myUnresolvedDependencies,
                myJavaCompileOptions,
                myBuildFolder,
                myResourcePrefix,
                myApiVersion,
                myProjectType,
                mySupportsPluginGeneration,
                myAaptOptions,
                myBaseSplit,
                myDynamicFeatures,
                myViewBindingOptions,
                myGroupId);
    }

    @Override
    public String toString() {
        return "IdeAndroidProject{"
                + "myModelVersion='"
                + myModelVersion
                + '\''
                + ", myName='"
                + myName
                + '\''
                + ", myDefaultConfig="
                + myDefaultConfig
                + ", myBuildTypes="
                + myBuildTypes
                + ", myProductFlavors="
                + myProductFlavors
                + ", myBuildToolsVersion='"
                + myBuildToolsVersion
                + '\''
                + ", mySyncIssues="
                + mySyncIssues
                + ", myVariants="
                + myVariants
                + ", myVariantNames="
                + myVariantNames
                + ", myDefaultVariant="
                + myDefaultVariant
                + ", myFlavorDimensions="
                + myFlavorDimensions
                + ", myCompileTarget='"
                + myCompileTarget
                + '\''
                + ", myBootClassPath="
                + myBootClassPath
                + ", myNativeToolchains="
                + myNativeToolchains
                + ", mySigningConfigs="
                + mySigningConfigs
                + ", myLintOptions="
                + myLintOptions
                + ", myUnresolvedDependencies="
                + myUnresolvedDependencies
                + ", myJavaCompileOptions="
                + myJavaCompileOptions
                + ", myBuildFolder="
                + myBuildFolder
                + ", myResourcePrefix='"
                + myResourcePrefix
                + '\''
                + ", myApiVersion="
                + myApiVersion
                + ", myProjectType="
                + myProjectType
                + ", mySupportsPluginGeneration="
                + mySupportsPluginGeneration
                + ", myAaptOptions="
                + myAaptOptions
                + ", myBaseSplit="
                + myBaseSplit
                + ", myDynamicFeatures="
                + myDynamicFeatures
                + ", myViewBindingOptions="
                + myViewBindingOptions
                + ", myGroupId="
                + myGroupId
                + "}";
    }
}
