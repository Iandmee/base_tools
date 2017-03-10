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
import com.android.build.FilterData;
import com.android.build.OutputFile;
import com.android.build.VariantOutput;
import com.android.build.gradle.api.ApkOutputFile;
import com.android.build.gradle.tasks.PackageAndroidArtifact;
import com.android.build.gradle.tasks.ProcessAndroidResources;
import com.android.ide.common.build.ApkData;
import com.android.utils.StringHelper;
import com.google.common.collect.ImmutableList;
import java.io.File;
import java.util.Collection;
import java.util.stream.Collectors;
import org.gradle.api.Task;


/**
 * Base output data about a variant.
 */
public abstract class BaseVariantOutputData implements VariantOutput {

    private static final String UNIVERSAL = "universal";

    @NonNull public final BaseVariantData variantData;

    @NonNull
    private final ApkOutputFile mainApkOutputFile;

    private boolean multiOutput = false;

    public ProcessAndroidResources processResourcesTask;

    public PackageAndroidArtifact packageAndroidArtifactTask;

    public Task assembleTask;

    public BaseVariantOutputData(
            @NonNull OutputFile.OutputType outputType,
            @NonNull Collection<FilterData> filters,
            @NonNull BaseVariantData variantData) {
        this.variantData = variantData;
        ApkData mainApkData = variantData.getSplitScope().getMainSplit();
        this.mainApkOutputFile =
                new ApkOutputFile(
                        outputType,
                        filters,
                        this::getFile,
                        mainApkData != null ? mainApkData.getVersionCode() : -1);
    }

    @NonNull
    @Override
    public Collection<FilterData> getFilters() {
        return mainApkOutputFile.getFilters();
    }

    @Nullable
    public String getFilter(String filterType) {
        return ApkData.getFilter(mainApkOutputFile.getFilters(), FilterType.valueOf(filterType));
    }

    @NonNull
    @Override
    public Collection<String> getFilterTypes() {
        return mainApkOutputFile
                .getFilters()
                .stream()
                .map(FilterData::getFilterType)
                .collect(Collectors.toList());
    }

    private File getFile() {
        return mainApkOutputFile.getOutputFile();
    }

    @NonNull
    @Override
    public String getOutputType() {
        return mainApkOutputFile.getType().name();
    }

    @NonNull
    @Override
    public ApkOutputFile getMainOutputFile() {
        return mainApkOutputFile;
    }

    @NonNull
    @Override
    public abstract ImmutableList<ApkOutputFile> getOutputs();

    @NonNull
    public String getFullName() {
        if (!multiOutput) {
            return variantData.getVariantConfiguration().getFullName();
        }
        return variantData.getVariantConfiguration().computeFullNameWithSplits(getFilterName());
    }

    @NonNull
    public String getBaseName() {
        if (!multiOutput) {
            return variantData.getVariantConfiguration().getBaseName();
        }
        return variantData.getVariantConfiguration().computeBaseNameWithSplits(getFilterName());
    }

    @NonNull
    public String getDirName() {
        if (!multiOutput) {
            return variantData.getVariantConfiguration().getDirName();
        }
        return variantData.getVariantConfiguration().computeDirNameWithSplits(
                mainApkOutputFile.getFilter(OutputFile.DENSITY),
                mainApkOutputFile.getFilter(OutputFile.ABI));
    }

    @NonNull
    private String getFilterName() {
        if (mainApkOutputFile.getFilters().isEmpty()) {
            return UNIVERSAL;
        }

        StringBuilder sb = new StringBuilder();
        String densityFilter = mainApkOutputFile.getFilter(OutputFile.DENSITY);
        if (densityFilter != null) {
            sb.append(densityFilter);
        }
        String abiFilter = mainApkOutputFile.getFilter(OutputFile.ABI);
        if (abiFilter != null) {
            if (sb.length() > 0) {
                sb.append(StringHelper.capitalize(abiFilter));
            } else {
                sb.append(abiFilter);
            }
        }

        return sb.toString();
    }

}