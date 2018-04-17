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

package com.android.build.gradle.tasks

import com.android.build.gradle.internal.scope.BuildArtifactsHolder
import com.android.build.gradle.internal.scope.TaskConfigAction
import com.android.build.gradle.internal.scope.VariantScope
import org.gradle.api.Task
import org.gradle.api.tasks.TaskProvider

/**
 * Convenient super class for ConfigAction implementation that will process all annotated
 * input and output properties. Each input and output will be looked up in the scope and
 * pre-allocated during the [TaskConfigAction.preConfigure] call.
 *
 * Once the task is created and the [TaskConfigAction.execute] is invoked, the pre-allocated
 * are transferred to the relevant input and output fields of the task instance.
 */
open class AnnotationProcessingTaskConfigAction<T: Task>(
    val scope: VariantScope,
    override val name: String,
    override val type: Class<T>): TaskConfigAction<T>() {

    private val artifactsHolder= TaskArtifactsHolder<T>(scope.artifacts)

    override fun preConfigure(taskProvider: TaskProvider<out T>, taskName: String) {
        super.preConfigure(taskProvider, taskName)
        artifactsHolder.allocateArtifacts(this, taskProvider)
    }

    override fun execute(task: T)  {
        artifactsHolder.transfer(task)
    }
}