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

package com.android.build.gradle.integration.application

import com.android.build.gradle.integration.common.fixture.BaseGradleExecutor
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.MinimalSubProject
import com.android.build.gradle.integration.common.fixture.app.MultiModuleTestProject
import com.android.build.gradle.integration.common.truth.TruthHelper.assertThat
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.build.gradle.options.BooleanOption
import com.android.builder.model.SyncIssue
import com.google.common.truth.Truth
import org.junit.Rule
import org.junit.Test

class NoManifestTest {

    val app = MinimalSubProject.app("com.example.app")
    init {
        app.removeFileByName("AndroidManifest.xml")
    }
    private val testApp = MultiModuleTestProject.builder().subproject(":app", app).build()
    @get:Rule val project = GradleTestProject.builder().fromTestApp(testApp).create()

    @Test
    fun noManifestConfigurationPassesTest() {
        project.executor()
            // org.gradle.api.tasks.diagnostics.TaskReportTask is incompatible
            .withConfigurationCaching(BaseGradleExecutor.ConfigurationCaching.OFF)
            .with(BooleanOption.DISABLE_EARLY_MANIFEST_PARSING, true).run("tasks")
        // we should be able to create task list, without a valid manifest.
    }

    // FIXME: Revise this once appId is removed from the model
    //@Test
    fun noManifestSyncNoApplicationIdTest() {
        val issues = project.model().with(BooleanOption.DISABLE_EARLY_MANIFEST_PARSING, true).ignoreSyncIssues().fetchAndroidProjects().onlyModelSyncIssues
        Truth.assertThat(issues).named("full issues list").hasSize(2)

        val errors = issues.filter { it.severity == SyncIssue.SEVERITY_ERROR}
        Truth.assertThat(errors).named("error-only issues").hasSize(1)
        assertThat(errors.first()).hasType(SyncIssue.TYPE_GENERIC)

        val warnings = issues.filter { it.severity == SyncIssue.SEVERITY_WARNING }
        Truth.assertThat(errors).named("warning-only issues").hasSize(1)
        assertThat(warnings.first()).hasType(SyncIssue.TYPE_UNSUPPORTED_PROJECT_OPTION_USE)
    }

    @Test
    fun noManifestSyncWithApplicationIdsTest() {
        TestFileUtils.appendToFile(
            project.getSubproject(":app").buildFile, """
                android.defaultConfig {
                    applicationId "com.example.app"
                    testApplicationId "com.example.app.test"
                }
            """.trimIndent()
        )
        val issues = project.model().with(BooleanOption.DISABLE_EARLY_MANIFEST_PARSING, true).ignoreSyncIssues().fetchAndroidProjects().onlyModelSyncIssues

        Truth.assertThat(issues).named("full issues list").hasSize(1)
        val issue = issues.first()
        assertThat(issue).hasType(SyncIssue.TYPE_UNSUPPORTED_PROJECT_OPTION_USE)
        assertThat(issue).hasSeverity(SyncIssue.SEVERITY_WARNING)
    }

    @Test
    fun noManifestSyncWithNamespaceTest() {
        TestFileUtils.appendToFile(
                project.getSubproject(":app").buildFile,
                """
                    android.namespace "com.example.app"
                    """.trimIndent()
        )
        val issues =
                project.model()
                        .with(BooleanOption.DISABLE_EARLY_MANIFEST_PARSING, true)
                        .ignoreSyncIssues()
                        .fetchAndroidProjects().onlyModelSyncIssues

        Truth.assertThat(issues).named("full issues list").hasSize(1)
        val issue = issues.first()
        assertThat(issue).hasType(SyncIssue.TYPE_UNSUPPORTED_PROJECT_OPTION_USE)
        assertThat(issue).hasSeverity(SyncIssue.SEVERITY_WARNING)
    }
}
