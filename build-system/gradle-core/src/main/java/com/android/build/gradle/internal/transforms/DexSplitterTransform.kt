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

package com.android.build.gradle.internal.transforms

import com.android.build.api.transform.Format
import com.android.build.api.transform.QualifiedContent
import com.android.build.api.transform.SecondaryFile
import com.android.build.api.transform.Transform
import com.android.build.api.transform.TransformException
import com.android.build.api.transform.TransformInvocation
import com.android.build.gradle.internal.pipeline.TransformManager
import com.android.build.gradle.internal.pipeline.TransformManager.CONTENT_DEX
import com.android.builder.dexing.DexSplitterTool
import com.android.utils.FileUtils
import org.gradle.api.file.Directory
import org.gradle.api.file.FileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import java.io.File
import java.nio.file.Files

/**
 * Transform that splits dex files depending on their feature sources
 */
class DexSplitterTransform(
        private val featureJars: FileCollection,
        private val baseJars: Provider<RegularFile>,
        private val mappingFileSrc: Provider<RegularFile>?,
        private val mainDexList: Provider<RegularFile>?
) :
        Transform() {

    private lateinit var outputDirectoryProperty: Property<Directory>

    override fun getName(): String = "dexSplitter"

    override fun getInputTypes(): MutableSet<QualifiedContent.ContentType> = CONTENT_DEX

    override fun getOutputTypes(): MutableSet<QualifiedContent.ContentType> = CONTENT_DEX

    override fun getScopes(): MutableSet<in QualifiedContent.Scope> =
            TransformManager.SCOPE_FULL_WITH_IR_AND_FEATURES

    override fun isIncremental(): Boolean = false

    override fun getSecondaryFiles(): MutableCollection<SecondaryFile> {
        val secondaryFiles: MutableCollection<SecondaryFile> = mutableListOf()
        secondaryFiles.add(SecondaryFile.nonIncremental(featureJars))
        secondaryFiles.add(SecondaryFile.nonIncremental(baseJars.get().asFile))
        mappingFileSrc?.let { secondaryFiles.add(SecondaryFile.nonIncremental(it.get().asFile)) }
        mainDexList?.let { secondaryFiles.add(SecondaryFile.nonIncremental(it.get().asFile)) }
        return secondaryFiles
    }

    override fun getSecondaryDirectoryOutputs(): MutableCollection<File> {
        return mutableListOf(outputDirectoryProperty.get().asFile)
    }

    override fun transform(transformInvocation: TransformInvocation) {

        try {
            val mappingFile =
                if (mappingFileSrc?.orNull?.asFile?.exists() == true
                    && mappingFileSrc.orNull?.asFile?.isFile == true) {
                mappingFileSrc.orNull?.asFile
            } else {
                null
            }

            val outputProvider = requireNotNull(
                transformInvocation.outputProvider,
                { "No output provider set" }
            )
            outputProvider.deleteAll()
            val outputDir = outputDirectoryProperty.get().asFile
            FileUtils.deleteRecursivelyIfExists(outputDir)

            val builder = DexSplitterTool.Builder(
                outputDir.toPath(), mappingFile?.toPath(), mainDexList?.orNull?.asFile?.toPath()
            )

            for (dirInput in TransformInputUtil.getDirectories(transformInvocation.inputs)) {
                dirInput.listFiles()?.toList()?.map { it.toPath() }?.forEach { builder.addInputArchive(it) }
            }

            featureJars.files.forEach { file ->
                builder.addFeatureJar(file.toPath(), file.nameWithoutExtension)
                Files.createDirectories(File(outputDir, file.nameWithoutExtension).toPath())
            }

            builder.addBaseJar(baseJars.get().asFile.toPath())

            builder.build().run()

            val transformOutputDir =
                outputProvider.getContentLocation(
                    "splitDexFiles", outputTypes, scopes, Format.DIRECTORY
                )
            Files.createDirectories(transformOutputDir.toPath())

            outputDir.listFiles().find { it.name == "base" }?.let {
                FileUtils.copyDirectory(it, transformOutputDir)
                FileUtils.deleteRecursivelyIfExists(it)
            }
        } catch (e: Exception) {
            throw TransformException(e)
        }
    }

    override fun setOutputDirectory(directory: Property<Directory>) {
        outputDirectoryProperty= directory
    }
}
