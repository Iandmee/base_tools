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

package com.android.tools.lint.client.api

import com.android.testutils.TestUtils
import com.android.tools.lint.checks.AccessibilityDetector
import com.android.tools.lint.checks.ApiDetector
import com.android.tools.lint.checks.HardcodedValuesDetector
import com.android.tools.lint.checks.ManifestDetector
import com.android.tools.lint.checks.PxUsageDetector
import com.android.tools.lint.checks.RangeDetector
import com.android.tools.lint.checks.RestrictToDetector
import com.android.tools.lint.checks.ScopedStorageDetector
import com.android.tools.lint.checks.infrastructure.TestLintClient
import com.android.tools.lint.client.api.LintBaseline.Companion.isSamePathSuffix
import com.android.tools.lint.client.api.LintBaseline.Companion.stringsEquivalent
import com.android.tools.lint.detector.api.DefaultPosition
import com.android.tools.lint.detector.api.Incident
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.Project
import com.android.tools.lint.detector.api.Severity
import com.android.utils.XmlUtils
import com.google.common.truth.Truth.assertThat
import org.intellij.lang.annotations.Language
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class LintBaselineTest {
    @get:Rule
    var temporaryFolder = TemporaryFolder()

    /**
     * Overrides TestLintClient to use the checked-in SDK that is
     * available in the tools/base repo. The "real" TestLintClient
     * is a public utility for writing lint tests, so it cannot make
     * assumptions specific to tools/base.
     */
    private inner class ToolsBaseTestLintClient : TestLintClient() {
        override fun getSdkHome(): File? {
            return TestUtils.getSdk().toFile()
        }
    }

    private fun LintBaseline.findAndMark(
        issue: Issue,
        location: Location,
        message: String,
        severity: Severity?,
        project: Project?
    ): Boolean {
        val incident = Incident(issue, location, message).apply {
            severity?.let { this.severity = it }
            project?.let { this.project = it }
        }
        return findAndMark(incident)
    }

    @Test
    fun testBaseline() {
        val baselineFile = temporaryFolder.newFile("baseline.xml")

        @Language("XML")
        val baselineContents =
            """
            <issues format="5" by="lint unittest">

                <issue
                    id="UsesMinSdkAttributes"
                    severity="Warning"
                    message="&lt;uses-sdk> tag should specify a target API level (the highest verified version; when running on later versions, compatibility behaviors may be enabled) with android:targetSdkVersion=&quot;?&quot;"
                    category="Correctness"
                    priority="9"
                    summary="Minimum SDK and target SDK attributes not defined"
                    explanation="The manifest should contain a `&lt;uses-sdk>` element which defines the minimum API Level required for the application to run, as well as the target version (the highest API level you have tested the version for)."
                    url="http://developer.android.com/guide/topics/manifest/uses-sdk-element.html"
                    urls="http://developer.android.com/guide/topics/manifest/uses-sdk-element.html"
                    errorLine1="    &lt;uses-sdk android:minSdkVersion=&quot;8&quot; />"
                    errorLine2="    ^">
                    <location
                        file="AndroidManifest.xml"
                        line="7"/>
                </issue>

                <issue
                    id="HardcodedText"
                    severity="Warning"
                    message="[I18N] Hardcoded string &quot;Fooo&quot;, should use @string resource"
                    category="Internationalization"
                    priority="5"
                    summary="Hardcoded text"
                    explanation="Hardcoding text attributes directly in layout files is bad for several reasons:

            * When creating configuration variations (for example for landscape or portrait)you have to repeat the actual text (and keep it up to date when making changes)

            * The application cannot be translated to other languages by just adding new translations for existing string resources.

            There are quickfixes to automatically extract this hardcoded string into a resource lookup."
                    errorLine1="        android:text=&quot;Fooo&quot; />"
                    errorLine2="        ~~~~~~~~~~~~~~~~~~~">
                    <location
                        file="res/layout/main.xml"
                        line="12"/>
                    <location
                        file="res/layout/main2.xml"
                        line="11"/>
                </issue>

                <issue
                    id="Range"
                    message="Value must be ≥ 0 (was -1)"
                    errorLine1="                                childHeightSpec = MeasureSpec.makeMeasureSpec(maxLayoutHeight,"
                    errorLine2="                                                                              ~~~~~~~~~~~~~~~">
                    <location
                        file="java/android/support/v4/widget/SlidingPaneLayout.java"
                        line="589"
                        column="79"/>
                </issue>

            </issues>
            """.trimIndent()
        baselineFile.writeText(baselineContents)

        val baseline = LintBaseline(ToolsBaseTestLintClient(), baselineFile)

        var found: Boolean = baseline.findAndMark(
            ManifestDetector.USES_SDK,
            Location.create(File("bogus")), "Unrelated", Severity.WARNING, null
        )
        assertThat(found).isFalse()
        assertThat(baseline.foundWarningCount).isEqualTo(0)
        assertThat(baseline.foundErrorCount).isEqualTo(0)
        assertThat(baseline.totalCount).isEqualTo(3)
        // because we haven't actually matched anything
        assertThat(baseline.fixedCount).isEqualTo(3)

        // Wrong issue
        found = baseline.findAndMark(
            ManifestDetector.USES_SDK,
            Location.create(File("bogus")),
            "Hardcoded string \"Fooo\", should use @string resource", Severity.WARNING, null
        )
        assertThat(found).isFalse()
        assertThat(baseline.foundWarningCount).isEqualTo(0)
        assertThat(baseline.foundErrorCount).isEqualTo(0)
        assertThat(baseline.fixedCount).isEqualTo(3)

        // Wrong file
        found = baseline.findAndMark(
            HardcodedValuesDetector.ISSUE,
            Location.create(File("res/layout-port/main.xml")),
            "Hardcoded string \"Fooo\", should use @string resource", Severity.WARNING, null
        )
        assertThat(found).isFalse()
        assertThat(baseline.foundWarningCount).isEqualTo(0)
        assertThat(baseline.foundErrorCount).isEqualTo(0)
        assertThat(baseline.fixedCount).isEqualTo(3)

        // Match
        found = baseline.findAndMark(
            HardcodedValuesDetector.ISSUE,
            Location.create(File("res/layout/main.xml")),
            "Hardcoded string \"Fooo\", should use @string resource", Severity.WARNING, null
        )
        assertThat(found).isTrue()
        assertThat(baseline.fixedCount).isEqualTo(2)
        assertThat(baseline.foundWarningCount).isEqualTo(1)
        assertThat(baseline.foundErrorCount).isEqualTo(0)
        assertThat(baseline.fixedCount).isEqualTo(2)

        // Search for the same error once it's already been found: no longer there
        found = baseline.findAndMark(
            HardcodedValuesDetector.ISSUE,
            Location.create(File("res/layout/main.xml")),
            "Hardcoded string \"Fooo\", should use @string resource", Severity.WARNING, null
        )
        assertThat(found).isFalse()
        assertThat(baseline.foundWarningCount).isEqualTo(1)
        assertThat(baseline.foundErrorCount).isEqualTo(0)
        assertThat(baseline.fixedCount).isEqualTo(2)

        found = baseline.findAndMark(
            RangeDetector.RANGE,
            Location.create(
                File(
                    "java/android/support/v4/widget/SlidingPaneLayout.java"
                )
            ),
            // Match, by different message
            // Actual: "Value must be \u2265 0 (was -1)", Severity.WARNING, null
            "Value must be \u2265 0", Severity.WARNING, null
        )
        assertThat(found).isTrue()
        assertThat(baseline.fixedCount).isEqualTo(1)
        assertThat(baseline.foundWarningCount).isEqualTo(2)
        assertThat(baseline.foundErrorCount).isEqualTo(0)
        assertThat(baseline.fixedCount).isEqualTo(1)

        baseline.close()
    }

    @Test
    fun testSuffix() {
        assertTrue(isSamePathSuffix("foo", "foo"))
        assertTrue(isSamePathSuffix("", ""))
        assertTrue(isSamePathSuffix("abc/def/foo", "def/foo"))
        assertTrue(isSamePathSuffix("abc/def/foo", "../../def/foo"))
        assertTrue(isSamePathSuffix("abc\\def\\foo", "abc\\def\\foo"))
        assertTrue(isSamePathSuffix("abc\\def\\foo", "..\\..\\abc\\def\\foo"))
        assertTrue(isSamePathSuffix("abc\\def\\foo", "def\\foo"))
        assertFalse(isSamePathSuffix("foo", "bar"))
    }

    @Test
    fun testStringsEquivalent() {
        assertTrue(stringsEquivalent("", ""))
        assertTrue(stringsEquivalent("foo", ""))
        assertTrue(stringsEquivalent("", "bar"))
        assertTrue(stringsEquivalent("foo", "foo"))
        assertTrue(stringsEquivalent("foo", "foo."))
        assertTrue(stringsEquivalent("foo.", "foo"))
        assertTrue(stringsEquivalent("foo.", "foo. Bar."))
        assertTrue(stringsEquivalent("foo. Bar.", "foo"))
        assertTrue(stringsEquivalent("", ""))
        assertTrue(stringsEquivalent("abc def", "abc `def`"))
        assertTrue(stringsEquivalent("abc `def` ghi", "abc def ghi"))
        assertTrue(stringsEquivalent("`abc` def", "abc def"))
        assertTrue(
            stringsEquivalent(
                "Suspicious equality check: equals() is not implemented in targetType",
                "Suspicious equality check: `equals()` is not implemented in targetType"
            )
        )
        assertTrue(
            stringsEquivalent(
                "This Handler class should be static or leaks might occur name",
                "This `Handler` class should be static or leaks might occur name"
            )
        )
        assertTrue(
            stringsEquivalent(
                "Using the AllowAllHostnameVerifier HostnameVerifier is unsafe ",
                "Using the `AllowAllHostnameVerifier` HostnameVerifier is unsafe "
            )
        )
        assertTrue(
            stringsEquivalent(
                "Reading app signatures from getPackageInfo: The app signatures could be exploited if not validated properly; see issue explanation for details.",
                "Reading app signatures from `getPackageInfo`: The app signatures could be exploited if not validated properly; see issue explanation for details"
            )
        )
        assertTrue(stringsEquivalent("````abc", "abc"))
        assertFalse(stringsEquivalent("abc", "def"))
        assertFalse(stringsEquivalent("abcd", "abce"))
    }

    @Test
    fun tolerateMinSpChanges() {
        val baseline = LintBaseline(null, File(""))
        assertTrue(
            baseline.sameMessage(
                PxUsageDetector.SMALL_SP_ISSUE,
                "Avoid using sizes smaller than 12sp: 11sp",
                "Avoid using sizes smaller than 11sp: 11sp"
            )
        )
    }

    @Test
    fun tolerateRestrictToChanges() {
        val baseline = LintBaseline(null, File(""))
        assertTrue(
            baseline.sameMessage(
                RestrictToDetector.RESTRICTED,
                "LibraryCode.method3 can only be called from within the same library group (referenced groupId=test.pkg.library from groupId=other.app)",
                "LibraryCode.method3 can only be called from within the same library group (groupId=test.pkg.library)"
            )
        )
        assertTrue(
            baseline.sameMessage(
                RestrictToDetector.RESTRICTED,
                "LibraryCode.method3 can only be called from within the same library group (referenced groupId=test.pkg.library from groupId=other.app)",
                "LibraryCode.method3 can only be called from within the same library group"
            )
        )
        assertFalse(
            baseline.sameMessage(
                RestrictToDetector.RESTRICTED,
                "LibraryCode.FIELD3 can only be called from within the same library group (referenced groupId=test.pkg.library from groupId=other.app)",
                "LibraryCode.method3 can only be called from within the same library group (groupId=test.pkg.library)"
            )
        )
    }

    @Test
    fun tolerateUrlChanges() {
        assertTrue(stringsEquivalent("abcd http://some.url1", "abcd http://other.url2"))
        assertTrue(stringsEquivalent("abcd http://some.url1, ok", "abcd http://other.url2"))
        assertTrue(stringsEquivalent("abcd http://some.url1", "abcd http://other.url2, ok"))
        assertFalse(stringsEquivalent("abcd http://some.url1 different", "abcd http://other.url2, words"))
    }

    @Test
    fun tolerateScopedStorageChanges() {
        val baseline = LintBaseline(null, File(""))
        assertTrue(
            baseline.sameMessage(
                ScopedStorageDetector.ISSUE,
                "The Google Play store has a policy that limits usage of MANAGE_EXTERNAL_STORAGE",
                "Most apps are not allowed to use MANAGE_EXTERNAL_STORAGE"
            )
        )
    }

    @Test
    fun tolerateApiDetectorMessageChanges() {
        val baseline = LintBaseline(null, File(""))
        assertTrue(
            baseline.sameMessage(
                ApiDetector.UNSUPPORTED,
                "Call requires API level R (current min is 1): `setZOrderedOnTop`",
                "Call requires API level 30 (current min is 29): `setZOrderedOnTop`"
            )
        )

        assertFalse(
            baseline.sameMessage(
                ApiDetector.UNSUPPORTED,
                "Field requires API level R (current min is 29): `setZOrderedOnTop`",
                "Call requires API level 30 (current min is 29): `setZOrderedOnTop`"
            )
        )

        assertFalse(
            baseline.sameMessage(
                ApiDetector.UNSUPPORTED,
                "Call requires API level R (current min is 29): `setZOrderedOnTop`",
                "Call requires API level 30 (current min is 29): `setZOrdered`"
            )
        )
    }

    @Test
    fun tolerateA11yI18nChanges() {
        val baseline = LintBaseline(null, File(""))
        assertTrue(
            baseline.sameMessage(
                HardcodedValuesDetector.ISSUE,
                "Hardcoded string \"Fooo\", should use @string resource",
                "[I18N] Hardcoded string \"Fooo\", should use @string resource"
            )
        )

        assertTrue(
            baseline.sameMessage(
                AccessibilityDetector.ISSUE,
                "Empty contentDescription attribute on image",
                "[Accessibility] Empty contentDescription attribute on image"
            )
        )
    }

    @Test
    fun testFormat() {
        val baselineFile = temporaryFolder.newFile("lint-baseline.xml")
        val client = ToolsBaseTestLintClient()
        var baseline = LintBaseline(client, baselineFile)
        assertThat(baseline.writeOnClose).isFalse()
        baseline.writeOnClose = true
        assertThat(baseline.writeOnClose).isTrue()

        val project1Folder = temporaryFolder.newFolder("project1")
        val project2Folder = temporaryFolder.newFolder("project2")
        val project2 = Project.create(client, project2Folder, project2Folder)

        // Make sure file exists, since path computations depend on it
        val sourceFile = File(project1Folder, "my/source/file.txt").absoluteFile
        sourceFile.parentFile.mkdirs()
        sourceFile.createNewFile()

        baseline.findAndMark(
            HardcodedValuesDetector.ISSUE,
            Location.create(sourceFile, "", 0),
            "Hardcoded string \"Fooo\", should use `@string` resource",
            Severity.WARNING, project2
        )
        baseline.findAndMark(
            ManifestDetector.USES_SDK,
            Location.create(
                File("/foo/bar/Foo/AndroidManifest.xml"),
                DefaultPosition(6, 4, 198), DefaultPosition(6, 42, 236)
            ),
            "<uses-sdk> tag should specify a target API level (the highest verified \n" +
                "version; when running on later versions, compatibility behaviors may \n" +
                "be enabled) with android:targetSdkVersion=\"?\"",
            Severity.WARNING, null
        )
        baseline.close()

        var actual = baselineFile.readText().replace(File.separatorChar, '/')

        @Language("XML")
        val expected =
            """<?xml version="1.0" encoding="UTF-8"?>
<issues format="5" by="lint unittest">

    <issue
        id="UsesMinSdkAttributes"
        message="&lt;uses-sdk> tag should specify a target API level (the highest verified &#xA;version; when running on later versions, compatibility behaviors may &#xA;be enabled) with android:targetSdkVersion=&quot;?&quot;">
        <location
            file="/foo/bar/Foo/AndroidManifest.xml"
            line="7"/>
    </issue>

    <issue
        id="HardcodedText"
        message="Hardcoded string &quot;Fooo&quot;, should use `@string` resource">
        <location
            file="../project1/my/source/file.txt"
            line="1"/>
    </issue>

</issues>
"""
        assertThat(actual).isEqualTo(expected)

        // Now load the baseline back in and make sure we can match entries correctly
        baseline = LintBaseline(client, baselineFile)
        baseline.writeOnClose = true
        assertThat(baseline.removeFixed).isFalse()

        var found: Boolean = baseline.findAndMark(
            HardcodedValuesDetector.ISSUE,
            Location.create(sourceFile, "", 0),
            "Hardcoded string \"Fooo\", should use `@string` resource",
            Severity.WARNING, project2
        )
        assertThat(found).isTrue()
        found = baseline.findAndMark(
            ManifestDetector.USES_SDK,
            Location.create(
                File("/foo/bar/Foo/AndroidManifest.xml"),
                DefaultPosition(6, 4, 198), DefaultPosition(6, 42, 236)
            ),
            "<uses-sdk> tag should specify a target API level (the highest verified \n" +
                "version; when running on later versions, compatibility behaviors may \n" +
                "be enabled) with android:targetSdkVersion=\"?\"",
            Severity.WARNING, null
        )
        assertThat(found).isTrue()
        baseline.close()

        actual = baselineFile.readText().replace(File.separatorChar, '/')
        assertThat(actual).isEqualTo(expected)

        // Test the skip fix flag
        baseline = LintBaseline(client, baselineFile)
        baseline.writeOnClose = true
        baseline.removeFixed = true
        assertThat(baseline.removeFixed).isTrue()

        found = baseline.findAndMark(
            HardcodedValuesDetector.ISSUE,
            Location.create(sourceFile, "", 0),
            "Hardcoded string \"Fooo\", should use `@string` resource",
            Severity.WARNING, project2
        )
        assertThat(found).isTrue()

        // Note that this is a different, unrelated issue
        found = baseline.findAndMark(
            ManifestDetector.APPLICATION_ICON,
            Location.create(
                File("/foo/bar/Foo/AndroidManifest.xml"),
                DefaultPosition(4, 4, 198), DefaultPosition(4, 42, 236)
            ),
            "Should explicitly set `android:icon`, there is no default",
            Severity.WARNING, null
        )
        assertThat(found).isFalse()
        baseline.close()

        actual = baselineFile.readText().replace(File.separatorChar, '/')

        // This time we should ONLY get the initial baseline issue back; we should
        // NOT see the new issue, and the fixed issue (the uses sdk error reported in the baseline
        // before but not repeated now) should be missing.
        assertThat(actual).isEqualTo(
            "" +
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<issues format=\"5\" by=\"lint unittest\">\n" +
                "\n" +
                "    <issue\n" +
                "        id=\"HardcodedText\"\n" +
                "        message=\"Hardcoded string &quot;Fooo&quot;, should use `@string` resource\">\n" +
                "        <location\n" +
                "            file=\"../project1/my/source/file.txt\"\n" +
                "            line=\"1\"/>\n" +
                "    </issue>\n" +
                "\n" +
                "</issues>\n"
        )
    }

    @Test
    fun testChangedUrl() {
        val baselineFile = temporaryFolder.newFile("baseline.xml")

        val errorMessage = "The attribute android:allowBackup is deprecated from Android 12 and higher and ..."

        @Language("XML")
        val baselineContents =
            """<?xml version="1.0" encoding="UTF-8"?>
<issues format="5" by="lint unittest">

    <issue
        id="DataExtractionRules"
        message="$errorMessage"
        errorLine1="    &lt;application"
        errorLine2="    ^">
        <location
            file="src/main/AndroidManifest.xml"
            line="5"
            column="5"/>
    </issue>

</issues>
"""
        baselineFile.writeText(baselineContents)
        val baseline = LintBaseline(ToolsBaseTestLintClient(), baselineFile)

        assertTrue(
            baseline.findAndMark(
                ManifestDetector.DATA_EXTRACTION_RULES,
                Location.create(File("src/main/AndroidManifest.xml")),
                errorMessage,
                Severity.WARNING,
                null
            )
        )

        baseline.close()
    }

    @Test
    fun testPlatformTestCase() {
        val baselineFile = temporaryFolder.newFile("baseline.xml")

        @Language("text")
        val path = "packages/modules/IPsec/src/java/com/android/internal/net/ipsec/ike/ChildSessionStateMachine.java"

        @Language("XML")
        val baselineContents =
            """
            <issues format="5" by="lint 4.1.0" client="cli" variant="all" version="4.1.0">

                <issue id="NewApi" message="Class requires API level 31 (current min is 30): `android.net.ipsec.ike.exceptions.IkeProtocolException`">
                    <location file="packages/modules/IPsec/src/java/android/net/ipsec/ike/exceptions/AuthenticationFailedException.java"/>
                </issue>
                <issue id="NewApi" message="Call requires API level 31 (current min is 30): `new android.net.ipsec.ike.exceptions.IkeInternalException`"><location file="$path" /></issue>
                <issue id="NewApi" message="Cast from `IkeInternalException` to `IkeException` requires API level 31 (current min is 30)"><location file="$path" /></issue>
                <issue id="NewApi" message="Class requires API level 31 (current min is 30): `android.net.ipsec.ike.exceptions.IkeException`"><location file="$path" /></issue>
                <issue id="NewApi" message="Class requires API level 31 (current min is 30): `android.net.ipsec.ike.exceptions.IkeException`"><location file="$path" /></issue>
                <issue id="NewApi" message="Call requires API level 31 (current min is 30): `new android.net.ipsec.ike.exceptions.IkeInternalException`"><location file="$path" /></issue>
                <issue id="NewApi" message="Cast from `IkeException` to `Throwable` requires API level 31 (current min is 30)"><location file="$path" /></issue>
                <issue id="NewApi" message="Cast from `IkeException` to `Exception` requires API level 31 (current min is 30)"><location file="$path" /></issue>
                <issue id="NewApi" message="Class requires API level 31 (current min is 30): `android.net.ipsec.ike.exceptions.TemporaryFailureException`"><location file="$path" /></issue>
                <issue id="NewApi" message="Call requires API level 31 (current min is 30): `new android.net.ipsec.ike.exceptions.InvalidSyntaxException`"><location file="$path" /></issue>
                <issue id="NewApi" message="Call requires API level 31 (current min is 30): `new android.net.ipsec.ike.exceptions.InvalidSyntaxException`"><location file="$path" /></issue>
                <issue id="NewApi" message="Exception requires API level 31 (current min is 30): `android.net.ipsec.ike.exceptions.IkeProtocolException`"><location file="$path" /></issue>
                <issue id="NewApi" message="Cast from `IkeProtocolException` to `Exception` requires API level 31 (current min is 30)"><location file="$path" /></issue>
                <issue id="NewApi" message="Cast from `IkeException` to `Exception` requires API level 31 (current min is 30)"><location file="$path" /></issue>
                <issue id="NewApi" message="Cast from `ChildSaProposal` to `SaProposal` requires API level 31 (current min is 30)"><location file="$path" /></issue>
                <issue id="NewApi" message="Exception requires API level 31 (current min is 30): `android.net.ipsec.ike.exceptions.NoValidProposalChosenException`"><location file="$path" /></issue>
                <issue id="NewApi" message="Cast from `NoValidProposalChosenException` to `IkeProtocolException` requires API level 31 (current min is 30)"><location file="$path" /></issue>
                <issue id="NewApi" message="Call requires API level 31 (current min is 30): `new android.net.ipsec.ike.exceptions.NoValidProposalChosenException`"><location file="$path" /></issue>
                <issue id="NewApi" message="Cast from `NoValidProposalChosenException` to `IkeProtocolException` requires API level 31 (current min is 30)"><location file="$path" /></issue>
                <issue id="NewApi" message="Call requires API level 31 (current min is 30): `new android.net.ipsec.ike.exceptions.NoValidProposalChosenException`"><location file="$path" /></issue>
                <issue id="NewApi" message="Cast from `NoValidProposalChosenException` to `IkeProtocolException` requires API level 31 (current min is 30)"><location file="$path" /></issue>
                <issue id="NewApi" message="Class requires API level 31 (current min is 30): `android.net.ipsec.ike.exceptions.IkeProtocolException`"><location file="$path" /></issue>
                <issue id="NewApi" message="Class requires API level 31 (current min is 30): `android.net.ipsec.ike.exceptions.IkeProtocolException`"><location file="$path" /></issue>
                <issue id="NewApi" message="Call requires API level 31 (current min is 30): `new android.net.ipsec.ike.exceptions.NoValidProposalChosenException`"><location file="$path" /></issue>
                <issue id="NewApi" message="Cast from `NoValidProposalChosenException` to `IkeProtocolException` requires API level 31 (current min is 30)"><location file="$path" /></issue>
                <issue id="NewApi" message="Cast from `IkeException` to `Throwable` requires API level 31 (current min is 30)"><location file="$path" /></issue>
                <issue id="NewApi" message="Cast from `IkeProtocolException` to `Throwable` requires API level 31 (current min is 30)"><location file="$path" /></issue>
                <issue id="NewApi" message="Exception requires API level 31 (current min is 30): `android.net.ipsec.ike.exceptions.IkeProtocolException`"><location file="$path" /></issue>
                <issue id="NewApi" message="Cast from `IkeProtocolException` to `Throwable` requires API level 31 (current min is 30)"><location file="$path" /></issue>
                <issue id="NewApi" message="Class requires API level 31 (current min is 30): `android.net.ipsec.ike.TunnelModeChildSessionParams`"><location file="$path" /></issue>
                <issue id="NewApi" message="Exception requires API level 31 (current min is 30): `android.net.ipsec.ike.exceptions.InvalidSyntaxException`"><location file="$path" /></issue>
                <issue id="NewApi" message="Cast from `InvalidSyntaxException` to `IkeException` requires API level 31 (current min is 30)"><location file="$path" /></issue>
                <issue id="NewApi" message="Cast from `IkeProtocolException` to `IkeException` requires API level 31 (current min is 30)"><location file="$path" /></issue>
                <issue id="NewApi" message="Exception requires API level 31 (current min is 30): `android.net.ipsec.ike.exceptions.InvalidSyntaxException`"><location file="$path" /></issue>
                <issue id="NewApi" message="Cast from `InvalidSyntaxException` to `IkeException` requires API level 31 (current min is 30)"><location file="$path" /></issue>
                <issue id="NewApi" message="Call requires API level 31 (current min is 30): `new android.net.ipsec.ike.exceptions.NoValidProposalChosenException`"><location file="$path" /></issue>
                <issue id="NewApi" message="Exception requires API level 31 (current min is 30): `android.net.ipsec.ike.exceptions.IkeProtocolException`"><location file="$path" /></issue>
                <issue id="NewApi" message="Class requires API level 31 (current min is 30): `android.net.ipsec.ike.exceptions.InvalidSyntaxException`"><location file="$path" /></issue>
                <issue id="NewApi" message="Cast from `InvalidSyntaxException` to `IkeException` requires API level 31 (current min is 30)"><location file="$path" /></issue>
                <issue id="NewApi" message="Class requires API level 31 (current min is 30): `android.net.ipsec.ike.exceptions.IkeProtocolException`"><location file="$path" /></issue>
                <issue id="NewApi" message="Call requires API level 31 (current min is 30): `new android.net.ipsec.ike.exceptions.InvalidSyntaxException`"><location file="$path" /></issue>
                <issue id="NewApi" message="Cast from `InvalidSyntaxException` to `IkeException` requires API level 31 (current min is 30)"><location file="$path" /></issue>
                <issue id="NewApi" message="Call requires API level 31 (current min is 30): `new android.net.ipsec.ike.exceptions.IkeInternalException`"><location file="$path" /></issue>
                <issue id="NewApi" message="Cast from `IkeInternalException` to `IkeException` requires API level 31 (current min is 30)"><location file="$path" /></issue>
                <issue id="NewApi" message="Call requires API level 31 (current min is 30): `new android.net.ipsec.ike.exceptions.InvalidSyntaxException`"><location file="$path" /></issue>
                <issue id="NewApi" message="Call requires API level 31 (current min is 30): `new android.net.ipsec.ike.exceptions.InvalidSyntaxException`"><location file="$path" /></issue>
                <issue id="NewApi" message="Call requires API level 31 (current min is 30): `new android.net.ipsec.ike.exceptions.TsUnacceptableException`"><location file="$path" /></issue>
                <issue id="NewApi" message="Call requires API level 31 (current min is 30): `new android.net.ipsec.ike.exceptions.InvalidSyntaxException`"><location file="$path" /></issue>
                <issue id="NewApi" message="Call requires API level 31 (current min is 30): `new android.net.ipsec.ike.exceptions.InvalidKeException`"><location file="$path" /></issue>
                <issue id="NewApi" message="Call requires API level 31 (current min is 30): `new android.net.ipsec.ike.exceptions.InvalidSyntaxException`"><location file="$path" /></issue>
            </issues>
            """.trimIndent()
        baselineFile.writeText(baselineContents)
        assertNotNull(XmlUtils.parseDocumentSilently(baselineContents, false))
        val baseline = LintBaseline(ToolsBaseTestLintClient(), baselineFile)

        fun mark(message: String, path: String): Boolean {
            val location = Location.create(File(path))
            return baseline.findAndMark(ApiDetector.UNSUPPORTED, location, message, Severity.WARNING, null)
        }

        assertTrue(mark("Class requires API level S (current min is 30): `android.net.ipsec.ike.exceptions.IkeException`", "/packages/modules/IPsec/src/java/com/android/internal/net/ipsec/ike/ChildSessionStateMachine.java"))
        assertTrue(mark("Class requires API level S (current min is 30): `android.net.ipsec.ike.exceptions.IkeException`", "/packages/modules/IPsec/src/java/com/android/internal/net/ipsec/ike/ChildSessionStateMachine.java"))
        assertTrue(mark("Cast from `IkeException` to `Throwable` requires API level 31 (current min is 30)", "/packages/modules/IPsec/src/java/com/android/internal/net/ipsec/ike/ChildSessionStateMachine.java"))
        assertTrue(mark("Cast from `IkeInternalException` to `IkeException` requires API level 31 (current min is 30)", "/packages/modules/IPsec/src/java/com/android/internal/net/ipsec/ike/ChildSessionStateMachine.java"))
        assertTrue(mark("Cast from `IkeException` to `Exception` requires API level 31 (current min is 30)", "/packages/modules/IPsec/src/java/com/android/internal/net/ipsec/ike/ChildSessionStateMachine.java"))
        assertTrue(mark("Exception requires API level S (current min is 30): `android.net.ipsec.ike.exceptions.IkeProtocolException`", "/packages/modules/IPsec/src/java/com/android/internal/net/ipsec/ike/ChildSessionStateMachine.java"))
        assertTrue(mark("Cast from `IkeProtocolException` to `Exception` requires API level 31 (current min is 30)", "/packages/modules/IPsec/src/java/com/android/internal/net/ipsec/ike/ChildSessionStateMachine.java"))
        assertTrue(mark("Cast from `IkeException` to `Exception` requires API level 31 (current min is 30)", "/packages/modules/IPsec/src/java/com/android/internal/net/ipsec/ike/ChildSessionStateMachine.java"))
        assertTrue(mark("Cast from `ChildSaProposal` to `SaProposal` requires API level 31 (current min is 30)", "/packages/modules/IPsec/src/java/com/android/internal/net/ipsec/ike/ChildSessionStateMachine.java"))
        assertTrue(mark("Class requires API level S (current min is 30): `android.net.ipsec.ike.exceptions.IkeProtocolException`", "/packages/modules/IPsec/src/java/com/android/internal/net/ipsec/ike/ChildSessionStateMachine.java"))
        assertTrue(mark("Class requires API level S (current min is 30): `android.net.ipsec.ike.exceptions.IkeProtocolException`", "/packages/modules/IPsec/src/java/com/android/internal/net/ipsec/ike/ChildSessionStateMachine.java"))
        assertTrue(mark("Cast from `IkeException` to `Throwable` requires API level 31 (current min is 30)", "/packages/modules/IPsec/src/java/com/android/internal/net/ipsec/ike/ChildSessionStateMachine.java"))
        assertTrue(mark("Cast from `IkeProtocolException` to `Throwable` requires API level 31 (current min is 30)", "/packages/modules/IPsec/src/java/com/android/internal/net/ipsec/ike/ChildSessionStateMachine.java"))
        assertTrue(mark("Exception requires API level S (current min is 30): `android.net.ipsec.ike.exceptions.IkeProtocolException`", "/packages/modules/IPsec/src/java/com/android/internal/net/ipsec/ike/ChildSessionStateMachine.java"))
        assertTrue(mark("Cast from `IkeProtocolException` to `Throwable` requires API level 31 (current min is 30)", "/packages/modules/IPsec/src/java/com/android/internal/net/ipsec/ike/ChildSessionStateMachine.java"))
        assertTrue(mark("Class requires API level S (current min is 30): `android.net.ipsec.ike.TunnelModeChildSessionParams`", "/packages/modules/IPsec/src/java/com/android/internal/net/ipsec/ike/ChildSessionStateMachine.java"))
        assertTrue(mark("Cast from `IkeProtocolException` to `IkeException` requires API level 31 (current min is 30)", "/packages/modules/IPsec/src/java/com/android/internal/net/ipsec/ike/ChildSessionStateMachine.java"))
        assertTrue(mark("Exception requires API level S (current min is 30): `android.net.ipsec.ike.exceptions.IkeProtocolException`", "/packages/modules/IPsec/src/java/com/android/internal/net/ipsec/ike/ChildSessionStateMachine.java"))
        assertTrue(mark("Class requires API level S (current min is 30): `android.net.ipsec.ike.exceptions.IkeProtocolException`", "/packages/modules/IPsec/src/java/com/android/internal/net/ipsec/ike/ChildSessionStateMachine.java"))
        assertTrue(mark("Cast from `IkeInternalException` to `IkeException` requires API level 31 (current min is 30)", "/packages/modules/IPsec/src/java/com/android/internal/net/ipsec/ike/ChildSessionStateMachine.java"))
        baseline.close()
    }

    @Test
    fun testInexactMatching() {
        // Test 1: Test matching where we look at the wrong file and return instead of getting to the next one
        val baselineFile = temporaryFolder.newFile("baseline.xml")

        @Language("XML")
        val baselineContents =
            """
            <issues format="5" by="lint 4.1.0" client="cli" variant="all" version="4.1.0">

                <issue id="NewApi" message="Call requires API level 29: `Something`"><location file="OtherFile.java"/></issue>
                <issue id="NewApi" message="Call requires API level 30: `Something`"><location file="MyFile.java"/></issue>
            </issues>
            """.trimIndent()
        baselineFile.writeText(baselineContents)
        assertNotNull(XmlUtils.parseDocumentSilently(baselineContents, false))
        val baseline = LintBaseline(ToolsBaseTestLintClient(), baselineFile)
        assertTrue(
            baseline.findAndMark(
                ApiDetector.UNSUPPORTED,
                Location.create(File("MyFile.java")),
                "Call requires API level S: `Something`",
                Severity.WARNING,
                null
            )
        )
        baseline.close()
    }

    @Test
    fun testMessageToEntryCleanup() {
        val baselineFile = temporaryFolder.newFile("baseline.xml")

        @Language("XML")
        val baselineContents =
            """
            <issues format="5" by="lint 4.1.0" client="cli" variant="all" version="4.1.0">

                <issue id="NewApi" message="Call requires API level 30: `Something`"><location file="MyFile.java"/></issue>
                <issue id="NewApi" message="Call requires API level 30: `Something`"><location file="OtherFile.java"/></issue>
            </issues>
            """.trimIndent()

        baselineFile.writeText(baselineContents)
        assertNotNull(XmlUtils.parseDocumentSilently(baselineContents, false))
        val baseline = LintBaseline(ToolsBaseTestLintClient(), baselineFile)

        fun mark(message: String, path: String): Boolean {
            val location = Location.create(File(path))
            return baseline.findAndMark(ApiDetector.UNSUPPORTED, location, message, Severity.WARNING, null)
        }

        assertTrue(mark("Call requires API level 30: `Something`", "MyFile.java"))
        assertTrue(mark("Call requires API level 29: `Something`", "OtherFile.java"))
        baseline.close()
    }
}
