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

package com.android.build.gradle.integration.model

import com.android.build.gradle.integration.common.fixture.GradleTestProject.Companion.builder
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp
import com.android.build.gradle.integration.common.fixture.model.ModelComparator
import com.android.builder.model.v2.ide.SyncIssue
import org.gradle.api.JavaVersion
import org.junit.Rule
import org.junit.Test

class AppModelTest: ModelComparator() {

    @get:Rule
    val project = builder()
        .fromTestApp(HelloWorldApp.forPlugin("com.android.application"))
        .create()

    @Test
    fun `default AndroidProject model`() {
        val result = project.modelV2()
            .ignoreSyncIssues(SyncIssue.SEVERITY_WARNING)
            .fetchAndroidProjects()

        with(result).compare(
            model = result.container.singleModel,
            goldenFile = "Default_AndroidProject_Model"
        )
    }

    @Test
    fun `default VariantDependencies model`() {
        val result = project.modelV2()
            .ignoreSyncIssues(SyncIssue.SEVERITY_WARNING)
            .fetchVariantDependencies("debug")

        with(result).compare(
            model = result.container.singleModel,
            goldenFile = "Default_VariantDependencies_Model"
        )
    }
}