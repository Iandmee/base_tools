/*
 * Copyright (C) 2022 The Android Open Source Project
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

import com.android.build.gradle.internal.privaysandboxsdk.PrivacySandboxSdkInternalArtifactType
import com.android.build.gradle.internal.privaysandboxsdk.PrivacySandboxSdkVariantScope
import com.android.build.gradle.internal.services.getBuildService
import com.android.build.gradle.internal.tasks.DexMergingTask
import com.android.build.gradle.internal.tasks.DexMergingTaskDelegate
import com.android.build.gradle.internal.tasks.NewIncrementalTask
import com.android.build.gradle.internal.tasks.factory.TaskCreationAction
import com.android.build.gradle.internal.utils.fromDisallowChanges
import com.android.build.gradle.internal.utils.setDisallowChanges
import com.android.build.gradle.options.SyncOptions
import com.android.builder.dexing.DexingType
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskProvider
import org.gradle.work.Incremental
import org.gradle.work.InputChanges

@CacheableTask
abstract class PrivacySandboxSdkMergeDexTask: NewIncrementalTask() {

    @get:Incremental
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val dexDirs: ConfigurableFileCollection

    @get:Nested
    abstract val sharedParams: DexMergingTask.SharedParams

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    override fun doTaskAction(inputChanges: InputChanges) {
        workerExecutor.noIsolation().submit(DexMergingTaskDelegate::class.java) {
            it.initializeFromAndroidVariantTask(this)
            it.initialize(
                sharedParams = sharedParams,
                numberOfBuckets = 1,
                dexDirsOrJars = dexDirs.files.toList(),
                outputDir = outputDir,
                incremental = inputChanges.isIncremental,
                fileChanges = if (inputChanges.isIncremental) {
                    inputChanges.getFileChanges(dexDirs).toSerializable()
                } else {
                    null
                },
                mainDexListOutput = null
            )
        }
    }

    class CreationAction constructor(
        private val creationConfig: PrivacySandboxSdkVariantScope,
    ) : TaskCreationAction<PrivacySandboxSdkMergeDexTask>() {

        override val name = "mergeDex"
        override val type = PrivacySandboxSdkMergeDexTask::class.java

        override fun handleProvider(taskProvider: TaskProvider<PrivacySandboxSdkMergeDexTask>) {
            super.handleProvider(taskProvider)
            creationConfig.artifacts.setInitialProvider(
                taskProvider,
                PrivacySandboxSdkMergeDexTask::outputDir
            ).on(PrivacySandboxSdkInternalArtifactType.DEX)

        }

        override fun configure(task: PrivacySandboxSdkMergeDexTask) {
            task.analyticsService.setDisallowChanges(
                getBuildService(creationConfig.services.buildServiceRegistry)
            )
            val minSdk = creationConfig.extension.minSdk ?: 1
            task.sharedParams.apply {
                dexingType.setDisallowChanges(
                    if (minSdk >= 21) DexingType.NATIVE_MULTIDEX
                    else DexingType.MONO_DEX
                )
                minSdkVersion.setDisallowChanges(
                    minSdk
                )
                debuggable.setDisallowChanges(false)
                errorFormatMode.setDisallowChanges(SyncOptions.ErrorFormatMode.HUMAN_READABLE)
            }

            task.dexDirs.fromDisallowChanges(
                creationConfig.artifacts.get(PrivacySandboxSdkInternalArtifactType.DEX_ARCHIVE)
            )
        }
    }
}
