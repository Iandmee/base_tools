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
import com.android.build.FilterData;
import com.android.build.OutputFile;
import com.android.build.gradle.BasePlugin;
import com.android.build.gradle.api.BaseVariant;
import com.android.build.gradle.api.BaseVariantOutput;
import com.android.build.gradle.internal.VariantModel;
import com.android.build.gradle.internal.api.ApkVariantImpl;
import com.android.build.gradle.internal.api.ApkVariantOutputImpl;
import com.android.build.gradle.internal.api.ApplicationVariantImpl;
import com.android.build.gradle.internal.api.ReadOnlyObjectProvider;
import com.android.build.gradle.internal.core.GradleVariantConfiguration;
import com.android.build.gradle.internal.model.FilterDataImpl;
import com.android.builder.core.VariantType;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 */
public class ApplicationVariantFactory implements VariantFactory<ApplicationVariantData> {

    @NonNull
    private final BasePlugin basePlugin;

    public ApplicationVariantFactory(@NonNull BasePlugin basePlugin) {
        this.basePlugin = basePlugin;
    }

    @Override
    @NonNull
    public ApplicationVariantData createVariantData(
            @NonNull GradleVariantConfiguration variantConfiguration,
            @NonNull Set<String> densities,
            @NonNull Set<String> abis,
            @NonNull Set<String> compatibleScreens) {
        ApplicationVariantData variant = new ApplicationVariantData(basePlugin, variantConfiguration);

        if (!densities.isEmpty()) {
            variant.setCompatibleScreens(compatibleScreens);
        }

        // create its outputs
        if (variant.getSplitHandlingPolicy() ==
                BaseVariantData.SplitHandlingPolicy.PRE_21_POLICY) {
            // create its outputs
            for (String density : densities) {
                for (String abi : abis) {
                    ImmutableList.Builder<FilterData> builder = ImmutableList.builder();
                    if (density != null) {
                        builder.add(FilterDataImpl.Builder.build(OutputFile.DENSITY, density));
                    }
                    if (abi != null) {
                        builder.add(FilterDataImpl.Builder.build(OutputFile.ABI, abi));
                    }
                    variant.createOutput(
                            OutputFile.OutputType.FULL_SPLIT,
                            builder.build());
                }
            }
        } else {
            variant.createOutput(OutputFile.OutputType.MAIN,
                    Collections.<FilterData>emptyList());
        }

        return variant;
    }

    @Override
    @NonNull
    public BaseVariant createVariantApi(
            @NonNull BaseVariantData<? extends BaseVariantOutputData> variantData,
            @NonNull ReadOnlyObjectProvider readOnlyObjectProvider) {
        // create the base variant object.
        ApplicationVariantImpl variant = basePlugin.getInstantiator().newInstance(
                ApplicationVariantImpl.class, variantData, basePlugin, readOnlyObjectProvider);

        // now create the output objects
        createApkOutputApiObjects(basePlugin, variantData, variant);

        return variant;
    }

    public static void createApkOutputApiObjects(
            @NonNull BasePlugin basePlugin,
            @NonNull BaseVariantData<? extends BaseVariantOutputData> variantData,
            @NonNull ApkVariantImpl variant) {
        List<? extends BaseVariantOutputData> outputList = variantData.getOutputs();
        List<BaseVariantOutput> apiOutputList = Lists.newArrayListWithCapacity(outputList.size());

        for (BaseVariantOutputData variantOutputData : outputList) {
            ApkVariantOutputData apkOutput = (ApkVariantOutputData) variantOutputData;

            ApkVariantOutputImpl output = basePlugin.getInstantiator().newInstance(
                    ApkVariantOutputImpl.class, apkOutput);

            apiOutputList.add(output);
        }

        variant.addOutputs(apiOutputList);
    }

    @NonNull
    @Override
    public VariantType getVariantConfigurationType() {
        return VariantType.DEFAULT;
    }

    @Override
    public boolean isLibrary() {
        return false;
    }

    @Override
    public void validateModel(@NonNull VariantModel model){
        // No additional checks for ApplicationVariantFactory, so just return.
    }
}
