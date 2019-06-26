/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.build.gradle.internal.tasks

import com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactScope.EXTERNAL
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType.CLASSES
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ConsumedConfigType.COMPILE_CLASSPATH
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.internal.tasks.factory.TaskCreationAction
import com.android.builder.errors.EvalIssueException
import com.android.builder.errors.EvalIssueReporter
import com.android.ide.common.repository.GradleVersion
import com.android.utils.FileUtils
import org.gradle.api.tasks.CacheableTask

/**
 * Pre build task that performs comparison of runtime and compile classpath for application. If
 * there are any differences between the two, that could lead to runtime issues.
 */
@CacheableTask
abstract class AppClasspathCheckTask : ClasspathComparisonTask() {

    private lateinit var reporter: EvalIssueReporter

    override fun onDifferentVersionsFound(
        group: String,
        module: String,
        runtimeVersion: String,
        compileVersion: String
    ) {

        val suggestedVersion: String = try {
            val runtime = GradleVersion.parse(runtimeVersion)
            val compile = GradleVersion.parse(compileVersion)
            if (runtime > compile) {
                runtimeVersion
            } else {
                compileVersion
            }
        } catch (e: Throwable) {
            // in case we are unable to parse versions for some reason, choose runtime
            runtimeVersion
        }

        val message =
            """Conflict with dependency '$group:$module' in project '${project.path}'.
Resolved versions for runtime classpath ($runtimeVersion) and compile classpath ($compileVersion) differ.
This can lead to runtime crashes.
To resolve this issue follow advice at https://developer.android.com/studio/build/gradle-tips#configure-project-wide-properties.
Alternatively, you can try to fix the problem by adding this snippet to ${project.buildFile}:

dependencies {
    implementation("$group:$module:$suggestedVersion")
}
"""

        reporter.reportError(EvalIssueReporter.Type.GENERIC, EvalIssueException(message))
    }

    class CreationAction(private val variantScope: VariantScope) :
        TaskCreationAction<AppClasspathCheckTask>() {

        override val name: String
            get() = variantScope.getTaskName("check", "Classpath")

        override val type: Class<AppClasspathCheckTask>
            get() = AppClasspathCheckTask::class.java

        override fun configure(task: AppClasspathCheckTask) {
            task.variantName = variantScope.fullVariantName

            task.runtimeClasspath =
                variantScope.getArtifactCollection(RUNTIME_CLASSPATH, EXTERNAL, CLASSES)
            task.compileClasspath =
                variantScope.getArtifactCollection(COMPILE_CLASSPATH, EXTERNAL, CLASSES)
            task.fakeOutputDirectory = FileUtils.join(
                variantScope.globalScope.intermediatesDir,
                name,
                variantScope.variantConfiguration.dirName
            )
            task.reporter = variantScope.globalScope.errorHandler
        }
    }
}
