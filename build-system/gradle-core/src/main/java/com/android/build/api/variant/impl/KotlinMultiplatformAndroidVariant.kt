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

package com.android.build.api.variant.impl

import com.android.build.api.artifact.Artifacts
import com.android.build.api.variant.GeneratesAar
import com.android.build.api.variant.HasAndroidTest
import com.android.build.api.variant.Instrumentation
import com.android.build.api.variant.Sources
import com.android.build.api.variant.UnitTest
import org.gradle.api.artifacts.Configuration
import org.gradle.api.file.FileCollection
import org.gradle.api.provider.Provider

/**
 * Temporary interface to develop the kotlin multiplatform android plugin.
 *
 * TODO(b/267309622): Move to gradle-api
 */
interface KotlinMultiplatformAndroidVariant: GeneratesAar, HasAndroidTest {
    val namespace: Provider<String>

    val name: String

    /**
     * Access to the variant's buildable artifacts for build customization.
     */
    val artifacts: Artifacts

    /**
     * Access to variant's source files.
     */
    val sources: Sources

    /**
     * Access to the variant's instrumentation options.
     */
    val instrumentation: Instrumentation

    /**
     * Access to the variant's compile classpath.
     *
     * The returned [FileCollection] should not be resolved until execution time.
     */
    val compileClasspath: FileCollection

    /**
     * Access to the variant's compile [Configuration]; for example, the debugCompileClasspath
     * [Configuration] for the debug variant.
     *
     * The returned [Configuration] should not be resolved until execution time.
     */
    val compileConfiguration: Configuration

    /**
     * Access to the variant's runtime [Configuration]; for example, the debugRuntimeClasspath
     * [Configuration] for the debug variant.
     *
     * The returned [Configuration] should not be resolved until execution time.
     */
    val runtimeConfiguration: Configuration

    /**
     * Variant's [UnitTest], or null if the unit tests for this variant are disabled.
     */
    val unitTest: UnitTest?
}
