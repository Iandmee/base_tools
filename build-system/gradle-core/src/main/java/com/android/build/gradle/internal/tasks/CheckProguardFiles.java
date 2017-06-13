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

package com.android.build.gradle.internal.tasks;

import com.android.annotations.NonNull;
import com.android.build.gradle.internal.scope.TaskConfigAction;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.shrinker.ProguardConfig;
import com.android.build.gradle.shrinker.parser.ProguardFlags;
import com.android.build.gradle.shrinker.parser.UnsupportedFlagsHandler;
import java.io.File;
import java.util.List;
import org.gradle.api.DefaultTask;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.TaskAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CheckProguardFiles extends DefaultTask {
    private static final Logger logger = LoggerFactory.getLogger(CheckProguardFiles.class);

    private List<File> proguardFiles;

    @TaskAction
    public void run() {
        ProguardConfig proguardConfig = new ProguardConfig();

        for (File file : proguardFiles) {
            try {
                proguardConfig.parse(file, UnsupportedFlagsHandler.NO_OP);
            } catch (Exception e) {
                // Don't break the build, but leave some trace of what happened.
                logger.info("Failed to parse " + file.getAbsolutePath(), e);
                continue;
            }

            ProguardFlags flags = proguardConfig.getFlags();
            if (flags.isDontShrink() || flags.isDontOptimize() || flags.isDontObfuscate()) {
                throw new InvalidUserDataException(
                        file.getAbsolutePath()
                                + ": When postprocessing features are configured in the DSL, "
                                + "corresponding flags (e.g. -dontobfuscate) cannot be used.");
            }
        }
    }

    @InputFiles
    public List<File> getProguardFiles() {
        return proguardFiles;
    }

    public static class ConfigAction implements TaskConfigAction<CheckProguardFiles> {
        private final VariantScope scope;

        public ConfigAction(VariantScope scope) {
            this.scope = scope;
        }

        @NonNull
        @Override
        public String getName() {
            return scope.getTaskName("check", "ProguardFiles");
        }

        @NonNull
        @Override
        public Class<CheckProguardFiles> getType() {
            return CheckProguardFiles.class;
        }

        @Override
        public void execute(@NonNull CheckProguardFiles task) {
            task.proguardFiles = scope.getProguardFiles();
        }
    }
}
