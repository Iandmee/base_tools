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

package com.android.build.gradle.tasks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.annotations.VisibleForTesting;
import com.android.build.api.artifact.BuildableArtifact;
import com.android.build.gradle.internal.TaskManager;
import com.android.build.gradle.internal.aapt.AaptGeneration;
import com.android.build.gradle.internal.aapt.AaptGradleFactory;
import com.android.build.gradle.internal.core.GradleVariantConfiguration;
import com.android.build.gradle.internal.dsl.AaptOptions;
import com.android.build.gradle.internal.dsl.DslAdaptersKt;
import com.android.build.gradle.internal.res.Aapt2MavenUtils;
import com.android.build.gradle.internal.res.Aapt2ProcessResourcesRunnable;
import com.android.build.gradle.internal.res.namespaced.Aapt2CompileRunnable;
import com.android.build.gradle.internal.res.namespaced.Aapt2DaemonManagerService;
import com.android.build.gradle.internal.res.namespaced.Aapt2ServiceKey;
import com.android.build.gradle.internal.scope.BuildElements;
import com.android.build.gradle.internal.scope.ExistingBuildElements;
import com.android.build.gradle.internal.scope.InternalArtifactType;
import com.android.build.gradle.internal.scope.TaskConfigAction;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.tasks.IncrementalTask;
import com.android.build.gradle.internal.variant.BaseVariantData;
import com.android.builder.core.AndroidBuilder;
import com.android.builder.core.VariantTypeImpl;
import com.android.builder.internal.aapt.Aapt;
import com.android.builder.internal.aapt.AaptException;
import com.android.builder.internal.aapt.AaptPackageConfig;
import com.android.builder.internal.aapt.BlockingResourceLinker;
import com.android.builder.internal.aapt.v1.AaptV1;
import com.android.builder.internal.aapt.v2.Aapt2RenamingConventions;
import com.android.ide.common.blame.MergingLog;
import com.android.ide.common.blame.MergingLogRewriter;
import com.android.ide.common.blame.ParsingProcessOutputHandler;
import com.android.ide.common.blame.parser.ToolOutputParser;
import com.android.ide.common.blame.parser.aapt.Aapt2OutputParser;
import com.android.ide.common.blame.parser.aapt.AaptOutputParser;
import com.android.ide.common.process.ProcessException;
import com.android.ide.common.process.ProcessOutputHandler;
import com.android.ide.common.resources.CompileResourceRequest;
import com.android.ide.common.resources.FileStatus;
import com.android.ide.common.resources.QueueableResourceCompiler;
import com.android.utils.FileUtils;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.inject.Inject;
import org.gradle.api.file.FileCollection;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.workers.IsolationMode;
import org.gradle.workers.WorkerExecutor;

public class VerifyLibraryResourcesTask extends IncrementalTask {

    private File compiledDirectory;
    private FileCollection inputDirectory;
    private File mergeBlameLogFolder;
    private InternalArtifactType taskInputType;
    private BuildableArtifact manifestFiles;

    private AaptGeneration aaptGeneration;
    @Nullable private FileCollection aapt2FromMaven;

    private final WorkerExecutor workerExecutor;

    @Inject
    public VerifyLibraryResourcesTask(WorkerExecutor workerExecutor) {
        this.workerExecutor = workerExecutor;
    }

    @Override
    protected boolean isIncremental() {
        return true;
    }

    @Override
    protected final void doFullTaskAction() throws Exception {
        // Mark all files as NEW and continue with the verification.
        Map<File, FileStatus> fileStatusMap;
        try (Stream<Path> paths = Files.walk(inputDirectory.getSingleFile().toPath())) {
            fileStatusMap =
                    paths.filter(Files::isRegularFile)
                            .collect(Collectors.toMap(Path::toFile, file -> FileStatus.NEW));
        }

        FileUtils.cleanOutputDir(compiledDirectory);
        compileAndVerifyResources(fileStatusMap);
    }

    @Override
    protected final void doIncrementalTaskAction(@NonNull Map<File, FileStatus> changedInputs)
            throws Exception {
        compileAndVerifyResources(changedInputs);
    }

    /**
     * Compiles and links the resources of the library.
     *
     * @param inputs the new, changed or modified files that need to be compiled or removed.
     */
    private void compileAndVerifyResources(@NonNull Map<File, FileStatus> inputs) throws Exception {

        AndroidBuilder builder = getBuilder();
        MergingLog mergingLog = new MergingLog(mergeBlameLogFolder);

        MergingLogRewriter mergingLogRewriter =
                new MergingLogRewriter(mergingLog::find, builder.getMessageReceiver());
        ProcessOutputHandler processOutputHandler =
                new ParsingProcessOutputHandler(
                        new ToolOutputParser(
                                aaptGeneration == AaptGeneration.AAPT_V1
                                        ? new AaptOutputParser()
                                        : new Aapt2OutputParser(),
                                getILogger()),
                        mergingLogRewriter);

        BuildElements manifestsOutputs = ExistingBuildElements.from(taskInputType, manifestFiles);
        File manifestFile = Iterables.getOnlyElement(manifestsOutputs).getOutputFile();

        if (aaptGeneration == AaptGeneration.AAPT_V2_DAEMON_SHARED_POOL) {
            Aapt2ServiceKey aapt2ServiceKey =
                    Aapt2DaemonManagerService.registerAaptService(
                            aapt2FromMaven, getBuildTools(), getILogger());
            // If we're using AAPT2 we need to compile the resources into the compiled directory
            // first as we need the .flat files for linking.
            compileResources(
                    inputs,
                    compiledDirectory,
                    null,
                    workerExecutor,
                    aapt2ServiceKey,
                    inputDirectory.getSingleFile());
            AaptPackageConfig config = getAaptPackageConfig(compiledDirectory, manifestFile);
            Aapt2ProcessResourcesRunnable.Params params =
                    new Aapt2ProcessResourcesRunnable.Params(aapt2ServiceKey, config);
            workerExecutor.submit(
                    Aapt2ProcessResourcesRunnable.class,
                    it -> {
                        it.setIsolationMode(IsolationMode.NONE);
                        it.setParams(params);
                    });
            return;
        }

        try (Aapt aapt =
                AaptGradleFactory.make(
                        aaptGeneration,
                        builder,
                        processOutputHandler,
                        true,
                        0)) {

            if (aapt instanceof AaptV1) {
                // If we're using AAPT1 we only need to link the resources.
                linkResources(inputDirectory.getSingleFile(), aapt, manifestFile);
            } else {
                // If we're using AAPT2 we need to compile the resources into the compiled directory
                // first as we need the .flat files for linking.
                compileResources(
                        inputs,
                        compiledDirectory,
                        aapt,
                        null,
                        null,
                        inputDirectory.getSingleFile());
                linkResources(compiledDirectory, aapt, manifestFile);
            }
        }
    }

    /**
     * Compiles new or changed files and removes files that were compiled from the removed files.
     *
     * <p>Should only be called when using AAPT2.
     *
     * @param inputs the new, changed or modified files that need to be compiled or removed.
     * @param outDirectory the directory containing compiled resources.
     * @param aapt AAPT tool to execute the resource compiling, either must be supplied or worker
     *     executor and revision must be supplied.
     * @param aapt2ServiceKey the AAPT2 service to inject in to the worker executor.
     * @param workerExecutor the worker executor to submit AAPT compilations to.
     * @param mergedResDirectory directory containing merged uncompiled resources.
     */
    @VisibleForTesting
    public static void compileResources(
            @NonNull Map<File, FileStatus> inputs,
            @NonNull File outDirectory,
            @Nullable QueueableResourceCompiler aapt,
            @Nullable WorkerExecutor workerExecutor,
            @Nullable Aapt2ServiceKey aapt2ServiceKey,
            @NonNull File mergedResDirectory)
            throws AaptException, ExecutionException, InterruptedException, IOException {
        Preconditions.checkState(
                !(aapt instanceof AaptV1),
                "Library resources should be compiled for verification using AAPT2");

        Preconditions.checkState(
                aapt != null || (workerExecutor != null && aapt2ServiceKey != null));
        List<Future<File>> compiling = new ArrayList<>();

        for (Map.Entry<File, FileStatus> input : inputs.entrySet()) {
            // Ignore files and directories directly under the merged resources directory.
            if (input.getKey().getParentFile().equals(mergedResDirectory)) {
                continue;
            }
            switch (input.getValue()) {
                case NEW:
                case CHANGED:
                    // If the file is NEW or CHANGED we need to compile it into the output
                    // directory. AAPT2 overwrites files in case they were CHANGED so no need to
                    // remove the corresponding file.
                    try {
                        CompileResourceRequest request =
                                new CompileResourceRequest(
                                        input.getKey(),
                                        outDirectory,
                                        input.getKey().getParent(),
                                        false /* pseudo-localize */,
                                        false /* crunch PNGs */);
                        if (aapt != null) {
                            Future<File> result = aapt.compile(request);
                            compiling.add(result);
                        } else {
                            workerExecutor.submit(
                                    Aapt2CompileRunnable.class,
                                    config -> {
                                        config.setIsolationMode(IsolationMode.NONE);
                                        config.params(
                                                new Aapt2CompileRunnable.Params(
                                                        aapt2ServiceKey,
                                                        Collections.singletonList(request)));
                                    });
                        }
                    } catch (Exception e) {
                        throw new AaptException(
                                String.format(
                                        "Failed to compile file %s",
                                        input.getKey().getAbsolutePath()),
                                e);
                    }
                    break;
                case REMOVED:
                    // If the file was REMOVED we need to remove the corresponding file from the
                    // output directory.
                    FileUtils.deleteIfExists(
                            new File(
                                    outDirectory,
                                    Aapt2RenamingConventions.compilationRename(input.getKey())));
            }
        }
        if (workerExecutor != null) {
            workerExecutor.await();
        } else {
            // Wait for all the files to finish compiling.
            for (Future<File> result : compiling) {
                result.get();
            }
        }
    }

    /**
     * Calls AAPT link to verify the correctness of the library's resources.
     *
     * @param resDir directory containing resources to link.
     * @param aapt AAPT tool to execute the resource linking.
     * @param manifestFile the manifest file to package.
     */
    private void linkResources(
            @NonNull File resDir, @NonNull BlockingResourceLinker aapt, @NonNull File manifestFile)
            throws ProcessException, IOException {

        Preconditions.checkNotNull(manifestFile, "Manifest file cannot be null");

        AaptPackageConfig config = getAaptPackageConfig(resDir, manifestFile);

        AndroidBuilder.processResources(aapt, config, getILogger());
    }

    @NonNull
    private AaptPackageConfig getAaptPackageConfig(
            @NonNull File resDir, @NonNull File manifestFile) {
        // We're do not want to generate any files - only to make sure everything links properly.
        return new AaptPackageConfig.Builder()
                .setManifestFile(manifestFile)
                .setResourceDir(resDir)
                .setLibrarySymbolTableFiles(ImmutableSet.of())
                .setOptions(DslAdaptersKt.convert(new AaptOptions()))
                .setVariantType(VariantTypeImpl.LIBRARY)
                .setAndroidTarget(getBuilder().getTarget())
                .build();
    }

    public static class ConfigAction implements TaskConfigAction<VerifyLibraryResourcesTask> {
        protected final VariantScope scope;
        private final TaskManager.MergeType sourceArtifactType;

        public ConfigAction(
                @NonNull VariantScope scope, @NonNull TaskManager.MergeType sourceArtifactType) {
            this.scope = scope;
            this.sourceArtifactType = sourceArtifactType;
        }

        /** Return the name of the task to be configured. */
        @NonNull
        @Override
        public String getName() {
            return scope.getTaskName("verify", "Resources");
        }

        /** Return the class type of the task to be configured. */
        @NonNull
        @Override
        public Class<VerifyLibraryResourcesTask> getType() {
            return VerifyLibraryResourcesTask.class;
        }

        /** Configure the given newly-created task object. */
        @Override
        public void execute(@NonNull VerifyLibraryResourcesTask verifyLibraryResources) {
            final BaseVariantData variantData = scope.getVariantData();
            final GradleVariantConfiguration config = variantData.getVariantConfiguration();
            verifyLibraryResources.setVariantName(config.getFullName());

            verifyLibraryResources.setAndroidBuilder(scope.getGlobalScope().getAndroidBuilder());

            verifyLibraryResources.aaptGeneration =
                    AaptGeneration.fromProjectOptions(scope.getGlobalScope().getProjectOptions());
            verifyLibraryResources.aapt2FromMaven =
                    Aapt2MavenUtils.getAapt2FromMavenIfEnabled(scope.getGlobalScope());
            verifyLibraryResources.setIncrementalFolder(scope.getIncrementalDir(getName()));

            Preconditions.checkState(
                    sourceArtifactType == TaskManager.MergeType.MERGE,
                    "Support for not merging resources in libraries not implemented yet.");
            verifyLibraryResources.inputDirectory =
                    scope.getOutput(sourceArtifactType.getOutputType());

            verifyLibraryResources.compiledDirectory = scope.getCompiledResourcesOutputDir();
            verifyLibraryResources.mergeBlameLogFolder = scope.getResourceBlameLogDir();

            boolean aaptFriendlyManifestsFilePresent =
                    scope.getBuildArtifactsHolder()
                            .hasArtifact(InternalArtifactType.AAPT_FRIENDLY_MERGED_MANIFESTS);
            verifyLibraryResources.taskInputType =
                    aaptFriendlyManifestsFilePresent
                            ? InternalArtifactType.AAPT_FRIENDLY_MERGED_MANIFESTS
                            : scope.getInstantRunBuildContext().isInInstantRunMode()
                                    ? InternalArtifactType.INSTANT_RUN_MERGED_MANIFESTS
                                    : InternalArtifactType.MERGED_MANIFESTS;
            verifyLibraryResources.manifestFiles =
                    scope.getBuildArtifactsHolder()
                            .getFinalArtifactFiles(verifyLibraryResources.taskInputType);
        }
    }

    @Input
    public String getAaptGeneration() {
        return aaptGeneration.name();
    }

    @InputFiles
    @Optional
    @PathSensitive(PathSensitivity.RELATIVE)
    @Nullable
    public FileCollection getAapt2FromMaven() {
        return aapt2FromMaven;
    }

    @NonNull
    @InputFiles
    public BuildableArtifact getManifestFiles() {
        return manifestFiles;
    }

    @NonNull
    @Input
    public InternalArtifactType getTaskInputType() {
        return taskInputType;
    }

    @NonNull
    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    public FileCollection getInputDirectory() {
        // Merged resources directory.
        return inputDirectory;
    }

    @NonNull
    @OutputDirectory
    public File getCompiledDirectory() {
        return compiledDirectory;
    }

    @Input
    public File getMergeBlameLogFolder() {
        return mergeBlameLogFolder;
    }
}
