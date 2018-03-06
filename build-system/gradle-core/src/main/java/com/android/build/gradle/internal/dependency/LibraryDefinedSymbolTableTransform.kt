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

package com.android.build.gradle.internal.dependency

import com.android.SdkConstants.FD_RESOURCES
import com.android.SdkConstants.FN_ANDROID_MANIFEST_XML
import com.android.annotations.NonNull
import com.android.ide.common.symbols.IdProvider
import com.android.ide.common.symbols.ResourceDirectoryParser
import com.android.ide.common.symbols.SymbolIo
import com.android.ide.common.symbols.SymbolTable
import com.android.ide.common.xml.AndroidManifestParser
import com.google.common.collect.ImmutableList
import org.gradle.api.artifacts.transform.ArtifactTransform
import java.io.BufferedInputStream
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

/**
 * Parses the res/ directory inside the AAR and creates a package aware library defined symbol file.
 *
 * The difference between a simple package aware symbol file and package aware library defined
 * symbol file file is that the first one contains all resources from this AAR and its' dependencies
 * while the second one contains only those resources that were defined in this AAR.
 */
class LibraryDefinedSymbolTableTransform : ArtifactTransform() {

    override fun transform(explodedAar: File): MutableList<File> {
        val manifest = explodedAar.resolve(FN_ANDROID_MANIFEST_XML)
        val resDir = explodedAar.resolve(FD_RESOURCES)
        val pkg = getPackage(manifest.toPath())

        // Include the package name in the filename so it's easier to debug and sort.
        val outputFile = File(outputDirectory, "$pkg-R-def.txt")

        // For now we will use our own parsers to get the symbol table. In the future though it will
        // be possible to generate it from partial R files generated by AAPT2 during resource
        // compilation.
        // We pass [null] for the platform attr symbols and use the constant [IdProvider] since we
        // don't care about the real values here.
        val symbols = if (resDir.isDirectory) {
            ResourceDirectoryParser.parseDirectory(
                resDir,
                IdProvider.constant(),
                null
            ).rename(pkg)
        } else {
            // If the /res directory does not exist, simply write an empty resource table.
            SymbolTable.builder().tablePackage(pkg).build()
        }

        SymbolIo.writeRDef(symbols, outputFile.toPath())

        return ImmutableList.of(outputFile)
    }

    private fun getPackage(@NonNull manifest: Path): String =
        BufferedInputStream(Files.newInputStream(manifest)).use {
            AndroidManifestParser.parse(it).`package`
        }
}