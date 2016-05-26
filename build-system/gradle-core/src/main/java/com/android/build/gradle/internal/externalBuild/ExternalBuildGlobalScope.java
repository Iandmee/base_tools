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

package com.android.build.gradle.internal.externalBuild;

import com.android.annotations.NonNull;
import com.android.build.gradle.AndroidGradleOptions;
import com.android.build.gradle.internal.scope.TransformGlobalScope;
import com.android.builder.model.OptionalCompilationStep;

import org.gradle.api.Project;

import java.io.File;
import java.util.EnumSet;

/**
 * Implementation of the {@link TransformGlobalScope} for external build system integration
 */
public class ExternalBuildGlobalScope implements TransformGlobalScope {

    private final Project project;

    @NonNull
    private final EnumSet<OptionalCompilationStep> optionalCompilationSteps;

    public ExternalBuildGlobalScope(Project project) {
        this.project = project;
        optionalCompilationSteps = AndroidGradleOptions.getOptionalCompilationSteps(project);
    }

    @Override
    public Project getProject() {
        return project;
    }

    @NonNull
    @Override
    public File getBuildDir() {
        return project.getBuildDir();
    }

    @Override
    public boolean isActive(OptionalCompilationStep step) {
        return optionalCompilationSteps.contains(step);
    }
}
