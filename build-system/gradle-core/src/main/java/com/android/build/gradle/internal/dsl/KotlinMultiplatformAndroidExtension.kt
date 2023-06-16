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

package com.android.build.gradle.internal.dsl

import com.android.build.api.dsl.AarMetadata
import com.android.build.api.dsl.ApkSigningConfig
import com.android.build.api.dsl.Installation
import com.android.build.api.dsl.Lint
import com.android.build.api.dsl.Packaging
import com.android.build.api.dsl.TestCoverage
import com.android.build.api.dsl.TestOptions
import com.android.build.api.variant.impl.KotlinMultiplatformAndroidVariant
import java.io.File

/**
 * Temporary interface to develop the kotlin multiplatform android plugin.
 *
 * TODO(b/267309622): Move to gradle-api
 */
interface KotlinMultiplatformAndroidExtension {
    var minSdk: Int?
    var minSdkPreview: String?
    var buildToolsVersion: String
    var namespace: String?

    fun useLibrary(name: String)
    fun useLibrary(name: String, required: Boolean)

    var compileSdk: Int?
    var compileSdkExtension: Int?
    var compileSdkPreview: String?

    val maxSdkVersion: Int?

    val experimentalProperties: MutableMap<String, Any>

    val dependencyVariantSelection: DependencyVariantSelection
    fun dependencyVariantSelection(action: DependencyVariantSelection.() -> Unit)

    val aarMetadata: AarMetadata

    val packagingOptions: Packaging

    val optimization: KmpOptimization

    fun optimization(action: KmpOptimization.() -> Unit)
    // test stuff. todo, combine to a test block

    var testNamespace: String?
    val testOptions: TestOptions

    var testInstrumentationRunner: String?

    val testInstrumentationRunnerArguments: MutableMap<String, String>

    var testHandleProfiling: Boolean?

    var testFunctionalTest: Boolean?

    var isTestMultiDexEnabled: Boolean?
    var testMultiDexKeepProguard: File?
    var isCoreLibraryDesugaringEnabled: Boolean

    val installation: Installation

    // should this be here?
    fun testSigningConfig(action: ApkSigningConfig.() -> Unit)

    var testTargetSdk: Int?
    var testTargetSdkPreview: String?

    fun withAndroidTestOnJvm(
        compilationName: String = "testOnJvm",
        action: KotlinMultiplatformAndroidTestOnJvmConfiguration.() -> Unit = {}
    )

    fun withAndroidTestOnDevice(
        compilationName: String = "testOnDevice",
        action: KotlinMultiplatformAndroidTestOnDeviceConfiguration.() -> Unit = {}
    )

    var enableUnitTestCoverage: Boolean
    var enableInstrumentedTestCoverage: Boolean

    val testCoverage: TestCoverage


    val lint: Lint
    fun lint(action: Lint.() -> Unit)

    fun onVariant(
        callback: KotlinMultiplatformAndroidVariant.() -> Unit
    )
}

interface KotlinMultiplatformAndroidTestConfiguration {

    /**
     * By default, this will be equal to the compilation name specified when enabling the test
     * component prefixed by "android". (i.e. when the compilation name is "testOnJvm" then the
     * default sourceSet name will be "androidTestOnJvm").
     */
    var defaultSourceSetName: String
}

interface KotlinMultiplatformAndroidTestOnJvmConfiguration: KotlinMultiplatformAndroidTestConfiguration
interface KotlinMultiplatformAndroidTestOnDeviceConfiguration: KotlinMultiplatformAndroidTestConfiguration
