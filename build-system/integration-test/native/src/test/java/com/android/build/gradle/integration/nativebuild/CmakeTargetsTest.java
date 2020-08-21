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

package com.android.build.gradle.integration.nativebuild;

import static com.android.build.gradle.integration.common.fixture.GradleTestProject.DEFAULT_NDK_SIDE_BY_SIDE_VERSION;
import static com.android.build.gradle.integration.common.fixture.model.NativeUtilsKt.dump;
import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat;
import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThatApk;
import static com.android.build.gradle.internal.cxx.configure.CmakeLocatorKt.DEFAULT_CMAKE_SDK_DOWNLOAD_VERSION;
import static com.android.testutils.truth.PathSubject.assertThat;

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.ModelBuilderV2;
import com.android.build.gradle.integration.common.fixture.ModelContainerV2;
import com.android.build.gradle.integration.common.fixture.app.HelloWorldJniApp;
import com.android.build.gradle.integration.common.utils.NdkHelper;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.build.gradle.options.BooleanOption;
import com.android.build.gradle.tasks.NativeBuildSystem;
import com.android.builder.model.NativeAndroidProject;
import com.android.builder.model.NativeArtifact;
import com.android.builder.model.v2.models.ndk.NativeModule;
import com.android.testutils.apk.Apk;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Multimap;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/** Assemble tests for CMake with targets clause. */
@RunWith(Parameterized.class)
public class CmakeTargetsTest {
    private final boolean useV2NativeModel;

    @Rule public GradleTestProject project;

    @Parameterized.Parameters(name = "useV2NativeModel={0}")
    public static Collection<Object[]> data() {
        return ImmutableList.of(new Object[] {false}, new Object[] {true});
    }

    public CmakeTargetsTest(boolean useV2NativeModel) {
        this.useV2NativeModel = useV2NativeModel;
        project =
                GradleTestProject.builder()
                        .fromTestApp(HelloWorldJniApp.builder().withNativeDir("cpp").build())
                        .addFile(HelloWorldJniApp.cmakeListsMultiModule("."))
                        .addFile(
                                HelloWorldJniApp.libraryCpp(
                                        "src/main/cpp/library1", "library1.cpp"))
                        .addFile(
                                HelloWorldJniApp.libraryCpp(
                                        "src/main/cpp/library2", "library2.cpp"))
                        .setCmakeVersion(DEFAULT_CMAKE_SDK_DOWNLOAD_VERSION)
                        .setSideBySideNdkVersion(DEFAULT_NDK_SIDE_BY_SIDE_VERSION)
                        .setWithCmakeDirInLocalProp(true)
                        .addGradleProperties(
                                BooleanOption.ENABLE_V2_NATIVE_MODEL.getPropertyName()
                                        + "="
                                        + useV2NativeModel)
                        .create();
    }

    @Before
    public void setUp() throws IOException {
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                "apply plugin: 'com.android.application'\n"
                        + "\n"
                        + "android {\n"
                        + "    compileSdkVersion "
                        + GradleTestProject.DEFAULT_COMPILE_SDK_VERSION
                        + "\n"
                        + "    buildToolsVersion \""
                        + GradleTestProject.DEFAULT_BUILD_TOOL_VERSION
                        + "\"\n"
                        + "    externalNativeBuild {\n"
                        + "      cmake {\n"
                        + "        path \"CMakeLists.txt\"\n"
                        + "      }\n"
                        + "    }\n"
                        + "}\n"
                        + "\n");
    }

    @Test
    public void checkMultiTargets() throws IOException {
        project.execute("clean", "assembleDebug");

        Apk apk = project.getApk(GradleTestProject.ApkType.DEBUG);
        assertThatApk(apk).hasVersionCode(1);
        assertThatApk(apk).contains("lib/armeabi-v7a/liblibrary1.so");
        assertThatApk(apk).contains("lib/x86/liblibrary1.so");
        assertThatApk(apk).contains("lib/x86_64/liblibrary1.so");
        assertThatApk(apk).contains("lib/armeabi-v7a/liblibrary2.so");
        assertThatApk(apk).contains("lib/x86/liblibrary2.so");
        assertThatApk(apk).contains("lib/x86_64/liblibrary2.so");

        if (useV2NativeModel) {
            checkV2Model();
        } else {
            project.model().fetchAndroidProjectsAllowSyncIssues();
            assertModel(project, project.model().fetch(NativeAndroidProject.class));
        }
    }

    @Test
    public void checkSingleTarget() throws IOException {
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                "android {\n"
                        + "    defaultConfig {\n"
                        + "      externalNativeBuild {\n"
                        + "          cmake {\n"
                        + "            targets.addAll(\"library2\")\n"
                        + "          }\n"
                        + "      }\n"
                        + "    }\n"
                        + "}\n");

        project.execute("clean", "assembleDebug");

        Apk apk = project.getApk(GradleTestProject.ApkType.DEBUG);
        assertThatApk(apk).hasVersionCode(1);
        assertThatApk(apk).doesNotContain("lib/armeabi-v7a/liblibrary1.so");
        assertThatApk(apk).doesNotContain("lib/armeabi/liblibrary1.so");
        assertThatApk(apk).doesNotContain("lib/x86/liblibrary1.so");
        assertThatApk(apk).doesNotContain("lib/x86_64/liblibrary1.so");
        assertThatApk(apk).contains("lib/armeabi-v7a/liblibrary2.so");
        assertThatApk(apk).contains("lib/x86/liblibrary2.so");
        assertThatApk(apk).contains("lib/x86_64/liblibrary2.so");

        if (useV2NativeModel) {
            checkV2Model();
        } else {
            project.model().fetchAndroidProjectsAllowSyncIssues();
            assertModel(project, project.model().fetch(NativeAndroidProject.class));
        }
    }

    private void checkV2Model() {
        ModelBuilderV2.FetchResult<ModelContainerV2<NativeModule>> result =
                project.modelV2().fetchNativeModules(ImmutableList.of(), ImmutableList.of());
        assertThat(dump(result))
                .isEqualTo(
                        "[:]\n"
                                + "> NativeModule:\n"
                                + "    - name                    = \"project\"\n"
                                + "    > variants:\n"
                                + "       * NativeVariant:\n"
                                + "          * name = \"debug\"\n"
                                + "          > abis:\n"
                                + "             * NativeAbi:\n"
                                + "                * name                  = \"armeabi-v7a\"\n"
                                + "                * sourceFlagsFile       = {PROJECT}/.cxx/cmake/debug/armeabi-v7a/compile_commands.json.bin{F}\n"
                                + "                * symbolFolderIndexFile = {PROJECT}/.cxx/cmake/debug/armeabi-v7a/symbol_folder_index.txt{F}\n"
                                + "                * buildFileIndexFile    = {PROJECT}/.cxx/cmake/debug/armeabi-v7a/build_file_index.txt{F}\n"
                                + "             * NativeAbi:\n"
                                + "                * name                  = \"arm64-v8a\"\n"
                                + "                * sourceFlagsFile       = {PROJECT}/.cxx/cmake/debug/arm64-v8a/compile_commands.json.bin{F}\n"
                                + "                * symbolFolderIndexFile = {PROJECT}/.cxx/cmake/debug/arm64-v8a/symbol_folder_index.txt{F}\n"
                                + "                * buildFileIndexFile    = {PROJECT}/.cxx/cmake/debug/arm64-v8a/build_file_index.txt{F}\n"
                                + "             * NativeAbi:\n"
                                + "                * name                  = \"x86\"\n"
                                + "                * sourceFlagsFile       = {PROJECT}/.cxx/cmake/debug/x86/compile_commands.json.bin{F}\n"
                                + "                * symbolFolderIndexFile = {PROJECT}/.cxx/cmake/debug/x86/symbol_folder_index.txt{F}\n"
                                + "                * buildFileIndexFile    = {PROJECT}/.cxx/cmake/debug/x86/build_file_index.txt{F}\n"
                                + "             * NativeAbi:\n"
                                + "                * name                  = \"x86_64\"\n"
                                + "                * sourceFlagsFile       = {PROJECT}/.cxx/cmake/debug/x86_64/compile_commands.json.bin{F}\n"
                                + "                * symbolFolderIndexFile = {PROJECT}/.cxx/cmake/debug/x86_64/symbol_folder_index.txt{F}\n"
                                + "                * buildFileIndexFile    = {PROJECT}/.cxx/cmake/debug/x86_64/build_file_index.txt{F}\n"
                                + "          < abis\n"
                                + "       * NativeVariant:\n"
                                + "          * name = \"release\"\n"
                                + "          > abis:\n"
                                + "             * NativeAbi:\n"
                                + "                * name                  = \"armeabi-v7a\"\n"
                                + "                * sourceFlagsFile       = {PROJECT}/.cxx/cmake/release/armeabi-v7a/compile_commands.json.bin{!}\n"
                                + "                * symbolFolderIndexFile = {PROJECT}/.cxx/cmake/release/armeabi-v7a/symbol_folder_index.txt{!}\n"
                                + "                * buildFileIndexFile    = {PROJECT}/.cxx/cmake/release/armeabi-v7a/build_file_index.txt{!}\n"
                                + "             * NativeAbi:\n"
                                + "                * name                  = \"arm64-v8a\"\n"
                                + "                * sourceFlagsFile       = {PROJECT}/.cxx/cmake/release/arm64-v8a/compile_commands.json.bin{!}\n"
                                + "                * symbolFolderIndexFile = {PROJECT}/.cxx/cmake/release/arm64-v8a/symbol_folder_index.txt{!}\n"
                                + "                * buildFileIndexFile    = {PROJECT}/.cxx/cmake/release/arm64-v8a/build_file_index.txt{!}\n"
                                + "             * NativeAbi:\n"
                                + "                * name                  = \"x86\"\n"
                                + "                * sourceFlagsFile       = {PROJECT}/.cxx/cmake/release/x86/compile_commands.json.bin{!}\n"
                                + "                * symbolFolderIndexFile = {PROJECT}/.cxx/cmake/release/x86/symbol_folder_index.txt{!}\n"
                                + "                * buildFileIndexFile    = {PROJECT}/.cxx/cmake/release/x86/build_file_index.txt{!}\n"
                                + "             * NativeAbi:\n"
                                + "                * name                  = \"x86_64\"\n"
                                + "                * sourceFlagsFile       = {PROJECT}/.cxx/cmake/release/x86_64/compile_commands.json.bin{!}\n"
                                + "                * symbolFolderIndexFile = {PROJECT}/.cxx/cmake/release/x86_64/symbol_folder_index.txt{!}\n"
                                + "                * buildFileIndexFile    = {PROJECT}/.cxx/cmake/release/x86_64/build_file_index.txt{!}\n"
                                + "          < abis\n"
                                + "    < variants\n"
                                + "    - nativeBuildSystem       = CMAKE\n"
                                + "    - ndkVersion              = \"{DEFAULT_NDK_VERSION}\"\n"
                                + "    - defaultNdkVersion       = \"{DEFAULT_NDK_VERSION}\"\n"
                                + "    - externalNativeBuildFile = {PROJECT}/CMakeLists.txt{F}\n"
                                + "< NativeModule");
    }

    private static void assertModel(GradleTestProject project, NativeAndroidProject model) {
        assertThat(model.getBuildSystems()).containsExactly(NativeBuildSystem.CMAKE.getTag());
        assertThat(model).hasExactBuildFilesShortNames("CMakeLists.txt");
        assertThat(model.getName()).isEqualTo("project");
        assertThat(model.getArtifacts())
                .hasSize(NdkHelper.getNdkInfo(project).getDefaultAbis().size() * 4);
        assertThat(model.getFileExtensions()).hasSize(1);

        for (File file : model.getBuildFiles()) {
            assertThat(file).isFile();
        }

        Multimap<String, NativeArtifact> groupToArtifacts = ArrayListMultimap.create();

        for (NativeArtifact artifact : model.getArtifacts()) {
            List<String> pathElements = TestFileUtils.splitPath(artifact.getOutputFile());
            assertThat(pathElements).contains("obj");
            assertThat(pathElements).doesNotContain("lib");
            groupToArtifacts.put(artifact.getGroupName(), artifact);
        }

        assertThat(model).hasArtifactGroupsNamed("debug", "release");
        assertThat(model)
                .hasArtifactGroupsOfSize(NdkHelper.getNdkInfo(project).getDefaultAbis().size() * 2);
    }
}
