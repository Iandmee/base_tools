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

package com.android.build.gradle.integration.application;

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat;

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.app.KotlinHelloWorldApp;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.utils.FileUtils;
import groovy.transform.CompileStatic;
import java.io.File;
import java.io.IOException;
import org.junit.AfterClass;
import org.junit.Assume;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;

/** Check that Jacoco runs for a Kotlin-based project. */
@CompileStatic
public class JacocoWithKotlinTest {
    @ClassRule
    public static GradleTestProject project =
            GradleTestProject.builder()
                    .fromTestApp(KotlinHelloWorldApp.forPlugin("com.android.application"))
                    .create();

    @Before
    public void skipJack() {
        Assume.assumeFalse("Not supported under Jack", GradleTestProject.USE_JACK);
    }

    @Before
    public void setUpBuildFile() throws IOException {
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                "android {\n"
                        + "  buildTypes {\n"
                        + "    debug {\n"
                        + "      testCoverageEnabled true\n"
                        + "    }\n"
                        + "  }\n"
                        + "}");
    }

    @AfterClass
    public static void cleanUp() {
        project = null;
    }

    @Test
    public void build() throws IOException, InterruptedException {
        project.execute("transformClassesWithJacocoForDebug");

        File javaFile =
                FileUtils.join(
                        project.getTestDir(),
                        "build",
                        "intermediates",
                        "transforms",
                        "jacoco",
                        "debug",
                        "folders",
                        "1",
                        "1",
                        "main",
                        "com",
                        "example",
                        "helloworld",
                        "HelloWorld.class");
        assertThat(javaFile).exists();
    }
}
