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

package com.android.tools.lint.model

import com.android.sdklib.AndroidVersion
import java.io.File

interface LmVariant {
    /** Module containing this variant */
    val module: LmModule

    val name: String
    val useSupportLibraryVectorDrawables: Boolean
    val mainArtifact: LmAndroidArtifact
    val testArtifact: LmJavaArtifact?
    val androidTestArtifact: LmAndroidArtifact?

    // For temporary backwards compatibility
    val oldVariant: com.android.builder.model.Variant?

    // In builder-model these are coming from the merged flavor, plus buildType merged in
    val `package`: String?
    val versionCode: Int?
    val versionName: String?
    val minSdkVersion: AndroidVersion?
    val targetSdkVersion: AndroidVersion?
    val resValues: Map<String, LmResourceField>
    val manifestPlaceholders: Map<String, String>
    val resourceConfigurations: Collection<String>
    val proguardFiles: Collection<File>
    val consumerProguardFiles: Collection<File>

    val sourceProviders: List<LmSourceProvider>
    val testSourceProviders: List<LmSourceProvider>

    val debuggable: Boolean
    val shrinkable: Boolean
}

class DefaultLmVariant(
    override val module: LmModule,

    override val name: String,
    override val useSupportLibraryVectorDrawables: Boolean,
    override val mainArtifact: LmAndroidArtifact,
    override val testArtifact: LmJavaArtifact?,
    override val androidTestArtifact: LmAndroidArtifact?,
    override val `package`: String?,
    override val versionCode: Int?,
    override val versionName: String?,
    override val minSdkVersion: AndroidVersion?,
    override val targetSdkVersion: AndroidVersion?,

    /**
     * Resource fields declared in the DSL. Note that unlike the builder-model,
     * this map merges all the values from the mergedFlavor (which includes the defaultConfig)
     * as well as the buildType.
     */
    override val resValues: Map<String, LmResourceField>,
    /**
     * Manifest placeholders declared in the DSL. Note that unlike the builder-model,
     * this map merges all the values from the mergedFlavor (which includes the defaultConfig)
     * as well as the buildType.
     */
    override val manifestPlaceholders: Map<String, String>,

    override val resourceConfigurations: Collection<String>,
    override val proguardFiles: Collection<File>,
    override val consumerProguardFiles: Collection<File>,

    override val sourceProviders: List<LmSourceProvider>,
    override val testSourceProviders: List<LmSourceProvider>,

    override val debuggable: Boolean,
    override val shrinkable: Boolean,

    // For temporary backwards compatibility
    override val oldVariant: com.android.builder.model.Variant?
) : LmVariant {
    override fun toString(): String = name
}
