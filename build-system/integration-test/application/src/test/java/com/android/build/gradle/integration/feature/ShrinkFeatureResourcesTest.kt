/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.build.gradle.integration.feature

import com.android.testutils.truth.PathSubject.assertThat

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.build.gradle.options.BooleanOption
import org.junit.Rule
import org.junit.Test

class ShrinkFeatureResourcesTest {
    @JvmField @Rule
    var project : GradleTestProject =
            GradleTestProject.builder()
                    .fromTestProject("singleFeature")
                    .withoutNdk()
                    .create()

    @Test
    fun build() {
        TestFileUtils.appendToFile(
                project.getSubproject(":feature").buildFile,
                "\n"
                        + "android {\n"
                        + "    buildTypes {\n"
                        + "        release {\n"
                        + "            minifyEnabled true\n"
                        + "            shrinkResources true\n"
                        + "        }\n"
                        + "    }\n"
                        + "}\n")

        // resource shrinker and R8 support missing - http://b/72370175
        project.executor().with(BooleanOption.ENABLE_R8, false).run("clean", "assembleRelease")
        val releaseApk = project.getSubproject(":feature").getFeatureApk(GradleTestProject.ApkType.RELEASE)
        assertThat(releaseApk.file).isFile()
    }
}