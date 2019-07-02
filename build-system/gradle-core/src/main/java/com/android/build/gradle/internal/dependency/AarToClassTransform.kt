/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.build.gradle.internal.dependency

import com.android.SdkConstants
import com.android.SdkConstants.DOT_JAR
import com.android.SdkConstants.FN_API_JAR
import com.android.SdkConstants.FN_CLASSES_JAR
import com.android.SdkConstants.LIBS_FOLDER
import com.android.builder.packaging.JarMerger
import org.gradle.api.artifacts.transform.InputArtifact
import org.gradle.api.artifacts.transform.TransformAction
import org.gradle.api.artifacts.transform.TransformOutputs
import org.gradle.api.artifacts.transform.TransformParameters
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

/**
 * A Gradle Artifact [TransformAction] from a processed AAR to a single classes JAR file.
 */
abstract class AarToClassTransform : TransformAction<AarToClassTransform.Params> {

    interface Params : TransformParameters {
        /** If set, return the compile classpath, otherwise return the runtime classpath. */
        @get:Input
        val forCompileUse: Property<Boolean>

        @get:Input
        val autoNamespaceDependencies: Property<Boolean>
    }

    @get:InputArtifact
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    abstract val inputAarFile: Provider<FileSystemLocation>

    override fun transform(transformOutputs: TransformOutputs) {

        ZipFile(inputAarFile.get().asFile).use { inputAar ->
            if (parameters.autoNamespaceDependencies.get() &&
                inputAar.getEntry(SdkConstants.FN_RESOURCE_STATIC_LIBRARY) == null
            ) {
                // Due to kotlin inlining, the implementations of the namespaced jars on the compile
                // classpath as well as the runtime classpath need to be auto-namespaced.
                return
            }
            val useSuffix = if (parameters.forCompileUse.get()) "api" else "runtime"
            val outputFileName =
                "${inputAarFile.get().asFile.nameWithoutExtension}-$useSuffix$DOT_JAR"
            val outputJar = transformOutputs.file(outputFileName)
            JarMerger(outputJar.toPath()).use { outputApiJar ->
                if (parameters.forCompileUse.get()) {
                    val apiJAr = inputAar.getEntry(FN_API_JAR)
                    if (apiJAr != null) {
                        inputAar.copyEntryTo(apiJAr, outputApiJar)
                        return
                    }
                }
                inputAar.copyAllClassesJarsTo(outputApiJar)
            }
        }
    }

    companion object {
        private const val LIBS_FOLDER_SLASH = "$LIBS_FOLDER/"

        private fun ZipFile.copyAllClassesJarsTo(outputApiJar: JarMerger) {
            entries()
                .asSequence()
                .filter(::isClassesJar)
                .forEach { copyEntryTo(it, outputApiJar) }
        }

        private fun ZipFile.copyEntryTo(entry: ZipEntry, outputApiJar: JarMerger) {
            getInputStream(entry).use { outputApiJar.addJar(it) }
        }

        private fun isClassesJar(entry: ZipEntry): Boolean {
            val name = entry.name
            return name == FN_CLASSES_JAR ||
                    (name.startsWith(LIBS_FOLDER_SLASH) && name.endsWith(DOT_JAR))
        }
    }
}
