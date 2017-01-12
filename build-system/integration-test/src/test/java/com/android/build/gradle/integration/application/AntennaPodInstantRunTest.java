/*
 * Copyright (C) 2016 The Android Open Source Project
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

import static com.android.testutils.truth.MoreTruth.assertThatDex;

import com.android.annotations.NonNull;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.RunGradleTasks;
import com.android.build.gradle.integration.common.utils.PerformanceTestProjects;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.build.gradle.integration.instant.InstantRunTestUtils;
import com.android.build.gradle.internal.incremental.ColdswapMode;
import com.android.builder.model.InstantRun;
import com.android.builder.model.OptionalCompilationStep;
import com.android.tools.fd.client.InstantRunArtifact;
import com.google.common.truth.Expect;
import java.io.File;
import java.io.IOException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class AntennaPodInstantRunTest {

    @Rule public Expect expect = Expect.createAndEnableStackTrace();

    @Rule
    public GradleTestProject mainProject =
            GradleTestProject.builder().fromExternalProject("AntennaPod").create();

    private GradleTestProject project;

    @Before
    public void setUp() throws IOException {
        project = mainProject.getSubproject("AntennaPod");
        PerformanceTestProjects.initializeAntennaPod(mainProject);
    }

    @Test
    public void buildAntennaPod() throws Exception {
        getExecutor().run("clean");
        InstantRun instantRunModel =
                InstantRunTestUtils.getInstantRunModel(
                        project.model().getMulti().getModelMap().get(":app"));

        getExecutor()
                .withInstantRun(23, ColdswapMode.MULTIAPK, OptionalCompilationStep.RESTART_ONLY)
                .run(":app:assembleDebug");

        // Test the incremental build
        makeHotSwapChange(1);
        getExecutor()
                .withInstantRun(23, ColdswapMode.MULTIAPK, OptionalCompilationStep.RESTART_ONLY)
                .run(":app:assembleDebug");

        makeHotSwapChange(100);

        getExecutor().withInstantRun(23, ColdswapMode.MULTIAPK).run("assembleDebug");

        InstantRunArtifact artifact = InstantRunTestUtils.getReloadDexArtifact(instantRunModel);

        assertThatDex(artifact.file)
                .containsClass("Lde/danoeh/antennapod/activity/MainActivity$override;")
                .that()
                .hasMethod("onStart");

        // Test cold swap
        makeColdSwapChange(100);

        getExecutor().withInstantRun(23, ColdswapMode.MULTIAPK).run(":app:assembleDebug");

        InstantRunTestUtils.getCompiledColdSwapChange(instantRunModel);
    }

    @NonNull
    private RunGradleTasks getExecutor() {
        return project.executor();
    }

    private void makeHotSwapChange(int i) throws IOException {
        TestFileUtils.searchAndReplace(
                project.file("app/src/main/java/de/danoeh/antennapod/activity/MainActivity.java"),
                "public void onStart\\(\\) \\{",
                "public void onStart() {\n" + "        Log.d(TAG, \"onStart called " + i + "\");");
    }

    private void makeColdSwapChange(int i) throws IOException {
        String newMethodName = "newMethod" + i;
        File mainActivity =
                project.file("app/src/main/java/de/danoeh/antennapod/activity/MainActivity.java");
        TestFileUtils.searchAndReplace(
                mainActivity,
                "public void onStart\\(\\) \\{",
                "public void onStart() {\n"
                        + "        " + newMethodName + "();");
        TestFileUtils.addMethod(
                mainActivity,
                "private void " + newMethodName + "() {\n"
                        + "        Log.d(TAG, \"" + newMethodName + " called\");\n"
                        + "    }\n");
    }
}
