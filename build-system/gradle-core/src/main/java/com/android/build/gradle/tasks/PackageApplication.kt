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
package com.android.build.gradle.tasks

import com.android.build.api.artifact.ArtifactTransformationRequest
import com.android.build.api.artifact.ArtifactType
import com.android.build.api.variant.impl.BuiltArtifactsImpl
import com.android.build.gradle.internal.component.ApkCreationConfig
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.InternalArtifactType.APK_IDE_MODEL
import com.android.builder.profile.ProcessProfileWriter
import com.google.wireless.android.sdk.stats.GradleBuildProjectMetrics
import org.gradle.api.file.Directory
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskProvider
import java.io.File
import java.io.IOException
import java.nio.file.Files

/** Task to package an Android application (APK).  */
abstract class PackageApplication : PackageAndroidArtifact() {
    private lateinit var transformationRequest: ArtifactTransformationRequest

    @Internal
    override fun getTransformationRequest(): ArtifactTransformationRequest {
        return transformationRequest
    }
    // ----- CreationAction -----
    /**
     * Configures the task to perform the "standard" packaging, including all files that should end
     * up in the APK.
     */
    class CreationAction(
        creationConfig: ApkCreationConfig,
        private val outputDirectory: File,
        useResourceShrinker: Boolean,
        manifests: Provider<Directory?>,
        manifestType: ArtifactType<Directory>,
        packageCustomClassDependencies: Boolean
    ) : PackageAndroidArtifact.CreationAction<PackageApplication>(
        creationConfig,
        useResourceShrinker,
        manifests,
        manifestType,
        packageCustomClassDependencies
    ) {
        private var transformationRequest: ArtifactTransformationRequest? = null
        private var task: PackageApplication? = null
        override val name: String
            get() = computeTaskName("package")

        override val type: Class<PackageApplication>
            get() = PackageApplication::class.java

        override fun handleProvider(
            taskProvider: TaskProvider<PackageApplication>
        ) {
            super.handleProvider(taskProvider)
            creationConfig.taskContainer.packageAndroidTask = taskProvider
            transformationRequest = (if (useResourceShrinker)
                 creationConfig.artifacts.use(taskProvider)
                    .toRead(InternalArtifactType.SHRUNK_PROCESSED_RES,
                        PackageAndroidArtifact::getResourceFiles)
            else
                creationConfig.artifacts.use(taskProvider)
                    .toRead(InternalArtifactType.PROCESSED_RES,
                        PackageAndroidArtifact::getResourceFiles))
                .andWrite(
                    InternalArtifactType.APK,
                    PackageApplication::getOutputDirectory,
                    outputDirectory.absolutePath)

            // in case configure is called before handleProvider, we need to save the request.
            transformationRequest?.let {
                task?.let { t -> t.transformationRequest = it }
            }

            creationConfig
                .artifacts
                .setInitialProvider(taskProvider, PackageApplication::getIdeModelOutputFile)
                .atLocation { obj: PackageApplication -> obj.outputDirectory }
                .withName(BuiltArtifactsImpl.METADATA_FILE_NAME)
                .on(APK_IDE_MODEL)
        }

        override fun finalConfigure(task: PackageApplication) {
            super.finalConfigure(task)
            this.task = task
            transformationRequest?.let {
                task.transformationRequest = it
            }
        }

    }

    companion object {
        @JvmStatic
        fun recordMetrics(
            projectPath: String?,
            apkOutputFile: File?,
            resourcesApFile: File?
        ) {
            val metricsStartTime = System.nanoTime()
            val metrics = GradleBuildProjectMetrics.newBuilder()
            val apkSize = getSize(apkOutputFile)
            if (apkSize != null) {
                metrics.apkSize = apkSize
            }
            val resourcesApSize =
                getSize(resourcesApFile)
            if (resourcesApSize != null) {
                metrics.resourcesApSize = resourcesApSize
            }
            metrics.metricsTimeNs = System.nanoTime() - metricsStartTime
            ProcessProfileWriter.getProject(projectPath!!).setMetrics(metrics)
        }

        private fun getSize(file: File?): Long? {
            return if (file == null) {
                null
            } else try {
                Files.size(file.toPath())
            } catch (e: IOException) {
                null
            }
        }
    }
}