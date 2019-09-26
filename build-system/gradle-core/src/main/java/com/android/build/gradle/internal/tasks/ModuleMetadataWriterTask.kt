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

package com.android.build.gradle.internal.tasks

import com.android.build.gradle.internal.scope.BuildArtifactsHolder
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.OutputScope
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskProvider

/**
 * Task responsible for publishing this module metadata (like its application ID) for other modules
 * to consume.
 *
 * If the module is an application module, it publishes the value coming from the variant config.
 *
 * If the module is a base feature, it consumes the value coming from the (installed) application
 * module and republishes it.
 *
 * Both dynamic-feature and feature modules consumes it, from the application module and the base
 * feature module respectively.
 */
abstract class ModuleMetadataWriterTask : NonIncrementalTask() {

    @get:Input lateinit var applicationId: Provider<String> private set

    private lateinit var outputScope: OutputScope

    @get:Input
    val versionCode
        get() = outputScope.mainSplit.versionCode

    @get:Input
    @get:Optional
    val versionName
        get() = outputScope.mainSplit.versionName

    @get:Input
    var debuggable: Boolean = false
    private set

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    override fun doTaskAction() {
        val declaration =
            ModuleMetadata(
                applicationId = applicationId.get(),
                versionCode = versionCode.toString(),
                versionName = versionName,
                debuggable = debuggable
            )

        declaration.save(outputFile.get().asFile)
    }

    class CreationAction(variantScope: VariantScope) :
        VariantTaskCreationAction<ModuleMetadataWriterTask>(variantScope) {

        override val name: String
            get() = variantScope.getTaskName("write", "ModuleMetadata")
        override val type: Class<ModuleMetadataWriterTask>
            get() = ModuleMetadataWriterTask::class.java

        override fun handleProvider(taskProvider: TaskProvider<out ModuleMetadataWriterTask>) {
            super.handleProvider(taskProvider)
            // publish the ID for the dynamic features (whether it's hybrid or not) to consume.
            variantScope.artifacts.producesFile(
                InternalArtifactType.METADATA_BASE_MODULE_DECLARATION,
                BuildArtifactsHolder.OperationType.INITIAL,
                taskProvider,
                ModuleMetadataWriterTask::outputFile,
                ModuleMetadata.PERSISTED_FILE_NAME
            )

            if (variantScope.type.isBaseModule) {
                variantScope.artifacts.republish(
                    InternalArtifactType.METADATA_BASE_MODULE_DECLARATION,
                    InternalArtifactType.METADATA_INSTALLED_BASE_DECLARATION
                )
            }
        }

        override fun configure(task: ModuleMetadataWriterTask) {
            super.configure(task)

            // default value of the app ID to publish. This may get overwritten by something
            // coming from an application module.
            task.applicationId = TaskInputHelper.memoizeToProvider(task.project) {
                variantScope.variantConfiguration.applicationId
            }

            task.outputScope = variantScope.variantData.outputScope

            task.debuggable = variantScope.variantConfiguration.buildType.isDebuggable
        }
    }
}
