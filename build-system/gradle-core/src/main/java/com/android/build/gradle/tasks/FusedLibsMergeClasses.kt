/*
 * Copyright (C) 2021 The Android Open Source Project
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

import com.android.build.gradle.internal.fusedlibs.FusedLibsInternalArtifactType
import com.android.build.gradle.internal.fusedlibs.FusedLibsVariantScope
import com.android.build.gradle.internal.tasks.factory.TaskCreationAction
import org.gradle.api.DefaultTask
import org.gradle.api.attributes.Usage
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.DuplicateFileCopyingException
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.TaskProvider
import org.gradle.work.DisableCachingByDefault
import java.io.File
import java.io.FileOutputStream
import java.util.jar.JarFile

/**
 * merge classes.jar coming from included libraries in fused libraries  plugin.
 */
@DisableCachingByDefault(because = "No calculation is made, merging classes. ")
abstract class FusedLibsMergeClasses: DefaultTask() {

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val incoming: ConfigurableFileCollection

    @TaskAction
    fun taskAction() {

        incoming.files.forEach { file ->
            println("Merging file: ${file.absolutePath}")
            JarFile(file).use { jarFile ->
                jarFile.entries().asSequence().forEach { jarEntry ->
                    jarFile.getInputStream(jarEntry).use { inputStream ->
                        val outputDir = outputDirectory.get().dir(jarEntry.name.substringBeforeLast('/'))
                        val fileName = jarEntry.name.substringAfterLast('/')
                        if (fileName.endsWith((".class"))) {
                            val outputFile = File(outputDir.asFile, fileName)
                            if (outputFile.exists()) {
                                throw DuplicateFileCopyingException(
                                        "${jarEntry.name} is present in multiple jar files.")
                            }
                            outputFile.parentFile.mkdirs()
                            FileOutputStream(outputFile).use { outputStream ->
                                outputStream.write(inputStream.readBytes())
                            }
                        }
                    }
                }
            }
        }
    }

    class CreationAction(val creationConfig: FusedLibsVariantScope) :
        TaskCreationAction<FusedLibsMergeClasses>() {
        override val name: String
            get() = "mergeClasses"
        override val type: Class<FusedLibsMergeClasses>
            get() = FusedLibsMergeClasses::class.java

        override fun handleProvider(taskProvider: TaskProvider<FusedLibsMergeClasses>) {
            super.handleProvider(taskProvider)
            creationConfig.artifacts.setInitialProvider(
                taskProvider,
                FusedLibsMergeClasses::outputDirectory
            ).on(FusedLibsInternalArtifactType.MERGED_CLASSES)
        }

        override fun configure(task: FusedLibsMergeClasses) {
            val artifactView =
                creationConfig
                    .incomingConfigurations
                    .getConfiguration(Usage.JAVA_RUNTIME)
                    .incoming
                    .artifactView{ view -> view.componentFilter(creationConfig.mergeSpec) }

            task.incoming.setFrom(artifactView.files)
        }
    }
}
