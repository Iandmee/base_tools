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

package com.android.build.gradle.internal;

import android.databinding.tool.DataBindingBuilder;
import com.android.annotations.NonNull;
import com.android.build.api.transform.QualifiedContent;
import com.android.build.gradle.AndroidConfig;
import com.android.build.gradle.internal.scope.GlobalScope;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.options.ProjectOptions;
import com.android.build.gradle.tasks.ProcessAndroidResources;
import com.android.builder.core.AndroidBuilder;
import com.android.builder.core.VariantType;
import com.android.builder.profile.Recorder;
import com.google.common.collect.ImmutableMap;
import java.io.File;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import org.gradle.api.Project;
import org.gradle.tooling.provider.model.ToolingModelBuilderRegistry;

/** TaskManager for creating tasks in an Android feature project. */
public class MultiTypeTaskManager extends TaskManager {

    @NonNull Map<VariantType, TaskManager> delegates;

    public MultiTypeTaskManager(
            @NonNull GlobalScope globalScope,
            @NonNull Project project,
            @NonNull ProjectOptions projectOptions,
            @NonNull AndroidBuilder androidBuilder,
            @NonNull DataBindingBuilder dataBindingBuilder,
            @NonNull AndroidConfig extension,
            @NonNull SdkHandler sdkHandler,
            @NonNull DependencyManager dependencyManager,
            @NonNull ToolingModelBuilderRegistry toolingRegistry,
            @NonNull Recorder recorder) {
        super(
                globalScope,
                project,
                projectOptions,
                androidBuilder,
                dataBindingBuilder,
                extension,
                sdkHandler,
                dependencyManager,
                toolingRegistry,
                recorder);
        delegates =
                ImmutableMap.of(
                        VariantType.FEATURE,
                        new FeatureTaskManager(
                                globalScope,
                                project,
                                projectOptions,
                                androidBuilder,
                                dataBindingBuilder,
                                extension,
                                sdkHandler,
                                dependencyManager,
                                toolingRegistry,
                                recorder),
                        VariantType.LIBRARY,
                        new LibraryTaskManager(
                                globalScope,
                                project,
                                projectOptions,
                                androidBuilder,
                                dataBindingBuilder,
                                extension,
                                sdkHandler,
                                dependencyManager,
                                toolingRegistry,
                                recorder));
    }

    @Override
    public void createTasksForVariantScope(
            @NonNull TaskFactory tasks, @NonNull VariantScope variantScope) {
        delegates
                .get(variantScope.getVariantData().getType())
                .createTasksForVariantScope(tasks, variantScope);
    }

    @Override
    protected ProcessAndroidResources.ConfigAction createProcessAndroidResourcesConfigAction(
            @NonNull VariantScope scope,
            @NonNull Supplier<File> symbolLocation,
            @NonNull File resPackageOutputFolder,
            boolean useAaptToGenerateLegacyMultidexMainDexProguardRules,
            @NonNull MergeType mergeType,
            @NonNull String baseName) {
        TaskManager delegateTaskManager = delegates.get(scope.getVariantData().getType());
        return delegateTaskManager != null
                ? delegateTaskManager.createProcessAndroidResourcesConfigAction(
                        scope,
                        symbolLocation,
                        resPackageOutputFolder,
                        useAaptToGenerateLegacyMultidexMainDexProguardRules,
                        mergeType,
                        baseName)
                : super.createProcessAndroidResourcesConfigAction(
                        scope,
                        symbolLocation,
                        resPackageOutputFolder,
                        useAaptToGenerateLegacyMultidexMainDexProguardRules,
                        mergeType,
                        baseName);
    }

    @NonNull
    @Override
    protected Set<? super QualifiedContent.Scope> getResMergingScopes(
            @NonNull VariantScope variantScope) {
        VariantType variantType = variantScope.getVariantData().getType();
        if (variantType.isForTesting()) {
            variantType = variantScope.getTestedVariantData().getType();
        }
        return delegates.get(variantType).getResMergingScopes(variantScope);
    }
}
