/*
 * Copyright (C) 2014 The Android Open Source Project
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

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp
import com.google.common.collect.Sets
import groovy.transform.CompileStatic
import org.junit.Before
import org.junit.Rule
import org.junit.Test

import java.util.regex.Matcher
import java.util.regex.Pattern

import static org.junit.Assert.assertTrue

@CompileStatic
/**
 * Tests to ensure that changing the build tools version in the build.gradle will trigger
 * re-execution of some tasks even if no source file change was detected.
 */
class BuildToolsTest {

    private static final Pattern UP_TO_DATE_PATTERN = ~/:(\S+)\s+UP-TO-DATE/

    private static final Pattern INPUT_CHANGED_PATTERN =
            ~/Value of input property 'buildToolsVersion' has changed for task ':(\S+)'/

    private static final String[] tasks = [
            "preDexDebug", "dexDebug", "compileDebugAidl", "compileDebugRenderscript",
            "mergeDebugResources", "processDebugResources",
            "preDexRelease", "dexRelease", "compileReleaseAidl", "compileReleaseRenderscript",
            "mergeReleaseResources", "processReleaseResources"
    ]

    @Rule
    public GradleTestProject project = GradleTestProject.builder()
            .fromTestApp(new HelloWorldApp())
            .captureStdOut(true)
            .create()

    @Before
    public void setUp() {
        project.getBuildFile() << """
apply plugin: 'com.android.application'

android {
    compileSdkVersion $GradleTestProject.DEFAULT_COMPILE_SDK_VERSION
    buildToolsVersion "$GradleTestProject.DEFAULT_BUILD_TOOL_VERSION"
}
"""
    }

    @Test
    public void nullBuild() {
        project.execute("assemble")
        project.stdout.reset()
        project.execute("assemble")

        Set<String> skippedTasks = getTasksMatching(UP_TO_DATE_PATTERN, project.stdout)
        for (String task : tasks) {
            assertTrue(String.format("Expecting task %s to be UP-TO-DATE" , task),
                    skippedTasks.contains(task))
        }
    }

    @Test
    public void invalidateBuildTools() {
        project.execute("assemble")
        // downgrade our build tools version to 19.1.0 unless it is already the current version,
        // in that case, upgrade to 20.0.0.
        // The point is, change the build tools version from what it was when the "assemble" task
        // was executed right before this comment.
        String newBuildToolVersion =
                GradleTestProject.DEFAULT_BUILD_TOOL_VERSION.contentEquals("19.1.0") ?
                        "20.0.0" : "19.1.0";

        project.getBuildFile() << """
apply plugin: 'com.android.application'

android {
    compileSdkVersion $GradleTestProject.DEFAULT_COMPILE_SDK_VERSION
    buildToolsVersion '$newBuildToolVersion'
}
"""

        project.stdout.reset()
        project.execute("assemble")
        Set<String> affectedTasks = getTasksMatching(INPUT_CHANGED_PATTERN, project.stdout)
        for (String task : tasks) {
            assertTrue(String.format("Expecting task %s to be invalidated", task),
                    affectedTasks.contains(task))
        }
    }

    private static Set<String> getTasksMatching(Pattern pattern, ByteArrayOutputStream output) {
        Set<String> result = Sets.newHashSet()
        Matcher matcher = (output.toString("UTF-8") =~ pattern)
        while (matcher.find()) {
            result.add(matcher.group(1))
        }
        result
    }
}
