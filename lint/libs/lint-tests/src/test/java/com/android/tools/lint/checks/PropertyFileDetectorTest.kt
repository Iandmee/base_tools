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

package com.android.tools.lint.checks

import com.android.tools.lint.checks.PropertyFileDetector.Companion.suggestEscapes
import com.android.tools.lint.detector.api.Detector

class PropertyFileDetectorTest : AbstractCheckTest() {
    override fun getDetector(): Detector {
        return PropertyFileDetector()
    }

    fun testBasic() {
        val expected =
            """
            local.properties:11: Error: Windows file separators (\) and drive letter separators (':') must be escaped (\\) in property files; use C\:\\my\\path\\to\\sdk [PropertyEscape]
            windows.dir=C:\my\path\to\sdk
                         ~~~~~~~~~~~~~~
            local.properties:14: Error: Windows file separators (\) and drive letter separators (':') must be escaped (\\) in property files; use C\:\\Documents and Settings\\UserName\\Local Settings\\Application Data\\Android\\android-studio\\sdk [PropertyEscape]
            ok.sdk.dir=C:\\Documents and Settings\\UserName\\Local Settings\\Application Data\\Android\\android-studio\\sdk
                        ~
            2 errors, 0 warnings
            """

        lint().files(
            propertyFile(
                "local.properties",
                "" +
                    "## This file is automatically generated by Android Studio.\n" +
                    "# Do not modify this file -- YOUR CHANGES WILL BE ERASED!\n" +
                    "#\n" +
                    "# This file should *NOT* be checked into Version Control Systems,\n" +
                    "# as it contains information specific to your local configuration.\n" +
                    "#\n" +
                    "# Location of the SDK. This is only used by Gradle.\n" +
                    "# For customization when using a Version Control System, please read the\n" +
                    "# header note.\n" +
                    "sdk.dir=/Users/test/dev/sdks\n" +
                    "windows.dir=C:\\my\\path\\to\\sdk\n" +
                    "windows2.dir=C\\:\\\\my\\\\path\\\\to\\\\sdk\n" +
                    "not.a.path.prop=Hello \\my\\path\\to\\sdk\n" +
                    "ok.sdk.dir=C:\\\\Documents and Settings\\\\UserName\\\\Local Settings\\\\Application Data\\\\Android\\\\android-studio\\\\sdk\n"
            )
        ).run().expect(expected).expectFixDiffs(
            "" +
                "Fix for local.properties line 11: Escape:\n" +
                "@@ -11 +11\n" +
                "- windows.dir=C:\\my\\path\\to\\sdk\n" +
                "+ windows.dir=C\\:\\\\my\\\\path\\\\to\\\\sdk\n" +
                "Fix for local.properties line 14: Escape:\n" +
                "@@ -14 +14\n" +
                "- ok.sdk.dir=C:\\\\Documents and Settings\\\\UserName\\\\Local Settings\\\\Application Data\\\\Android\\\\android-studio\\\\sdk\n" +
                "+ ok.sdk.dir=C\\:\\\\Documents and Settings\\\\UserName\\\\Local Settings\\\\Application Data\\\\Android\\\\android-studio\\\\sdk\n"
        )
    }

    fun testUseHttpInsteadOfHttps() {

        val expected =
            """
            gradle/wrapper/gradle-wrapper.properties:5: Warning: Replace HTTP with HTTPS for better security; use https\://services.gradle.org/distributions/gradle-2.1-all.zip [UsingHttp]
            distributionUrl=http\://services.gradle.org/distributions/gradle-2.1-all.zip
                            ~~~~
            0 errors, 1 warnings
            """
        lint().files(
            source(
                "gradle/wrapper/gradle-wrapper.properties",
                "" +
                    "distributionBase=GRADLE_USER_HOME\n" +
                    "distributionPath=wrapper/dists\n" +
                    "zipStoreBase=GRADLE_USER_HOME\n" +
                    "zipStorePath=wrapper/dists\n" +
                    "distributionUrl=http\\://services.gradle.org/distributions/gradle-2.1-all.zip\n"
            )
        ).run().expect(expected).expectFixDiffs(
            "" +
                "Fix for gradle/wrapper/gradle-wrapper.properties line 5: Replace with https:\n" +
                "@@ -5 +5\n" +
                "- distributionUrl=http\\://services.gradle.org/distributions/gradle-2.1-all.zip\n" +
                "+ distributionUrl=https\\://services.gradle.org/distributions/gradle-2.1-all.zip\n"
        )
    }

    fun testIssue92789() {

        // Regression test for https://code.google.com/p/android/issues/detail?id=92789

        val expected =
            """
            local.properties:1: Error: Windows file separators (\) and drive letter separators (':') must be escaped (\\) in property files; use D\:\\development\\android-sdks [PropertyEscape]
            sdk.dir=D:\\development\\android-sdks
                     ~
            1 errors, 0 warnings
            """
        lint().files(
            source(
                "local.properties",
                "sdk.dir=D:\\\\development\\\\android-sdks\n" + "\n"
            )
        ).run().expect(expected).expectFixDiffs(
            "" +
                "Fix for local.properties line 1: Escape:\n" +
                "@@ -1 +1\n" +
                "- sdk.dir=D:\\\\development\\\\android-sdks\n" +
                "+ sdk.dir=D\\:\\\\development\\\\android-sdks\n"
        )
    }

    fun testSuggestEscapes() {
        assertEquals("", suggestEscapes(""))
        assertEquals("foo", suggestEscapes("foo"))
        assertEquals("foo/bar", suggestEscapes("foo/bar"))
        assertEquals("c\\:\\\\foo\\\\bar", suggestEscapes("c\\:\\\\foo\\\\bar"))
        assertEquals("c\\:\\\\foo\\\\bar", suggestEscapes("c:\\\\foo\\bar"))
    }

    fun testNewLintVersion() {
        val task = lint()
        task.issues(GradleDetector.DEPENDENCY, PropertyFileDetector.HTTP)
        task.networkData(
            "https://maven.google.com/master-index.xml",
            """
            <metadata>
              <com.android.tools.build/>
            </metadata>
            """.trimIndent()
        )
        task.networkData(
            "https://maven.google.com/com/android/tools/build/group-index.xml",
            "" +
                "<com.android.tools.build>\n" +
                "  <gradle versions=\"3.0.0-alpha1,7.0.0,7.1.0-alpha01,7.1.0-alpha02,7.1.0-alpha03\"/>\n" +
                "</com.android.tools.build>"
        )
        task.files(
            source(
                "gradle.properties",
                "" +
                    "android.experimental.lint.version=7.0.0-alpha08\n" +
                    // Extra whitespace
                    "android.experimental.lint.version = 7.0.0-alpha09\n" +
                    // Too high, no suggestion
                    "android.experimental.lint.version=100.0.0-alpha10\n" +
                    // Suppressed
                    "#noinspection GradleDependency\n" +
                    "android.experimental.lint.version=7.0.0-alpha11\n" +
                    ""

                // TODO: Figure out if we're allowed to have comments trailing on lines
                // TODO write changes.md.html
            )
        ).run().expect(
            """
            gradle.properties:1: Warning: Newer version of lint available: 7.1.0-alpha03 [GradleDependency]
            android.experimental.lint.version=7.0.0-alpha08
                                              ~~~~~~~~~~~~~
            gradle.properties:2: Warning: Newer version of lint available: 7.1.0-alpha03 [GradleDependency]
            android.experimental.lint.version = 7.0.0-alpha09
                                                ~~~~~~~~~~~~~
            0 errors, 2 warnings
            """
        ).expectFixDiffs(
            """
            Fix for gradle.properties line 1: Update lint to 7.1.0-alpha03:
            @@ -1 +1
            - android.experimental.lint.version=7.0.0-alpha08
            + android.experimental.lint.version=7.1.0-alpha03
            Fix for gradle.properties line 2: Update lint to 7.1.0-alpha03:
            @@ -2 +2
            - android.experimental.lint.version = 7.0.0-alpha09
            + android.experimental.lint.version = 7.1.0-alpha03
            """
        )
    }

    fun testPasswords1() {
        // Regression test for b/63914231

        val expected =
            """
            gradle.properties:1: Warning: Storing passwords in clear text is risky; make sure this file is not shared or checked in via version control [ProxyPassword]
            systemProp.http.proxyPassword=something
                                          ~~~~~~~~~
            0 errors, 1 warnings"""
        lint().files(
            source(
                "gradle.properties",
                "systemProp.http.proxyPassword=something\n"
            )
        ).run().expect(expected)
    }

    fun testPasswords2() {
        lint().files(
            source(
                "local.properties",
                "systemProp.http.proxyPassword=something\n"
            ),
            source(
                ".gitignore",
                "" +
                    "*.iml\n" +
                    ".gradle\n" +
                    "/local.properties\n" +
                    "/.idea/libraries\n" +
                    "/.idea/modules.xml\n" +
                    "/.idea/workspace.xml\n" +
                    ".DS_Store\n" +
                    "/build\n" +
                    "/captures\n" +
                    ".externalNativeBuild"
            )
        ).run().expectClean()
    }
}
