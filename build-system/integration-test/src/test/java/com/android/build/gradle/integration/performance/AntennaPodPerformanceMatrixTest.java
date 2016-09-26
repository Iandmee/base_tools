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

package com.android.build.gradle.integration.performance;

import com.android.annotations.NonNull;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.runner.FilterableParameterized;
import com.android.build.gradle.integration.common.utils.DexInProcessHelper;
import com.android.build.gradle.integration.common.utils.ModelHelper;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.build.gradle.integration.instant.InstantRunTestUtils;
import com.android.build.gradle.internal.incremental.ColdswapMode;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.InstantRun;
import com.android.utils.FileUtils;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import com.google.wireless.android.sdk.gradlelogging.proto.Logging;
import com.google.wireless.android.sdk.gradlelogging.proto.Logging.BenchmarkMode;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RunWith(FilterableParameterized.class)
public class AntennaPodPerformanceMatrixTest {

    private int nonce = 0;
    @Rule public final GradleTestProject mainProject;
    @NonNull private final ProjectScenario projectScenario;
    private GradleTestProject project;

    public AntennaPodPerformanceMatrixTest(@NonNull ProjectScenario scenario) {
        this.projectScenario = scenario;
        mainProject =
                GradleTestProject.builder()
                        .fromExternalProject("AntennaPod")
                        .forBenchmarkRecording(
                                new BenchmarkRecorder(Logging.Benchmark.ANTENNA_POD, scenario))
                        .create();
    }

    @Parameterized.Parameters(name = "{0}")
    public static Collection<Object[]> getParameters() {
        return Sets.cartesianProduct(ImmutableList.of(EnumSet.allOf(ProjectScenario.class)))
                .stream()
                .map(List::toArray)
                .collect(Collectors.toList());
    }

    @Before
    public void initializeProject() throws IOException {
        project = mainProject.getSubproject("AntennaPod");
        TestFileUtils.searchAndReplace(
                project.getBuildFile(),
                "classpath \"com\\.android\\.tools\\.build:gradle:\\d+.\\d+.\\d+\"",
                "classpath \"com.android.tools.build:gradle:"
                        + GradleTestProject.ANDROID_GRADLE_PLUGIN_VERSION
                        + '"');
        TestFileUtils.searchAndReplace(
                project.getBuildFile(),
                "jcenter\\(\\)",
                "maven { url '"
                        + FileUtils.toSystemIndependentPath(System.getenv("CUSTOM_REPO"))
                        + "'} \n"
                        + "        jcenter()");
        switch (projectScenario) {
            case NORMAL:
                break;
            case DEX_OUT_OF_PROCESS:
                DexInProcessHelper.disableDexInProcess(project.file("app/build.gradle"));
                break;
            default:
                throw new IllegalArgumentException("Unknown project scenario" + projectScenario);
        }
    }

    @Test
    public void runBenchmarks() throws Exception {
        Map<String, AndroidProject> model = project.model().getMulti();

        project.executor()
                .withEnableInfoLogging(false)
                .run("assembleDebug", "assembleDebugAndroidTest");
        project.executor().withEnableInfoLogging(false).run("clean");

        for (BenchmarkMode benchmarkMode : PerformanceTestUtil.BENCHMARK_MODES) {
            InstantRun instantRunModel = null;
            List<String> tasks;
            boolean isEdit = false;

            switch (benchmarkMode) {
                case EVALUATION:
                case SYNC:
                    //TODO
                    continue;
                case BUILD__FROM_CLEAN:
                    tasks = ImmutableList.of(":app:assembleDebug");
                    break;
                case BUILD_INC__MAIN_PROJECT__JAVA__IMPLEMENTATION_CHANGE:
                case BUILD_INC__MAIN_PROJECT__JAVA__API_CHANGE:
                    tasks = ImmutableList.of(":app:assembleDebug");
                    // Initial build for incremental tasks
                    project.executor().withEnableInfoLogging(false).run(tasks);
                    isEdit = true;
                    break;
                case BUILD_INC__SUB_PROJECT__JAVA__IMPLEMENTATION_CHANGE:
                case BUILD_INC__SUB_PROJECT__JAVA__API_CHANGE:
                case BUILD_INC__MAIN_PROJECT__RES__EDIT:
                case BUILD_INC__MAIN_PROJECT__RES__ADD:
                case BUILD_INC__SUB_PROJECT__RES__EDIT:
                case BUILD_INC__SUB_PROJECT__RES__ADD:
                    //TODO
                    continue;
                case INSTANT_RUN_BUILD__FROM_CLEAN:
                    tasks = ImmutableList.of(":app:assembleDebug");
                    break;
                case INSTANT_RUN_BUILD__MAIN_PROJECT__JAVA__IMPLEMENTATION_CHANGE:
                case INSTANT_RUN_BUILD__MAIN_PROJECT__JAVA__API_CHANGE:
                    instantRunModel = InstantRunTestUtils.getInstantRunModel(model.get(":app"));
                    tasks = ImmutableList.of(":app:assembleDebug");
                    // Initial build for incremental instant run tasks
                    project.executor()
                            .withInstantRun(24, ColdswapMode.MULTIDEX)
                            .withEnableInfoLogging(false)
                            .run(tasks);
                    isEdit = true;
                    break;
                case INSTANT_RUN_BUILD__SUB_PROJECT__JAVA__IMPLEMENTATION_CHANGE:
                case INSTANT_RUN_BUILD__SUB_PROJECT__JAVA__API_CHANGE:
                case INSTANT_RUN_BUILD__MAIN_PROJECT__RES__EDIT:
                case INSTANT_RUN_BUILD__MAIN_PROJECT__RES__ADD:
                case INSTANT_RUN_BUILD__SUB_PROJECT__RES__EDIT:
                case INSTANT_RUN_BUILD__SUB_PROJECT__RES__ADD:
                    //TODO
                    continue;
                case BUILD_ANDROID_TESTS_FROM_CLEAN:
                    tasks = ImmutableList.of(":app:assembleDebugAndroidTest");
                    break;
                case BUILD_UNIT_TESTS_FROM_CLEAN:
                    tasks = ImmutableList.of(":app:assembleDebugUnitTest");
                    break;
                case GENERATE_SOURCES:
                    tasks = ModelHelper.getGenerateSourcesCommands(model);
                    break;
                case NO_OP:
                    // Do an initial build for NO_OP.
                    tasks = ImmutableList.of(":app:assembleDebug");
                    project.executor().withEnableInfoLogging(false).run(tasks);
                    break;
                case LINT_RUN:
                    continue; // TODO
                default:
                    throw new UnsupportedOperationException(benchmarkMode.toString());
            }

            if (isEdit) {
                doEdit(
                        PerformanceTestUtil.getSubProjectType(benchmarkMode),
                        PerformanceTestUtil.getEditType(benchmarkMode),
                        nonce++);
            }

            if (instantRunModel != null) {
                project.executor()
                        .withEnableInfoLogging(false)
                        .recordBenchmark(benchmarkMode)
                        .withInstantRun(24, ColdswapMode.MULTIDEX)
                        .run(tasks);

                InstantRunTestUtils.loadContext(instantRunModel).getVerifierStatus();
            } else {
                project.executor()
                        .withEnableInfoLogging(false)
                        .recordBenchmark(benchmarkMode)
                        .run(tasks);
            }
        }
    }

    private void doEdit(
            @NonNull PerformanceTestUtil.SubProjectType subProjectType,
            @NonNull PerformanceTestUtil.EditType editType,
            int nonce)
            throws IOException {

        if (subProjectType != PerformanceTestUtil.SubProjectType.MAIN_PROJECT) {
            throw new UnsupportedOperationException("TODO: Cannot edit non main project yet.");
        }
        switch (editType) {
            case JAVA__IMPLEMENTATION_CHANGE:
                TestFileUtils.searchAndReplace(
                        project.file(
                                "app/src/main/java/de/danoeh/antennapod/activity/MainActivity.java"),
                        "public void onStart\\(\\) \\{",
                        "public void onStart() {\n"
                                + "        Log.d(TAG, \"onStart called "
                                + nonce
                                + "\");");
                break;
            case JAVA__API_CHANGE:
                String newMethodName = "newMethod" + nonce;
                File mainActivity =
                        project.file(
                                "app/src/main/java/de/danoeh/antennapod/activity/MainActivity.java");
                TestFileUtils.searchAndReplace(
                        mainActivity,
                        "public void onStart\\(\\) \\{",
                        "public void onStart() {\n" + "        " + newMethodName + "();");
                TestFileUtils.addMethod(
                        mainActivity,
                        "private void "
                                + newMethodName
                                + "() {\n"
                                + "        Log.d(TAG, \""
                                + newMethodName
                                + " called\");\n"
                                + "    }\n");
                break;
            case RES__EDIT:
            case RES__ADD:
                throw new UnsupportedOperationException(
                        "TODO: Support '" + editType.toString() + "'");
            default:
                throw new UnsupportedOperationException(
                        "Don't know how to do '" + editType.toString() + "'.");
        }
    }
}
