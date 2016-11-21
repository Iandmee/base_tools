/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.tools.lint.checks;

import static com.android.SdkConstants.FN_PUBLIC_TXT;
import static com.android.SdkConstants.FN_RESOURCE_TEXT;
import static org.mockito.Mockito.when;

import com.android.builder.model.AndroidLibrary;
import com.android.builder.model.Dependencies;
import com.android.testutils.TestUtils;
import com.android.tools.lint.checks.infrastructure.TestLintTask;
import com.android.tools.lint.detector.api.Detector;
import com.google.common.base.Charsets;
import com.google.common.io.Files;
import java.io.File;
import java.io.IOException;

@SuppressWarnings("javadoc")
public class PrivateResourceDetectorTest extends AbstractCheckTest {
    @Override
    protected Detector getDetector() {
        return new PrivateResourceDetector();
    }

    private TestLintTask.GradleMockModifier mockModifier = (project, variant) -> {
        // Null out the resolved coordinates in the result to simulate the
        // observed failure in issue 226240
        //noinspection ConstantConditions
        Dependencies dependencies = variant.getMainArtifact().getDependencies();
        AndroidLibrary library = dependencies.getLibraries().iterator().next();

        final File tempDir = TestUtils.createTempDirDeletedOnExit();

        try {
            String allResources = ""
                    + "int string my_private_string 0x7f040000\n"
                    + "int string my_public_string 0x7f040001\n"
                    + "int layout my_private_layout 0x7f040002\n"
                    + "int id title 0x7f040003\n"
                    + "int style Theme_AppCompat_DayNight 0x7f070004";

            File rFile = new File(tempDir, FN_RESOURCE_TEXT);
            Files.write(allResources, rFile, Charsets.UTF_8);

            String publicResources = ""
                    + ""
                    + "string my_public_string\n"
                    + "style Theme.AppCompat.DayNight\n";

            File publicTxtFile = new File(tempDir, FN_PUBLIC_TXT);
            Files.write(publicResources, publicTxtFile, Charsets.UTF_8);
            when(library.getPublicResources()).thenReturn(publicTxtFile);
            when(library.getSymbolFile()).thenReturn(rFile);
        } catch (IOException ioe) {
            fail(ioe.getMessage());
        }
    };

    @Override
    protected boolean allowCompilationErrors() {
        // Some of these unit tests are still relying on source code that references
        // unresolved symbols etc.
        return false;
    }

    public void testPrivateInXml() throws Exception {
        String expected = ""
                + "res/layout/private.xml:11: Warning: The resource @string/my_private_string is marked as private in com.android.tools:test-library [PrivateResource]\n"
                + "            android:text=\"@string/my_private_string\" />\n"
                + "                          ~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "0 errors, 1 warnings\n";
        lint().files(
                xml("res/layout/private.xml", ""
                        + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                        + "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                        + "              android:id=\"@+id/newlinear\"\n"
                        + "              android:orientation=\"vertical\"\n"
                        + "              android:layout_width=\"match_parent\"\n"
                        + "              android:layout_height=\"match_parent\">\n"
                        + "\n"
                        + "    <TextView\n"
                        + "            android:layout_width=\"wrap_content\"\n"
                        + "            android:layout_height=\"wrap_content\"\n"
                        + "            android:text=\"@string/my_private_string\" />\n"
                        + "\n"
                        + "    <TextView\n"
                        + "            android:layout_width=\"wrap_content\"\n"
                        + "            android:layout_height=\"wrap_content\"\n"
                        + "            android:text=\"@string/my_public_string\" />\n"
                        + "</LinearLayout>\n"),
                gradle(""
                        + "apply plugin: 'com.android.application'\n"
                        + "\n"
                        + "dependencies {\n"
                        + "    compile 'com.android.tools:test-library:1.0.0'\n"
                        + "}\n"))
                .modifyGradleMocks(mockModifier)
                .run()
                .expect(expected);
    }

    @SuppressWarnings("ClassNameDiffersFromFileName")
    public void testPrivateInJava() throws Exception {
        String expected = ""
                + "src/main/java/Private.java:3: Warning: The resource @string/my_private_string is marked as private in com.android.tools:test-library [PrivateResource]\n"
                + "        int x = R.string.my_private_string; // ERROR\n"
                + "                ~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "0 errors, 1 warnings\n";

        //noinspection all // Sample code
        lint().files(
                java("src/main/java/Private.java", ""
                        + "public class Private {\n"
                        + "    void test() {\n"
                        + "        int x = R.string.my_private_string; // ERROR\n"
                        + "        int y = R.string.my_public_string; // OK\n"
                        + "        int z = android.R.string.my_private_string; // OK (not in project namespace)\n"
                        + "    }\n"
                        + "    public static final class R {\n"
                        + "        public static final class string {\n"
                        + "            public static final int my_private_string = 0x7f0a0000;\n"
                        + "            public static final int my_public_string = 0x7f0a0001;\n"
                        + "        }\n"
                        + "    }\n"
                        + "}\n"),
                gradle(""
                        + "apply plugin: 'com.android.application'\n"
                        + "\n"
                        + "dependencies {\n"
                        + "    compile 'com.android.tools:test-library:1.0.0'\n"
                        + "}\n"))
                .modifyGradleMocks(mockModifier)
                .allowCompilationErrors()
                .run()
                .expect(expected);
    }

    public void testStyle() throws Exception {
        // Regression test for https://code.google.com/p/android/issues/detail?id=221560
        lint().files(
                xml("res/layout/private2.xml", ""
                        + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                        + "<merge xmlns:android=\"http://schemas.android.com/apk/res/android\">\n"
                        + "\n"
                        + "    <View\n"
                        + "        android:layout_width=\"match_parent\"\n"
                        + "        android:layout_height=\"match_parent\"\n"
                        + "        android:theme=\"@style/Theme.AppCompat.DayNight\" />\n"
                        + "\n"
                        + "    <View\n"
                        + "        android:layout_width=\"match_parent\"\n"
                        + "        android:layout_height=\"match_parent\"\n"
                        + "        android:theme=\"@style/Theme_AppCompat_DayNight\" />\n"
                        + "\n"
                        + "</merge>\n"),
                gradle(""
                        + "apply plugin: 'com.android.application'\n"
                        + "\n"
                        + "dependencies {\n"
                        + "    compile 'com.android.tools:test-library:1.0.0'\n"
                        + "}\n"))
                .modifyGradleMocks(mockModifier)
                .run()
                .expectClean();
    }

    public void testOverride() throws Exception {
        String expected = ""
                + "res/layout/my_private_layout.xml: Warning: Overriding @layout/my_private_layout which is marked as private in com.android.tools:test-library. If deliberate, use tools:override=\"true\", otherwise pick a different name. [PrivateResource]\n"
                + "res/values/strings.xml:5: Warning: Overriding @string/my_private_string which is marked as private in com.android.tools:test-library. If deliberate, use tools:override=\"true\", otherwise pick a different name. [PrivateResource]\n"
                + "    <string name=\"my_private_string\">String 1</string>\n"
                + "                  ~~~~~~~~~~~~~~~~~\n"
                + "res/values/strings.xml:9: Warning: Overriding @string/my_private_string which is marked as private in com.android.tools:test-library. If deliberate, use tools:override=\"true\", otherwise pick a different name. [PrivateResource]\n"
                + "    <item type=\"string\" name=\"my_private_string\">String 1</item>\n"
                + "                              ~~~~~~~~~~~~~~~~~\n"
                + "res/values/strings.xml:12: Warning: Overriding @string/my_private_string which is marked as private in com.android.tools:test-library. If deliberate, use tools:override=\"true\", otherwise pick a different name. [PrivateResource]\n"
                + "    <string tools:override=\"false\" name=\"my_private_string\">String 2</string>\n"
                + "                                         ~~~~~~~~~~~~~~~~~\n"
                + "0 errors, 4 warnings\n";

        lint().files(
                xml("res/values/strings.xml", ""
                        + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                        + "<resources xmlns:tools=\"http://schemas.android.com/tools\">\n"
                        + "\n"
                        + "    <string name=\"app_name\">LibraryProject</string>\n"
                        + "    <string name=\"my_private_string\">String 1</string>\n"
                        + "    <string name=\"my_public_string\">String 2</string>\n"
                        + "    <string name=\"string3\"> @my_private_string </string>\n"
                        + "    <string name=\"string4\"> @my_public_string </string>\n"
                        + "    <item type=\"string\" name=\"my_private_string\">String 1</item>\n"
                        + "    <dimen name=\"my_private_string\">String 1</dimen>\n" // unrelated
                        + "    <string tools:ignore=\"PrivateResource\" name=\"my_private_string\">String 2</string>\n"
                        + "    <string tools:override=\"false\" name=\"my_private_string\">String 2</string>\n"
                        + "    <string tools:override=\"true\" name=\"my_private_string\">String 2</string>\n"
                        + "\n"
                        + "</resources>\n"),
                xml("res/layout/my_private_layout.xml", "<LinearLayout/>"),
                xml("res/layout/my_public_layout.xml", "<LinearLayout/>"),
                gradle(""
                        + "apply plugin: 'com.android.application'\n"
                        + "\n"
                        + "dependencies {\n"
                        + "    compile 'com.android.tools:test-library:1.0.0'\n"
                        + "}\n"))
                .modifyGradleMocks(mockModifier)
                .run()
                .expect(expected);
    }

    @SuppressWarnings("ClassNameDiffersFromFileName")
    public void testIds() throws Exception {
        // Regression test for https://code.google.com/p/android/issues/detail?id=183851
        //noinspection all // Sample code
        lint().files(
                xml("res/layout/private.xml", ""
                        + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                        + "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                        + "              android:id=\"@+id/title\"\n"
                        + "              android:orientation=\"vertical\"\n"
                        + "              android:layout_width=\"match_parent\"\n"
                        + "              android:layout_height=\"match_parent\"/>\n"),
                java("src/main/java/test/pkg/Private.java", ""
                        + "package test.pkg;\n"
                        + "public class Private {\n"
                        + "    void test() {\n"
                        + "        int x = R.id.title; // ERROR\n"
                        + "    }\n"
                        + "    public static final class R {\n"
                        + "        public static final class id {\n"
                        + "            public static final int title = 0x7f0a0000;\n"
                        + "        }\n"
                        + "    }\n"
                        + "}\n"),
                gradle(""
                        + "apply plugin: 'com.android.application'\n"
                        + "\n"
                        + "dependencies {\n"
                        + "    compile 'com.android.tools:test-library:1.0.0'\n"
                        + "}\n"))
                .modifyGradleMocks(mockModifier)
                .run()
                .expectClean();
    }
}
