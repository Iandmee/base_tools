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

import com.android.build.api.instrumentation.FramesComputationMode
import com.android.build.gradle.internal.component.ComponentCreationConfig
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.services.ClassesHierarchyBuildService
import com.android.build.gradle.internal.services.getBuildService
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.build.gradle.internal.tasks.factory.features.InstrumentationTaskCreationAction
import com.android.build.gradle.internal.tasks.factory.features.InstrumentationTaskCreationActionImpl
import com.android.build.gradle.internal.utils.setDisallowChanges
import com.android.build.gradle.internal.tasks.TaskCategory
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.CompileClasspath
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskProvider
import org.gradle.work.DisableCachingByDefault
import org.gradle.work.Incremental
import org.gradle.work.InputChanges

/**
 * Recalculates the stack frames for all project classes and jars.
 *
 * This is used for recalculating the stack frames after the classes are instrumented with
 * [com.android.build.gradle.tasks.TransformClassesWithAsmTask], when the stack frames computation
 * mode is [FramesComputationMode.COMPUTE_FRAMES_FOR_ALL_CLASSES].
 */
@DisableCachingByDefault
@BuildAnalyzer(primaryTaskCategory = TaskCategory.COMPILED_CLASSES, secondaryTaskCategories = [TaskCategory.SOURCE_PROCESSING])
abstract class RecalculateStackFramesTask : NewIncrementalTask() {

    @get:Classpath
    @get:Incremental
    abstract val classesInputDir: ConfigurableFileCollection

    @get:Classpath
    @get:Incremental
    abstract val jarsInputDir: ConfigurableFileCollection

    @get:CompileClasspath
    abstract val bootClasspath: ConfigurableFileCollection

    @get:CompileClasspath
    abstract val referencedClasses: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val classesOutputDir: DirectoryProperty

    @get:OutputDirectory
    abstract val jarsOutputDir: DirectoryProperty

    @get:Internal
    abstract val classesHierarchyBuildService: Property<ClassesHierarchyBuildService>

    private fun createDelegate() = FixStackFramesDelegate(
        // TODO : this is very fragile, we should not expect the cardinality of the collection
        // of classes directories produced by the ASM transform task.
        classesDir = classesInputDir.singleFile,
        jarsDir = if (jarsInputDir.isEmpty) null else jarsInputDir.singleFile,
        bootClasspath = bootClasspath.files,
        referencedClasses = referencedClasses.files,
        classesOutDir = classesOutputDir.get().asFile,
        jarsOutDir = jarsOutputDir.get().asFile,
        workers = workerExecutor,
        task = this,
        classesHierarchyBuildServiceProvider = classesHierarchyBuildService
    )

    override fun doTaskAction(inputChanges: InputChanges) {
        if (inputChanges.isIncremental) {
            createDelegate().doIncrementalRun(
                jarChanges = inputChanges.getFileChanges(jarsInputDir),
                classesChanges = inputChanges.getFileChanges(classesInputDir)
            )
        } else {
            createDelegate().doFullRun()
        }
    }

    class CreationAction(
        creationConfig: ComponentCreationConfig
    ): VariantTaskCreationAction<RecalculateStackFramesTask, ComponentCreationConfig>(
        creationConfig
    ), InstrumentationTaskCreationAction by InstrumentationTaskCreationActionImpl(
        creationConfig
    ) {

        override val name = computeTaskName("fixInstrumented", "ClassesStackFrames")
        override val type = RecalculateStackFramesTask::class.java

        override fun configure(
            task: RecalculateStackFramesTask
        ) {
            super.configure(task)

            task.bootClasspath.from(creationConfig.global.bootClasspath).disallowChanges()

            task.referencedClasses
                .from(creationConfig.providedOnlyClasspath)
                .from(
                    instrumentationCreationConfig.getDependenciesClassesJarsPostInstrumentation(
                        AndroidArtifacts.ArtifactScope.ALL
                    )
                ).disallowChanges()

            task.classesHierarchyBuildService.setDisallowChanges(
                getBuildService(creationConfig.services.buildServiceRegistry)
            )
        }
    }
}
