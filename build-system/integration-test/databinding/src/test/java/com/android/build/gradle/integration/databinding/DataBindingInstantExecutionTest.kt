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

package com.android.build.gradle.integration.databinding

import com.android.build.gradle.integration.common.fixture.GradleTaskExecutor
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.truth.ScannerSubject
import org.junit.Rule
import org.junit.Test

class DataBindingInstantExecutionTest {

    @get:Rule
    val project = GradleTestProject.builder()
        .fromTestProject("databinding")
        .create()

    @Test
    fun testProjectInstanceAccessAtTaskExecution() {
        val result = executor().run("assembleDebug")
        result.stdout.use {
            ScannerSubject.assertThat(it)
                .doesNotContain("invocation of 'Task.project' at execution time is unsupported")
        }
    }

    private fun executor(): GradleTaskExecutor =
        project.executor()
            .withArgument("-Dorg.gradle.unsafe.instant-execution=true")
            .withArgument("-Dorg.gradle.unsafe.instant-execution.fail-on-problems=false")
}