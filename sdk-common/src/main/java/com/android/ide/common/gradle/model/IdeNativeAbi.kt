/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.ide.common.gradle.model

import com.android.builder.model.v2.models.ndk.NativeAbi
import java.io.File

data class IdeNativeAbi private constructor(
    override val name: String,
    override val sourceFlagsFile: File,
    override val symbolFolderIndexFile: File,
    override val buildFileIndexFile: File
) : NativeAbi {
    constructor(nativeAbi: NativeAbi) : this(
        nativeAbi.name,
        nativeAbi.sourceFlagsFile,
        nativeAbi.symbolFolderIndexFile,
        nativeAbi.buildFileIndexFile
    )
}