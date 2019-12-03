/*
 * Copyright (C) 2013 The Android Open Source Project
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
import com.android.build.gradle.internal.TaskManager;
import com.android.build.gradle.internal.core.VariantDslInfo;
import com.android.build.gradle.internal.core.VariantDslInfoImpl;
import com.android.build.gradle.internal.core.VariantSources;
import com.android.build.gradle.internal.scope.GlobalScope;
import com.android.builder.profile.Recorder;
import com.android.utils.StringHelper;

/** Base data about a variant that generates an APK file. */
public abstract class ApkVariantData extends InstallableVariantData {

    protected ApkVariantData(
            @NonNull GlobalScope globalScope,
            @NonNull TaskManager taskManager,
            @NonNull VariantDslInfoImpl variantDslInfo,
            @NonNull VariantSources variantSources,
            @NonNull Recorder recorder) {
        super(globalScope, taskManager, variantDslInfo, variantSources, recorder);
    }

    @Override
    @NonNull
    public String getDescription() {
        final VariantDslInfo variantDslInfo = getVariantDslInfo();

        if (variantDslInfo.hasFlavors()) {
            StringBuilder sb = new StringBuilder(50);
            StringHelper.appendCapitalized(sb, variantDslInfo.getBuildType());
            StringHelper.appendCapitalized(sb, variantDslInfo.getFlavorName());
            sb.append(" build");
            return sb.toString();
        } else {
            return StringHelper.capitalizeAndAppend(variantDslInfo.getBuildType(), " build");
        }
    }
}
