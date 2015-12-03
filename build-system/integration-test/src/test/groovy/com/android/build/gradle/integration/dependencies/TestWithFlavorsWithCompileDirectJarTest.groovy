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



package com.android.build.gradle.integration.dependencies
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.utils.ModelHelper
import com.android.builder.model.AndroidArtifact
import com.android.builder.model.AndroidProject
import com.android.builder.model.Dependencies
import com.android.builder.model.Variant
import groovy.transform.CompileStatic
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.ClassRule
import org.junit.Test

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThatApk
import static com.android.build.gradle.integration.common.utils.ModelHelper.getAndroidArtifact
import static com.android.builder.model.AndroidProject.ARTIFACT_ANDROID_TEST
import static org.junit.Assert.assertEquals
import static org.junit.Assert.assertNotNull
/**
 * test for compile jar in a test app
 */
@CompileStatic
class TestWithFlavorsWithCompileDirectJarTest {

    @ClassRule
    static public GradleTestProject project = GradleTestProject.builder()
            .fromTestProject("projectWithModules")
            .create()
    static Map<String, AndroidProject> models

    @BeforeClass
    static void setUp() {
        project.getSubproject('app').getBuildFile() <<
                "\n" +
                "\n" +
                "android {\n" +
                "    productFlavors {\n" +
                "      pro { }\n" +
                "      free { }\n" +
                "    }\n" +
                "}\n" +
                "\n" +
                "dependencies {\n" +
                "    androidTestCompile project(':jar')\n" +
                "}\n"
        models = project.executeAndReturnMultiModel("clean", ":app:assembleFreeDebugAndroidTest")
    }

    @AfterClass
    static void cleanUp() {
        project = null
        models = null
    }

    @Test
    void "check compiled jar is packaged"() {
        assertThatApk(project.getSubproject('app').getApk("free", "debug", "androidTest", "unaligned"))
                .containsClass("Lcom/example/android/multiproject/person/People;")
    }

    @Test
    void "check compiled jar is in the test artifact model"() {
        Variant variant = ModelHelper.getVariant(models.get(':app').getVariants(), "freeDebug")

        Collection<AndroidArtifact> androidArtifacts = variant.getExtraAndroidArtifacts()
        AndroidArtifact testArtifact = getAndroidArtifact(androidArtifacts, ARTIFACT_ANDROID_TEST)
        assertNotNull(testArtifact)

        Dependencies deps = testArtifact.getDependencies()
        Collection<String> projectDeps = deps.getProjects()

        assertEquals("Check there is 1 dependency", 1, projectDeps.size())
    }
}
