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

package com.android.build.gradle.internal.fusedlibrary

import com.android.build.api.artifact.Artifact
import com.android.build.api.artifact.ArtifactKind
import org.gradle.api.file.Directory
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.file.RegularFile

@Suppress("ClassName")
sealed class
FusedLibraryInternalArtifactType<T : FileSystemLocation>(
    kind: ArtifactKind<T>,
    category: Category = Category.INTERMEDIATES,
) : Artifact.Single<T>(kind, category) {

    // Directory of classes for use in the fused library.
    object FINAL_CLASSES: FusedLibraryInternalArtifactType<Directory>(ArtifactKind.DIRECTORY), Replaceable
    object MERGED_CLASSES: FusedLibraryInternalArtifactType<Directory>(ArtifactKind.DIRECTORY), Replaceable
    // Directory containing merged resources from all libraries and their dependencies.
    object MERGED_RES: FusedLibraryInternalArtifactType<Directory>(ArtifactKind.DIRECTORY), Replaceable
    // Directory containing blame log of fused library manifest merging
    object MERGED_RES_BLAME_LOG: FusedLibraryInternalArtifactType<Directory>(ArtifactKind.DIRECTORY), Replaceable
    object CLASSES_JAR: FusedLibraryInternalArtifactType<RegularFile>(ArtifactKind.FILE), Replaceable
    object BUNDLED_LIBRARY: FusedLibraryInternalArtifactType<RegularFile>(ArtifactKind.FILE), Replaceable
    // R Class containing all Android resource symbols from libraries contained in a fused library.
    object FUSED_R_CLASS : FusedLibraryInternalArtifactType<RegularFile>(FILE), Replaceable
    object INCREMENTAL_MERGED_RES : FusedLibraryInternalArtifactType<Directory>(ArtifactKind.DIRECTORY), Replaceable
    object MERGED_MANIFEST: FusedLibraryInternalArtifactType<RegularFile>(FILE), Replaceable
    object MANIFEST_MERGE_REPORT: FusedLibraryInternalArtifactType<RegularFile>(FILE), Replaceable
}
