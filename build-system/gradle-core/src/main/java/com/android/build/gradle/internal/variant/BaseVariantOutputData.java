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

import static com.android.SdkConstants.DOT_RES;
import static com.android.SdkConstants.FD_RES;
import static com.android.SdkConstants.FN_RES_BASE;
import static com.android.SdkConstants.RES_QUALIFIER_SEP;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.FilterData;
import com.android.build.OutputFile;
import com.android.build.VariantOutput;
import com.android.build.gradle.api.ApkOutputFile;
import com.android.build.gradle.internal.scope.VariantOutputScope;
import com.android.build.gradle.tasks.BundleAtom;
import com.android.build.gradle.tasks.GenerateAtomMetadata;
import com.android.build.gradle.tasks.ManifestProcessorTask;
import com.android.build.gradle.tasks.PackageAndroidArtifact;
import com.android.build.gradle.tasks.PackageSplitAbi;
import com.android.build.gradle.tasks.PackageSplitRes;
import com.android.build.gradle.tasks.ProcessAndroidResources;
import com.android.builder.model.AndroidAtom;
import com.android.utils.FileUtils;
import com.android.utils.StringHelper;
import com.google.common.collect.ImmutableList;

import org.gradle.api.Task;

import java.io.File;
import java.util.Collection;

/**
 * Base output data about a variant.
 */
public abstract class BaseVariantOutputData implements VariantOutput {

    private static final String UNIVERSAL = "universal";

    @NonNull
    public final BaseVariantData<?> variantData;

    @NonNull
    private final ApkOutputFile mainApkOutputFile;

    private boolean multiOutput = false;

    public ManifestProcessorTask manifestProcessorTask;

    public ProcessAndroidResources processResourcesTask;

    public PackageSplitRes packageSplitResourcesTask;

    public PackageSplitAbi packageSplitAbiTask;

    public PackageAndroidArtifact packageAndroidArtifactTask;

    public GenerateAtomMetadata generateAtomMetadataTask;

    public BundleAtom bundleAtomTask;

    public Task assembleTask;

    @NonNull
    private final VariantOutputScope scope;

    public BaseVariantOutputData(
            @NonNull OutputFile.OutputType outputType,
            @NonNull Collection<FilterData> filters,
            @NonNull BaseVariantData<?> variantData) {
        this.variantData = variantData;
        this.mainApkOutputFile = new ApkOutputFile(outputType, filters, this::getOutputFile);
        scope = new VariantOutputScope(variantData.getScope(), this);
    }

    @NonNull
    @Override
    public ApkOutputFile getMainOutputFile() {
        return mainApkOutputFile;
    }


    public abstract void setOutputFile(@NonNull File file);

    @Nullable
    public abstract File getOutputFile();

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

    @NonNull
    @Override
    public File getSplitFolder() {
        return getOutputFile().getParentFile();
    }

    void setMultiOutput(boolean multiOutput) {
        this.multiOutput = multiOutput;
    }

    @Nullable
    public File getAtomMetadataBaseFolder() {
        if (generateAtomMetadataTask == null)
            return null;
        else
            return generateAtomMetadataTask.getAtomMetadataFolder();
    }

    @NonNull
    public File getProcessResourcePackageOutputFile() {
        return FileUtils.join(getScope().getGlobalScope().getIntermediatesDir(),
                FD_RES, FN_RES_BASE + RES_QUALIFIER_SEP + getBaseName() + DOT_RES);

    }

    @NonNull
    public File getProcessResourcePackageOutputFile(AndroidAtom androidAtom) {
        return FileUtils.join(getScope().getGlobalScope().getIntermediatesDir(),
                FD_RES, FN_RES_BASE
                        + RES_QUALIFIER_SEP
                        + androidAtom.getAtomName()
                        + RES_QUALIFIER_SEP
                        + getBaseName()
                        + DOT_RES);
    }

    @NonNull
    public VariantOutputScope getScope() {
        return scope;
    }
}