/*
 * Copyright (C) 2018 The Android Open Source Project
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
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.testutils.truth.PathSubject.assertThat
import com.android.utils.FileUtils
import org.junit.Rule
import org.junit.Test
import java.io.IOException
import java.nio.file.Files
import kotlin.test.assertTrue

class PrivateResourcesTest {
    @get:Rule
    var project = GradleTestProject.builder()
        .fromTestProject("projectWithModules")
        .create()

    @Test
    fun noPrivateJavaForNonNamespacedCase() {
        TestFileUtils.appendToFile(
                project.getSubproject("app").buildFile,
                "android.aaptOptions.privateRDotJavaPackage \"com.foo.bar.symbols\"\n"
                        + "android.aaptOptions.namespaced = false\n")


        val result = project.executor().expectFailure().run(":app:assembleDebug")

        assertTrue(
                result.failureMessage!!.contains(
                        "Private R.java generation is currently only allowed with resource" +
                                " namespacing enabled."))
    }

    @Test
    @Throws(IOException::class, InterruptedException::class)
    fun checkPrivateRDotJavaGenerated() {
        TestFileUtils.appendToFile(
                project.getSubproject("app").buildFile,
                "android.aaptOptions.privateRDotJavaPackage \"com.foo.bar.symbols\"\n"
                        + "android.aaptOptions.namespaced = true\n")

        val stringsXml = FileUtils.join(
                project.getSubproject("app").mainSrcDir.parentFile,
                "res", "values", "strings.xml")
        TestFileUtils.searchAndReplace(
                stringsXml,
                "</resources>",
                "    <string name=\"public_string\">s1</string>\n" +
                "    <string name=\"private_string\">s2</string>\n" +
                "    <string name=\"default_string\">s3</string>\n" +
                "</resources>"
        )

        val publicXml = FileUtils.join(
                project.getSubproject("app").mainSrcDir.parentFile,
                "res", "values", "public.xml")
        FileUtils.writeToFile(
                publicXml,
                "<resources>\n" +
                "    <public type=\"layout\" name=\"main\"/>\n" +
                "    <public type=\"string\" name=\"public_string\"/>\n" +
                "</resources>")

        val privateXml = FileUtils.join(
                project.getSubproject("app").mainSrcDir.parentFile,
                "res", "values", "symbols.xml")
        FileUtils.writeToFile(
                privateXml,
                "<resources>\n" +
                "    <java-symbol type=\"string\" name=\"private_string\"/>\n" +
                "</resources>")

        project.executor().run(":app:assembleDebug")

        val publicR = FileUtils.join(
                project.getSubproject("app").generatedDir,
                "source", "final-r", "debug", "com", "example", "android", "multiproject", "R.java")
        assertThat(publicR).exists()

        val publicLines = Files.readAllLines(publicR.toPath())
        assertTrue(publicLines.any { it.contains("public_string")})
        assertTrue(publicLines.none { it.contains("private_string")})
        assertTrue(publicLines.none { it.contains("default_string")})

        val privateR = FileUtils.join(
                project.getSubproject("app").generatedDir,
                "source", "final-r", "debug", "com", "foo", "bar", "symbols", "R.java")
        assertThat(privateR).exists()

        val privateLines = Files.readAllLines(privateR.toPath())
        assertTrue(privateLines.any { it.contains("public_string")})
        assertTrue(privateLines.any { it.contains("private_string")})
        assertTrue(privateLines.none { it.contains("default_string")})

    }
}
