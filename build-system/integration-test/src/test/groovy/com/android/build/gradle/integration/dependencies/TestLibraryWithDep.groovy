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

package com.android.build.gradle.integration.dependencies

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import groovy.transform.CompileStatic
import org.junit.BeforeClass
import org.junit.ClassRule
import org.junit.Test

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThatApk

/**
 * Created by jedo on 1/16/15.
 */
@CompileStatic
class TestLibraryWithDep {

    @ClassRule
    static public GradleTestProject project = GradleTestProject.builder()
            .fromTestProject("libTestDep")
            .create()

    @BeforeClass
    static void setUp() {
        project.executeAndReturnMultiModel("clean", "assembleDebugAndroidTest")
    }

    @Test
    void "check lib dependency jar is packaged"() {
        assertThatApk(project.getApk("debug", "androidTest", "unaligned"))
                .containsClass("Lcom/google/common/base/Splitter;")
    }
}

