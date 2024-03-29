/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.build.gradle.integration.lint

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.MinimalSubProject
import com.android.build.gradle.integration.common.truth.GradleTaskSubject.assertThat
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.testutils.truth.PathSubject.assertThat
import org.junit.Rule
import org.junit.Test

/**
 * Integration test running lint on generated sources
 */
class LintGeneratedSourcesTest {

    @get:Rule
    val project: GradleTestProject =
        GradleTestProject.builder()
            .fromTestApp(
                MinimalSubProject.app("com.example.app")
                    .appendToBuild(
                        """
                            android {
                                buildFeatures {
                                    buildConfig true
                                }
                                lint {
                                    abortOnError = false
                                    textOutput = file("lint-results.txt")
                                    ignoreTestSources = true
                                    checkGeneratedSources = true
                                    enable 'StopShip'
                                }
                            }

                            // Add a STOPSHIP comment to a generated source file
                            androidComponents {
                                onVariants(selector().all(), { variant ->
                                    variant.buildConfigFields.put(
                                        "FOO",
                                        new com.android.build.api.variant.BuildConfigField(
                                            "String",
                                            "\"foo\"",
                                            "STOPSHIP"
                                        )
                                    )
                                })
                            }
                        """.trimIndent()
                    )
            ).create()

    /** Test that changes to generated sources cause the lint tasks to re-run as expected. */
    @Test
    fun testNotUpToDate() {
        project.executor().run("clean", "lintRelease")
        assertThat(project.buildResult.getTask(":lintReportRelease")).didWork()
        assertThat(project.buildResult.getTask(":lintAnalyzeRelease")).didWork()
        val lintReport = project.file("lint-results.txt")
        assertThat(lintReport).exists()
        assertThat(lintReport).contains("StopShip")

        TestFileUtils.searchAndReplace(project.buildFile, "STOPSHIP", "comment")
        project.executor().run("lintRelease")
        assertThat(project.buildResult.getTask(":lintReportRelease")).didWork()
        assertThat(project.buildResult.getTask(":lintAnalyzeRelease")).didWork()
        assertThat(lintReport).exists()
        assertThat(lintReport).doesNotContain("StopShip")
    }
}
