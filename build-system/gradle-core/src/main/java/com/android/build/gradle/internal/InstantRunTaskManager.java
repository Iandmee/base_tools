/*
 * Copyright (C) 2016 The Android Open Source Project
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

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.api.transform.QualifiedContent;
import com.android.build.gradle.AndroidGradleOptions;
import com.android.build.gradle.internal.incremental.BuildInfoLoaderTask;
import com.android.build.gradle.internal.incremental.InstantRunBuildContext;
import com.android.build.gradle.internal.incremental.InstantRunVerifierStatus;
import com.android.build.gradle.internal.incremental.InstantRunWrapperTask;
import com.android.build.gradle.internal.pipeline.ExtendedContentType;
import com.android.build.gradle.internal.pipeline.OriginalStream;
import com.android.build.gradle.internal.pipeline.TransformManager;
import com.android.build.gradle.internal.pipeline.TransformTask;
import com.android.build.gradle.internal.scope.AndroidTask;
import com.android.build.gradle.internal.scope.AndroidTaskRegistry;
import com.android.build.gradle.internal.scope.InstantRunVariantScope;
import com.android.build.gradle.internal.scope.SupplierTask;
import com.android.build.gradle.internal.scope.TransformVariantScope;
import com.android.build.gradle.internal.transforms.InstantRunDex;
import com.android.build.gradle.internal.transforms.InstantRunSlicer;
import com.android.build.gradle.internal.transforms.InstantRunTransform;
import com.android.build.gradle.internal.transforms.InstantRunVerifierTransform;
import com.android.build.gradle.internal.transforms.NoChangesVerifierTransform;
import com.android.build.gradle.tasks.PreColdSwapTask;
import com.android.build.gradle.tasks.fd.FastDeployRuntimeExtractorTask;
import com.android.build.gradle.tasks.fd.GenerateInstantRunAppInfoTask;
import com.android.builder.core.DexByteCodeConverter;
import com.android.builder.core.DexOptions;
import com.android.builder.model.OptionalCompilationStep;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;

import org.gradle.api.Project;
import org.gradle.api.logging.Logger;

import java.io.File;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Task Manager for InstantRun related transforms configuration and tasks handling.
 */
public class InstantRunTaskManager {

    @Nullable private AndroidTask<TransformTask> verifierTask;
    @Nullable private AndroidTask<TransformTask> reloadDexTask;

    @NonNull
    private final Logger logger;

    @NonNull
    private final InstantRunVariantScope variantScope;

    @NonNull
    private final TransformManager transformManager;

    @NonNull
    private final AndroidTaskRegistry androidTasks;

    @NonNull
    private final TaskFactory tasks;

    public InstantRunTaskManager(@NonNull Logger logger,
            @NonNull InstantRunVariantScope instantRunVariantScope,
            @NonNull TransformManager transformManager,
            @NonNull AndroidTaskRegistry androidTasks,
            @NonNull TaskFactory tasks) {
        this.logger = logger;
        this.variantScope = instantRunVariantScope;
        this.transformManager = transformManager;
        this.androidTasks = androidTasks;
        this.tasks = tasks;
    }


    public AndroidTask<BuildInfoLoaderTask> createInstantRunAllTasks(
            DexOptions dexOptions,
            @NonNull Supplier<DexByteCodeConverter> dexByteCodeConverter,
            @Nullable AndroidTask<?> preTask,
            AndroidTask<?> anchorTask,
            Set<QualifiedContent.Scope> resMergingScopes,
            SupplierTask<File> instantRunMergedManifest,
            boolean addResourceVerifier) {

        TransformVariantScope transformVariantScope = variantScope.getTransformVariantScope();

        AndroidTask<BuildInfoLoaderTask> buildInfoLoaderTask = androidTasks.create(tasks,
                new BuildInfoLoaderTask.ConfigAction(variantScope, logger));

        // always run the verifier first, since if it detects incompatible changes, we
        // should skip bytecode enhancements of the changed classes.
        InstantRunVerifierTransform verifierTransform = new InstantRunVerifierTransform(variantScope);
        Optional<AndroidTask<TransformTask>> verifierTaskOptional =
                transformManager.addTransform(tasks, transformVariantScope, verifierTransform);
        verifierTask = verifierTaskOptional.orElse(null);
        verifierTaskOptional.ifPresent(t -> t.optionalDependsOn(tasks, preTask));

        NoChangesVerifierTransform javaResourcesVerifierTransform =
                new NoChangesVerifierTransform(
                        "javaResourcesVerifier",
                        variantScope,
                        ImmutableSet.of(
                                QualifiedContent.DefaultContentType.RESOURCES,
                                ExtendedContentType.NATIVE_LIBS),
                        resMergingScopes,
                        InstantRunVerifierStatus.JAVA_RESOURCES_CHANGED);

        Optional<AndroidTask<TransformTask>> javaResourcesVerifierTask =
                transformManager.addTransform(
                        tasks, transformVariantScope, javaResourcesVerifierTransform);
        javaResourcesVerifierTask.ifPresent(t -> t.optionalDependsOn(tasks, verifierTask));

        InstantRunTransform instantRunTransform = new InstantRunTransform(variantScope);
        Optional<AndroidTask<TransformTask>> instantRunTask =
                transformManager.addTransform(tasks, transformVariantScope, instantRunTransform);

        instantRunTask.ifPresent(t ->
                t.dependsOn(
                        tasks,
                        buildInfoLoaderTask,
                        verifierTask,
                        javaResourcesVerifierTask.orElse(null)));

        if (addResourceVerifier) {
            NoChangesVerifierTransform dependenciesVerifierTransform =
                    new NoChangesVerifierTransform(
                            "dependenciesVerifier",
                            variantScope,
                            ImmutableSet.of(QualifiedContent.DefaultContentType.CLASSES),
                            Sets.immutableEnumSet(
                                    QualifiedContent.Scope.PROJECT_LOCAL_DEPS,
                                    QualifiedContent.Scope.SUB_PROJECTS_LOCAL_DEPS,
                                    QualifiedContent.Scope.EXTERNAL_LIBRARIES),
                            InstantRunVerifierStatus.DEPENDENCY_CHANGED);
            Optional<AndroidTask<TransformTask>> dependenciesVerifierTask =
                    transformManager.addTransform(
                            tasks, transformVariantScope, dependenciesVerifierTransform);
            dependenciesVerifierTask.ifPresent(t -> t.optionalDependsOn(tasks, verifierTask));
            instantRunTask.ifPresent(t ->
                    t.optionalDependsOn(tasks, dependenciesVerifierTask.orElse(null)));
        }


        AndroidTask<FastDeployRuntimeExtractorTask> extractorTask = androidTasks.create(
                tasks, new FastDeployRuntimeExtractorTask.ConfigAction(variantScope));
        extractorTask.dependsOn(tasks, buildInfoLoaderTask);

        // also add a new stream for the extractor task output.
        transformManager.addStream(OriginalStream.builder()
                .addContentTypes(TransformManager.CONTENT_CLASS)
                .addScope(QualifiedContent.Scope.EXTERNAL_LIBRARIES)
                .setJar(variantScope.getIncrementalRuntimeSupportJar())
                .setDependency(extractorTask.get(tasks))
                .build());

        AndroidTask<GenerateInstantRunAppInfoTask> generateAppInfoAndroidTask = androidTasks
                .create(tasks, new GenerateInstantRunAppInfoTask.ConfigAction(
                        transformVariantScope, variantScope, instantRunMergedManifest));

        // create the AppInfo.class for this variant.
        // TODO: remove this get() as it forces task creation.
        GenerateInstantRunAppInfoTask generateInstantRunAppInfoTask =
                generateAppInfoAndroidTask.get(tasks);

        // also add a new stream for the injector task output.
        transformManager.addStream(OriginalStream.builder()
                .addContentTypes(TransformManager.CONTENT_CLASS)
                .addScope(QualifiedContent.Scope.EXTERNAL_LIBRARIES)
                .setJar(generateInstantRunAppInfoTask.getOutputFile())
                .setDependency(generateInstantRunAppInfoTask)
                .build());

        // add a dependency on the manifest merger for the appInfo generation as it uses its output
        // the manifest merger task may be null depending if an external build system or Gradle
        // is used.
        generateAppInfoAndroidTask.optionalDependsOn(
                tasks, instantRunMergedManifest.getBuilderTask());

        anchorTask.optionalDependsOn(tasks, instantRunTask.orElse(null));

        // we always produce the reload.dex irrespective of the targeted version,
        // and if we are not in incremental mode, we need to still need to clean our output state.
        InstantRunDex reloadDexTransform = new InstantRunDex(
                variantScope,
                dexByteCodeConverter,
                dexOptions,
                logger);

        reloadDexTask =
                transformManager
                        .addTransform(tasks, transformVariantScope, reloadDexTransform)
                        .orElse(null);
        anchorTask.optionalDependsOn(tasks, reloadDexTask);

        return buildInfoLoaderTask;
    }


    /**
     * Creates all InstantRun related transforms after compilation.
     */
    @NonNull
    public AndroidTask<PreColdSwapTask> createPreColdswapTask(
            @NonNull Project project) {

        TransformVariantScope transformVariantScope = variantScope.getTransformVariantScope();
        InstantRunBuildContext context = variantScope.getInstantRunBuildContext();

        context.setApiLevel(
                AndroidGradleOptions.getTargetFeatureLevel(project),
                AndroidGradleOptions.getColdswapMode(project),
                AndroidGradleOptions.getBuildTargetAbi(project));
        context.setDensity(AndroidGradleOptions.getBuildTargetDensity(project));

        if (transformVariantScope.getGlobalScope().isActive(OptionalCompilationStep.FULL_APK)) {
            context.setVerifierStatus(InstantRunVerifierStatus.FULL_BUILD_REQUESTED);
        } else if (transformVariantScope.getGlobalScope().isActive(
                OptionalCompilationStep.RESTART_ONLY)) {
            context.setVerifierStatus(InstantRunVerifierStatus.COLD_SWAP_REQUESTED);
        }

        AndroidTask<PreColdSwapTask> preColdSwapTask = androidTasks.create(
                tasks, new PreColdSwapTask.ConfigAction("preColdswap",
                        transformVariantScope,
                        variantScope));

        preColdSwapTask.optionalDependsOn(tasks, verifierTask);

        return preColdSwapTask;
    }


    /**
     * If we are at API 21 or above, we generate multi-dexes.
     */
    @NonNull
    public void createSlicerTask() {
        TransformVariantScope transformVariantScope = variantScope.getTransformVariantScope();
        //
        InstantRunSlicer slicer = new InstantRunSlicer(logger, variantScope);
        Optional<AndroidTask<TransformTask>> slicing =
                transformManager.addTransform(tasks, transformVariantScope, slicer);
        slicing.ifPresent(variantScope::addColdSwapBuildTask);
    }

    /**
     * Creates the task to save the build-info.xml and sets its dependencies.
     * @return the task instance.
     */
    @NonNull
    public AndroidTask<InstantRunWrapperTask> createBuildInfoGeneratorTask(
            AndroidTask<?>... dependencies) {
        AndroidTask<InstantRunWrapperTask> buildInfoGeneratorTask =
                androidTasks.create(tasks,
                        new InstantRunWrapperTask.ConfigAction(variantScope, logger));
        buildInfoGeneratorTask.dependsOn(tasks, reloadDexTask);
        if (dependencies != null) {
            for (AndroidTask<?> dependency : dependencies) {
                buildInfoGeneratorTask.dependsOn(tasks, dependency);
            }
        }
        return buildInfoGeneratorTask;
    }

}
