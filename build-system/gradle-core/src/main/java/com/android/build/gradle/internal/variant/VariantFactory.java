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

package com.android.build.gradle.internal.variant;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.api.artifact.impl.ArtifactsImpl;
import com.android.build.api.component.ComponentIdentity;
import com.android.build.api.component.impl.AndroidTestBuilderImpl;
import com.android.build.api.component.impl.AndroidTestImpl;
import com.android.build.api.component.impl.ComponentImpl;
import com.android.build.api.component.impl.UnitTestBuilderImpl;
import com.android.build.api.component.impl.UnitTestImpl;
import com.android.build.api.dsl.BuildFeatures;
import com.android.build.api.variant.impl.VariantBuilderImpl;
import com.android.build.api.variant.impl.VariantImpl;
import com.android.build.gradle.internal.api.BaseVariantImpl;
import com.android.build.gradle.internal.api.ReadOnlyObjectProvider;
import com.android.build.gradle.internal.core.VariantDslInfo;
import com.android.build.gradle.internal.core.VariantSources;
import com.android.build.gradle.internal.dependency.VariantDependencies;
import com.android.build.gradle.internal.dsl.BuildType;
import com.android.build.gradle.internal.dsl.DataBindingOptions;
import com.android.build.gradle.internal.dsl.DefaultConfig;
import com.android.build.gradle.internal.dsl.ProductFlavor;
import com.android.build.gradle.internal.dsl.SigningConfig;
import com.android.build.gradle.internal.pipeline.TransformManager;
import com.android.build.gradle.internal.plugins.DslContainerProvider;
import com.android.build.gradle.internal.scope.BuildFeatureValues;
import com.android.build.gradle.internal.scope.GlobalScope;
import com.android.build.gradle.internal.scope.MutableTaskContainer;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.services.BaseServices;
import com.android.build.gradle.internal.services.TaskCreationServices;
import com.android.build.gradle.internal.services.VariantApiServices;
import com.android.build.gradle.internal.services.VariantPropertiesApiServices;
import com.android.build.gradle.options.ProjectOptions;
import com.android.builder.core.VariantType;
import org.gradle.api.Project;

/**
 * Interface for Variant Factory.
 *
 * <p>While VariantManager is the general variant management, implementation of this interface
 * provides variant type (app, lib) specific implementation.
 */
public interface VariantFactory<
        VariantBuilderT extends VariantBuilderImpl, VariantT extends VariantImpl> {

    @NonNull
    VariantBuilderT createVariantObject(
            @NonNull ComponentIdentity componentIdentity,
            @NonNull VariantDslInfo variantDslInfo,
            @NonNull VariantApiServices variantApiServices);

    @NonNull
    UnitTestBuilderImpl createUnitTestObject(
            @NonNull ComponentIdentity componentIdentity,
            @NonNull VariantDslInfo variantDslInfo,
            @NonNull VariantApiServices variantApiServices);

    @NonNull
    AndroidTestBuilderImpl createAndroidTestObject(
            @NonNull ComponentIdentity componentIdentity,
            @NonNull VariantDslInfo variantDslInfo,
            @NonNull VariantApiServices variantApiServices);

    @NonNull
    VariantT createVariantPropertiesObject(
            @NonNull VariantBuilderT variant,
            @NonNull ComponentIdentity componentIdentity,
            @NonNull BuildFeatureValues buildFeatures,
            @NonNull VariantDslInfo variantDslInfo,
            @NonNull VariantDependencies variantDependencies,
            @NonNull VariantSources variantSources,
            @NonNull VariantPathHelper paths,
            @NonNull ArtifactsImpl artifacts,
            @NonNull VariantScope variantScope,
            @NonNull BaseVariantData variantData,
            @NonNull TransformManager transformManager,
            @NonNull VariantPropertiesApiServices variantPropertiesApiServices,
            @NonNull TaskCreationServices taskCreationServices);

    @NonNull
    UnitTestImpl createUnitTestProperties(
            @NonNull UnitTestBuilderImpl componentIdentity,
            @NonNull BuildFeatureValues buildFeatures,
            @NonNull VariantDslInfo variantDslInfo,
            @NonNull VariantDependencies variantDependencies,
            @NonNull VariantSources variantSources,
            @NonNull VariantPathHelper paths,
            @NonNull ArtifactsImpl artifacts,
            @NonNull VariantScope variantScope,
            @NonNull TestVariantData variantData,
            @NonNull VariantImpl testedVariantProperties,
            @NonNull TransformManager transformManager,
            @NonNull VariantPropertiesApiServices variantPropertiesApiServices,
            @NonNull TaskCreationServices taskCreationServices);

    @NonNull
    AndroidTestImpl createAndroidTestProperties(
            @NonNull AndroidTestBuilderImpl componentIdentity,
            @NonNull BuildFeatureValues buildFeatures,
            @NonNull VariantDslInfo variantDslInfo,
            @NonNull VariantDependencies variantDependencies,
            @NonNull VariantSources variantSources,
            @NonNull VariantPathHelper paths,
            @NonNull ArtifactsImpl artifacts,
            @NonNull VariantScope variantScope,
            @NonNull TestVariantData variantData,
            @NonNull VariantImpl testedVariantProperties,
            @NonNull TransformManager transformManager,
            @NonNull VariantPropertiesApiServices variantPropertiesApiServices,
            @NonNull TaskCreationServices taskCreationServices);

    @NonNull
    BaseVariantData createVariantData(
            @NonNull ComponentIdentity componentIdentity,
            @NonNull VariantDslInfo variantDslInfo,
            @NonNull VariantDependencies variantDependencies,
            @NonNull VariantSources variantSources,
            @NonNull VariantPathHelper paths,
            @NonNull ArtifactsImpl artifacts,
            @NonNull VariantPropertiesApiServices services,
            @NonNull GlobalScope globalScope,
            @NonNull MutableTaskContainer taskContainer);

    @NonNull
    BuildFeatureValues createBuildFeatureValues(
            @NonNull BuildFeatures buildFeatures, @NonNull ProjectOptions projectOptions);

    @NonNull
    BuildFeatureValues createTestBuildFeatureValues(
            @NonNull BuildFeatures buildFeatures,
            @NonNull DataBindingOptions dataBindingOptions,
            @NonNull ProjectOptions projectOptions);

    @NonNull
    Class<? extends BaseVariantImpl> getVariantImplementationClass(
            @NonNull BaseVariantData variantData);

    @Nullable
    BaseVariantImpl createVariantApi(
            @NonNull GlobalScope globalScope,
            @NonNull ComponentImpl componentProperties,
            @NonNull BaseVariantData variantData,
            @NonNull ReadOnlyObjectProvider readOnlyObjectProvider);

    @NonNull
    BaseServices getServicesForOldVariantObjectsOnly();

    @NonNull
    VariantType getVariantType();

    /**
     * Fail if the model is configured incorrectly.
     *
     * @param model the non-null model to validate, as implemented by the VariantManager.
     * @throws org.gradle.api.GradleException when the model does not validate.
     */
    void validateModel(
            @NonNull
                    VariantInputModel<DefaultConfig, BuildType, ProductFlavor, SigningConfig>
                            model);

    void preVariantWork(Project project);

    void createDefaultComponents(
            @NonNull
                    DslContainerProvider<DefaultConfig, BuildType, ProductFlavor, SigningConfig>
                            dslContainers);
}
