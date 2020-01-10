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

package com.android.build.gradle.integration.ndk

import com.android.SdkConstants.ABI_ARMEABI_V7A
import com.android.SdkConstants.ABI_INTEL_ATOM
import com.android.SdkConstants.ABI_INTEL_ATOM64
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.GradleTestProject.DEFAULT_NDK_SIDE_BY_SIDE_VERSION
import com.android.build.gradle.integration.common.runner.FilterableParameterized
import com.android.build.gradle.integration.common.utils.getOutputByName
import com.android.build.gradle.integration.common.utils.getVariantByName
import com.android.build.gradle.internal.dsl.NdkOptions.DebugSymbolLevel
import com.android.build.gradle.internal.dsl.NdkOptions.DebugSymbolLevel.FULL
import com.android.build.gradle.internal.dsl.NdkOptions.DebugSymbolLevel.NONE
import com.android.build.gradle.internal.dsl.NdkOptions.DebugSymbolLevel.SYMBOL_TABLE
import com.android.build.gradle.internal.tasks.ExtractNativeDebugMetadataTask
import com.android.build.gradle.internal.tasks.PackageBundleTask
import com.android.builder.model.AppBundleProjectBuildOutput
import com.android.builder.model.AppBundleVariantBuildOutput
import com.android.testutils.apk.Zip
import com.android.testutils.truth.FileSubject.assertThat
import com.android.utils.FileUtils
import com.google.common.base.Throwables
import com.google.common.truth.Truth.assertThat
import org.gradle.tooling.BuildException
import org.junit.Assume
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.File
import kotlin.test.fail

/** Test behavior of [ExtractNativeDebugMetadataTask] and [PackageBundleTask]*/
@RunWith(FilterableParameterized::class)
class ExtractNativeDebugMetadataTaskTest(private val debugSymbolLevel: DebugSymbolLevel?) {

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "debugSymbolLevel_{0}")
        fun params() = listOf(null, NONE, SYMBOL_TABLE, FULL)
    }

    @get:Rule
    val project: GradleTestProject =
        GradleTestProject.builder()
            .fromTestProject("dynamicApp")
            .setSideBySideNdkVersion(DEFAULT_NDK_SIDE_BY_SIDE_VERSION)
            .create()

    @Before
    fun setUp() {
        // use lowercase and uppercase for different cases because both are supported
        val debugSymbolLevel =
            when (debugSymbolLevel) {
                null -> return
                NONE -> debugSymbolLevel.name
                SYMBOL_TABLE -> debugSymbolLevel.name.toLowerCase()
                FULL -> debugSymbolLevel.name.toUpperCase()
            }
        project.getSubproject(":app").buildFile.appendText(
            """
                android.buildTypes.release.ndk.debugSymbolLevel '$debugSymbolLevel'
                """.trimIndent()
        )
    }

    @Test
    fun testNativeDebugMetadataInBundle() {
        // add native libs to app and feature modules
        listOf("app", "feature1", "feature2").forEach {
            val subProject = project.getSubproject(":$it")
            createAbiFile(subProject, ABI_ARMEABI_V7A, "$it.so")
            createAbiFile(subProject, ABI_INTEL_ATOM, "$it.so")
            createAbiFile(subProject, ABI_INTEL_ATOM64, "$it.so")
        }

        val bundleTaskName = getBundleTaskName("release")
        project.executor().run("app:$bundleTaskName")

        val bundleFile = getApkFolderOutput("release").bundleFile
        assertThat(bundleFile).exists()

        val bundleEntryPrefix = "/BUNDLE-METADATA/com.android.tools.build.debugsymbols"
        val expectedFullEntries = listOf(
            "$bundleEntryPrefix/$ABI_ARMEABI_V7A/app.so.dbg",
            "$bundleEntryPrefix/$ABI_ARMEABI_V7A/feature1.so.dbg",
            "$bundleEntryPrefix/$ABI_ARMEABI_V7A/feature2.so.dbg",
            "$bundleEntryPrefix/$ABI_INTEL_ATOM/app.so.dbg",
            "$bundleEntryPrefix/$ABI_INTEL_ATOM/feature1.so.dbg",
            "$bundleEntryPrefix/$ABI_INTEL_ATOM/feature2.so.dbg",
            "$bundleEntryPrefix/$ABI_INTEL_ATOM64/app.so.dbg",
            "$bundleEntryPrefix/$ABI_INTEL_ATOM64/feature1.so.dbg",
            "$bundleEntryPrefix/$ABI_INTEL_ATOM64/feature2.so.dbg"
        )
        val expectedSymbolTableEntries = listOf(
            "$bundleEntryPrefix/$ABI_ARMEABI_V7A/app.so.sym",
            "$bundleEntryPrefix/$ABI_ARMEABI_V7A/feature1.so.sym",
            "$bundleEntryPrefix/$ABI_ARMEABI_V7A/feature2.so.sym",
            "$bundleEntryPrefix/$ABI_INTEL_ATOM/app.so.sym",
            "$bundleEntryPrefix/$ABI_INTEL_ATOM/feature1.so.sym",
            "$bundleEntryPrefix/$ABI_INTEL_ATOM/feature2.so.sym",
            "$bundleEntryPrefix/$ABI_INTEL_ATOM64/app.so.sym",
            "$bundleEntryPrefix/$ABI_INTEL_ATOM64/feature1.so.sym",
            "$bundleEntryPrefix/$ABI_INTEL_ATOM64/feature2.so.sym"
        )
        Zip(bundleFile).use { zip ->
            when (debugSymbolLevel) {
                null -> {
                    assertThat(zip.entries.map { it.toString() })
                        .containsNoneIn(expectedFullEntries)
                    assertThat(zip.entries.map { it.toString() })
                        .containsNoneIn(expectedSymbolTableEntries)
                }
                NONE -> {
                    assertThat(zip.entries.map { it.toString() })
                        .containsNoneIn(expectedFullEntries)
                    assertThat(zip.entries.map { it.toString() })
                        .containsNoneIn(expectedSymbolTableEntries)
                }
                SYMBOL_TABLE -> {
                    assertThat(zip.entries.map { it.toString() })
                        .containsNoneIn(expectedFullEntries)
                    assertThat(zip.entries.map { it.toString() })
                        .containsAtLeastElementsIn(expectedSymbolTableEntries)
                }
                FULL -> {
                    assertThat(zip.entries.map { it.toString() })
                        .containsAtLeastElementsIn(expectedFullEntries)
                    assertThat(zip.entries.map { it.toString() })
                        .containsNoneIn(expectedSymbolTableEntries)
                }
            }
        }
    }

    @Test
    fun testErrorIfCollidingNativeLibs() {
        Assume.assumeTrue(debugSymbolLevel == SYMBOL_TABLE || debugSymbolLevel == FULL)
        // add native libs to app and feature modules
        listOf("app", "feature1").forEach {
            val subProject = project.getSubproject(":$it")
            createAbiFile(subProject, ABI_ARMEABI_V7A, "collide.so")
        }

        val bundleTaskName = getBundleTaskName("release")
        try {
            project.executor().run("app:$bundleTaskName")
        } catch (e: BuildException) {
            assertThat(Throwables.getRootCause(e).message).startsWith(
                "Multiple entries with same key"
            )
            return
        }
        fail("expected build error because of native libraries with same name.")
    }

    @Test
    fun testTaskSkippedWhenNoNativeLibs() {
        // first test that the task is skipped in all modules when there are no native libraries.
        val bundleTaskName = getBundleTaskName("release")
        val result1 = project.executor().run("app:$bundleTaskName")
        // if mode is NONE or null, the task should not be part of the task graph at all.
        if (debugSymbolLevel == null || debugSymbolLevel == NONE) {
            assertThat(result1.tasks).containsNoneIn(
                listOf(
                    ":app:extractReleaseNativeDebugMetadata",
                    ":feature1:extractReleaseNativeDebugMetadata",
                    ":feature2:extractReleaseNativeDebugMetadata"
                )
            )
            return
        }
        // otherwise, the task should be skipped in all modules
        assertThat(result1.skippedTasks).containsAtLeastElementsIn(
            listOf(
                ":app:extractReleaseNativeDebugMetadata",
                ":feature1:extractReleaseNativeDebugMetadata",
                ":feature2:extractReleaseNativeDebugMetadata"
            )
        )
        // then test that the task only does work for modules with native libraries.
        createAbiFile(project.getSubproject(":feature1"), ABI_ARMEABI_V7A, "feature1.so")
        val result2 = project.executor().run("app:$bundleTaskName")
        assertThat(result2.skippedTasks).containsAtLeastElementsIn(
            listOf(
                ":app:extractReleaseNativeDebugMetadata",
                ":feature2:extractReleaseNativeDebugMetadata"
            )
        )
        assertThat(result2.didWorkTasks).containsAtLeastElementsIn(
            listOf(":feature1:extractReleaseNativeDebugMetadata")
        )
    }

    @Test
    fun testErrorWhenInvalidString() {
        Assume.assumeTrue(debugSymbolLevel == null)
        project.getSubproject(":app").buildFile.appendText(
            """
                android.defaultConfig.ndk.debugSymbolLevel 'INVALID'
                """.trimIndent()
        )
        try {
            project.executor().run("app:assembleDebug")
        } catch (e: Exception) {
            assertThat(Throwables.getRootCause(e).message).startsWith(
                "Unknown DebugSymbolLevel value 'INVALID'. Possible values are 'full', " +
                        "'symbol_table', 'none'."
            )
            return
        }
        fail("expected error because of invalid debugSymbolLevel value.")
    }

    private fun getBundleTaskName(name: String): String {
        // query the model to get the task name
        val syncModels = project.model().fetchAndroidProjectsAllowSyncIssues()
        val appModel =
            syncModels.rootBuildModelMap[":app"] ?: fail("Failed to get sync model for :app module")

        val debugArtifact = appModel.getVariantByName(name).mainArtifact
        return debugArtifact.bundleTaskName ?: fail("Module App does not have bundle task name")
    }

    private fun getApkFolderOutput(variantName: String): AppBundleVariantBuildOutput {
        val outputModels = project.model()
            .fetchContainer(AppBundleProjectBuildOutput::class.java)

        val outputAppModel = outputModels.rootBuildModelMap[":app"]
                ?: fail("Failed to get output model for :app module")

        return outputAppModel.getOutputByName(variantName)
    }

    private fun createAbiFile(
        project: GradleTestProject,
        abiName: String,
        libName: String
    ) {
        val abiFolder = File(project.getMainSrcDir("jniLibs"), abiName)
        FileUtils.mkdirs(abiFolder)
        ExtractNativeDebugMetadataTaskTest::class.java.getResourceAsStream(
            "/nativeLibs/libhello-jni.so"
        ).use { inputStream ->
            File(abiFolder, libName).outputStream().use { outputStream ->
                inputStream.copyTo(outputStream)
            }
        }
    }
}
