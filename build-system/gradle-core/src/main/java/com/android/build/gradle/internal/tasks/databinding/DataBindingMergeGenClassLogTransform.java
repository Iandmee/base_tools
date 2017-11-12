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

package com.android.build.gradle.internal.tasks.databinding;

import android.databinding.tool.DataBindingBuilder;
import com.android.annotations.NonNull;
import com.android.build.api.transform.DirectoryInput;
import com.android.build.api.transform.QualifiedContent;
import com.android.build.api.transform.Status;
import com.android.build.api.transform.Transform;
import com.android.build.api.transform.TransformException;
import com.android.build.api.transform.TransformInput;
import com.android.build.api.transform.TransformInvocation;
import com.android.build.gradle.internal.LoggerWrapper;
import com.android.build.gradle.internal.pipeline.TransformManager;
import com.android.utils.ILogger;
import com.google.common.collect.Sets;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.IOFileFilter;
import org.apache.commons.io.filefilter.TrueFileFilter;
import org.gradle.api.logging.Logger;

/**
 * This transform merges the generated class log from dependencies and puts them into a folder which
 * will be consumed by {@link DataBindingGenBaseClassesTask}.
 *
 * <p>There is only a secondary output from this transform. The actual file is generated by that
 * task which is a summary of all of its dependencies.
 */
public class DataBindingMergeGenClassLogTransform extends Transform {
    @NonNull private final ILogger logger;
    private final File outFolder;

    public DataBindingMergeGenClassLogTransform(@NonNull Logger logger, File outFolder) {
        this.logger = new LoggerWrapper(logger);
        this.outFolder = outFolder;
    }

    @NonNull
    @Override
    public String getName() {
        return "dataBindingMergeGenClasses";
    }

    @NonNull
    @Override
    public Collection<File> getSecondaryDirectoryOutputs() {
        return Collections.singleton(outFolder);
    }

    @Override
    public void transform(@NonNull TransformInvocation transformInvocation)
            throws TransformException, InterruptedException, IOException {
        Collection<TransformInput> inputs = transformInvocation.getReferencedInputs();
        if (transformInvocation.isIncremental()) {
            incrementalUpdate(inputs);
        } else {
            fullCopy(inputs);
        }
    }

    private void incrementalUpdate(@NonNull Collection<TransformInput> inputs) {
        inputs.forEach(
                input ->
                        input.getDirectoryInputs()
                                .forEach(
                                        directoryInput -> {
                                            directoryInput
                                                    .getChangedFiles()
                                                    .forEach(this::processFileInput);
                                        }));
    }

    private void processFileInput(File file, Status status) {
        if (isClassListFile(file.getName())) {
            switch (status) {
                case NOTCHANGED:
                    // Ignore
                    break;
                case ADDED:
                case CHANGED:
                    try {
                        FileUtils.copyFile(file, new File(outFolder, file.getName()));
                    } catch (IOException e) {
                        logger.error(e, "Cannot copy data binding artifacts from dependency.");
                        throw new UncheckedIOException(e);
                    }
                    break;
                case REMOVED:
                    try {
                        File outFile = new File(outFolder, file.getName());
                        if (outFile.exists()) {
                            FileUtils.forceDelete(outFile);
                        }
                    } catch (IOException e) {
                        logger.error(
                                e,
                                "error while trying to delete removed data"
                                        + " binding artifact from dependency.");
                        throw new UncheckedIOException(e);
                    }
                    break;
            }
        }
    }

    private void fullCopy(Collection<TransformInput> inputs) throws IOException {
        com.android.utils.FileUtils.cleanOutputDir(outFolder);
        for (TransformInput input : inputs) {
            for (DirectoryInput dirInput : input.getDirectoryInputs()) {
                File dataBindingDir = dirInput.getFile();
                if (!dataBindingDir.exists()) {
                    continue;
                }
                // copy generated binder list
                copyBindingClassNames(dataBindingDir);
            }
        }
    }

    private void copyBindingClassNames(File dataBindingDir) throws IOException {
        Collection<File> files =
                FileUtils.listFiles(
                        dataBindingDir,
                        new IOFileFilter() {
                            @Override
                            public boolean accept(File file) {
                                return isClassListFile(file.getName());
                            }

                            @Override
                            public boolean accept(File dir, String name) {
                                return isClassListFile(name);
                            }
                        },
                        TrueFileFilter.INSTANCE);
        for (File listFile : files) {
            FileUtils.copyFile(listFile, new File(outFolder, listFile.getName()));
        }
    }

    private static boolean isClassListFile(String listFile) {
        return listFile.endsWith(DataBindingBuilder.BINDING_CLASS_LIST_SUFFIX);
    }

    @NonNull
    @Override
    public Set<QualifiedContent.ContentType> getInputTypes() {
        return TransformManager.DATA_BINDING_BASE_CLASS_LOG_ARTIFACT;
    }

    @NonNull
    @Override
    public Set<QualifiedContent.Scope> getReferencedScopes() {
        return Sets.immutableEnumSet(
                QualifiedContent.Scope.SUB_PROJECTS, QualifiedContent.Scope.EXTERNAL_LIBRARIES);
    }

    @NonNull
    @Override
    public Set<QualifiedContent.Scope> getScopes() {
        return TransformManager.EMPTY_SCOPES;
    }

    @Override
    public boolean isIncremental() {
        return true;
    }
}
