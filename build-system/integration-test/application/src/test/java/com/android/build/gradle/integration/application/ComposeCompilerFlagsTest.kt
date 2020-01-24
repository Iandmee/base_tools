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

package com.android.build.gradle.integration.application

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.KotlinHelloWorldApp
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.build.gradle.internal.TaskManager
import com.android.builder.model.AndroidGradlePluginProjectFlags
import com.google.common.truth.Truth
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.IOException
import kotlin.test.assertNotNull

class ComposeCompilerFlagsTest {
    @get:Rule
    var project = GradleTestProject.builder()
        .fromTestApp(KotlinHelloWorldApp.forPlugin("com.android.application"))
        .create()

    @Before
    @Throws(IOException::class)
    fun setUpBuildFile() {
        TestFileUtils.appendToFile(
        project.buildFile, """
android {
    buildFeatures {
        compose true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "+"
    }
}
        """.trimIndent()
        )
    }

    @Test
    fun testFlags() {

        assertNotNull(project)
        val withModel = project.model().fetchAndroidProjects().onlyModelMap[":"]
        assertNotNull(withModel)
        assertThat(
            withModel.flags.booleanFlagMap[
                    AndroidGradlePluginProjectFlags.BooleanFlag.JETPACK_COMPOSE]
        ).isTrue()

        project.execute(listOf("--debug"),"clean", "prepareDebugKotlinCompileTask")
        val prepareTask = project.buildResult.findTask(":prepareDebugKotlinCompileTask")
        assertNotNull(prepareTask)
        assertThat(prepareTask.didWork()).isTrue()
    }
}
