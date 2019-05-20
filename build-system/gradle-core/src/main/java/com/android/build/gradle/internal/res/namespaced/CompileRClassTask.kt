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
package com.android.build.gradle.internal.res.namespaced

import com.android.build.gradle.internal.scope.BuildArtifactsHolder
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.internal.tasks.VariantAwareTask
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.compile.JavaCompile
import java.io.File

/**
 * Task to compile a directory containing R.java file(s) and jar the result.
 *
 * For namespaced libraries, there will be exactly one R.java file, but for applications there will
 * be a regenerated one per dependency.
 *
 * In the future, this might not call javac at all, but it needs to be profiled first.
 */
@CacheableTask
abstract class CompileRClassTask : JavaCompile(), VariantAwareTask {

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @Internal
    override lateinit var variantName: String


    // Override without the OutputDirectory annotation, it is already present on the above property.
    @Suppress("RedundantOverride")
    override fun getDestinationDir(): File {
        return super.getDestinationDir()
    }

    class CreationAction(variantScope: VariantScope) :
        VariantTaskCreationAction<CompileRClassTask>(variantScope) {

        override val name: String
            get() = variantScope.getTaskName("compile", "FinalRClass")
        override val type: Class<CompileRClassTask>
            get() = CompileRClassTask::class.java

        override fun handleProvider(taskProvider: TaskProvider<out CompileRClassTask>) {
            super.handleProvider(taskProvider)
            variantScope.artifacts.producesDir(
                InternalArtifactType.RUNTIME_R_CLASS_CLASSES,
                BuildArtifactsHolder.OperationType.INITIAL,
                taskProvider,
                CompileRClassTask::outputDirectory
            )
        }

        override fun configure(task: CompileRClassTask) {
            super.configure(task)

            val artifacts = variantScope.artifacts
            task.classpath = task.project.files()
            task.source(
                artifacts.getFinalProduct<Directory>(InternalArtifactType.RUNTIME_R_CLASS_SOURCES))
            task.setDestinationDir(task.outputDirectory.map { it.asFile })
        }
    }

}
