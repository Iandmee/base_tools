/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.build.gradle.integration.application

import com.android.build.gradle.integration.common.fixture.BaseGradleExecutor
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.MinimalSubProject
import com.android.build.gradle.integration.common.runner.FilterableParameterized
import com.google.common.truth.Truth
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(FilterableParameterized::class)
class ProjectNoJavaSourcesTest(testProject: MinimalSubProject) {

    companion object {
        @Parameterized.Parameters
        @JvmStatic
        fun parameters() =
            listOf(MinimalSubProject.app("com.test"), MinimalSubProject.lib("com.test"))
    }

    @get: Rule
    val project = GradleTestProject.builder().fromTestApp(
        testProject.appendToBuild(
            """
            android.buildFeatures.buildConfig = false
        """.trimIndent()
        )
    ).also {
        if (testProject.plugin == "com.android.application") {
            // http://b/149978740 and http://b/146208910
            it.withConfigurationCaching(BaseGradleExecutor.ConfigurationCaching.OFF)
        }
    }.create()

    @Test
    fun testBuild() {
        project.executor().run("assemble", "assembleDebugAndroidTest")
        Truth.assertThat(project.testDir.walk().filter { it.extension == "java" }
            .toList()).named("list of Java sources").isEmpty()
    }

    @Test
    fun testMinified() {
        project.buildFile.appendText(
            """
            android {
              buildTypes {
                debug {
                  minifyEnabled true
                }
              }
            }
        """.trimIndent()
        )
        project.executor().run("assemble", "assembleDebugAndroidTest")
        Truth.assertThat(project.testDir.walk().filter { it.extension == "java" }
            .toList()).named("list of Java sources").isEmpty()
    }

    @Test
    fun testLegacyMultidex() {
        project.buildFile.appendText(
            """
            android {
              defaultConfig {
                minSdkVersion 19
                multiDexEnabled true
              }
            }
        """.trimIndent()
        )
        project.executor().run("assemble", "assembleDebugAndroidTest")
        Truth.assertThat(project.testDir.walk().filter { it.extension == "java" }
            .toList()).named("list of Java sources").isEmpty()
    }
}
