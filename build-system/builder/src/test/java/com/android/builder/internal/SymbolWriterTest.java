/*
 * Copyright (C) 2012 The Android Open Source Project
 *
 * Licensed under the Eclipse Public License, Version 1.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.eclipse.org/org/documents/epl-v10.php
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.builder.internal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import com.android.utils.NullLogger;
import com.google.common.base.Charsets;
import com.google.common.collect.Lists;
import com.google.common.collect.Table;
import com.google.common.io.Files;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.util.List;

@SuppressWarnings("javadoc")
public class SymbolWriterTest {
    @Rule
    public TemporaryFolder mTemporaryFolder = new TemporaryFolder();

    private void check(String packageName, String rJava, String rValues, String... rTexts)
            throws Exception {
        if (rValues == null) {
            if (rTexts.length == 1) {
                rValues = rTexts[0];
            } else {
                throw new IllegalArgumentException(
                        "Can't have a null rValues with rTexts.length!=1");
            }
        }

        // Load the symbol values
        // 1. write rText in a temp file
        File file = File.createTempFile(getClass().getSimpleName(), "txt");
        file.deleteOnExit();
        Files.write(rValues, file, Charsets.UTF_8);
        // 2. load symbol from temp file.
        SymbolLoader symbolValues = new SymbolLoader(file, NullLogger.getLogger());
        symbolValues.load();
        Table<String, String, SymbolLoader.SymbolEntry> values = symbolValues.getSymbols();
        assertNotNull(values);


        // Load the symbols to write
        List<SymbolLoader> symbolList = Lists.newArrayListWithCapacity(rTexts.length);
        for (String rText : rTexts) {
            // 1. write rText in a temp file
            file = File.createTempFile(getClass().getSimpleName(), "txt");
            file.deleteOnExit();
            Files.write(rText, file, Charsets.UTF_8);
            // 2. load symbol from temp file.
            SymbolLoader loader = new SymbolLoader(file, NullLogger.getLogger());
            loader.load();
            Table<String, String, SymbolLoader.SymbolEntry> symbols = loader.getSymbols();
            assertNotNull(symbols);
            symbolList.add(loader);
        }

        // Write symbols
        File outFolder = mTemporaryFolder.newFolder();

        SymbolWriter writer = new SymbolWriter(outFolder.getPath(), packageName, symbolValues);
        for (SymbolLoader symbolLoader : symbolList) {
            writer.addSymbolsToWrite(symbolLoader);
        }
        writer.write();

        String contents = Files.toString(new File(outFolder,
                packageName.replace('.',  File.separatorChar) + File.separator + "R.java"),
                Charsets.UTF_8);

        // Ensure we wrote what was expected
        assertEquals(rJava, contents.replaceAll("\t", "    "));
    }

    @Test
    public void test1() throws Exception {
        check(
            // Package
            "test.pkg",

            // R.java
            "/* AUTO-GENERATED FILE.  DO NOT MODIFY.\n" +
            " *\n" +
            " * This class was automatically generated by the\n" +
            " * aapt tool from the resource data it found.  It\n" +
            " * should not be modified by hand.\n" +
            " */\n" +
            "package test.pkg;\n" +
            "\n" +
            "public final class R {\n" +
            "    public static final class xml {\n" +
            "        public static final int authenticator = 0x7f040000;\n" +
            "    }\n" +
            "}\n",

            // R values
            null,

            // R.txt
            "int xml authenticator 0x7f040000\n"
        );
    }

    @Test
    public void test2() throws Exception {
        check(
            // Package
            "test.pkg",

            // R.java
            "/* AUTO-GENERATED FILE.  DO NOT MODIFY.\n" +
            " *\n" +
            " * This class was automatically generated by the\n" +
            " * aapt tool from the resource data it found.  It\n" +
            " * should not be modified by hand.\n" +
            " */\n" +
            "package test.pkg;\n" +
            "\n" +
            "public final class R {\n" +
            "    public static final class drawable {\n" +
            "        public static final int foobar = 0x7f020000;\n" +
            "        public static final int ic_launcher = 0x7f020001;\n" +
            "    }\n" +
            "    public static final class string {\n" +
            "        public static final int app_name = 0x7f030000;\n" +
            "        public static final int lib1 = 0x7f030001;\n" +
            "    }\n" +
            "    public static final class style {\n" +
            "        public static final int AppBaseTheme = 0x7f040000;\n" +
            "        public static final int AppTheme = 0x7f040001;\n" +
            "    }\n" +
            "}\n",

            // R values
            "int drawable foobar 0x7f020000\n" +
            "int drawable ic_launcher 0x7f020001\n" +
            "int string app_name 0x7f030000\n" +
            "int string lib1 0x7f030001\n" +
            "int style AppBaseTheme 0x7f040000\n" +
            "int style AppTheme 0x7f040001\n",

            // R.txt
            "int drawable foobar 0x7fffffff\n" +
            "int drawable ic_launcher 0x7fffffff\n" +
            "int string app_name 0x7fffffff\n" +
            "int string lib1 0x7fffffff\n" +
            "int style AppBaseTheme 0x7fffffff\n" +
            "int style AppTheme 0x7fffffff\n"
        );
    }

    @Test
    public void testStyleables1() throws Exception {
        check(
            // Package
            "test.pkg",

            // R.java
            "/* AUTO-GENERATED FILE.  DO NOT MODIFY.\n" +
            " *\n" +
            " * This class was automatically generated by the\n" +
            " * aapt tool from the resource data it found.  It\n" +
            " * should not be modified by hand.\n" +
            " */\n" +
            "package test.pkg;\n" +
            "\n" +
            "public final class R {\n" +
            "    public static final class styleable {\n" +
            "        public static final int[] TiledView = { 0x7f010000, 0x7f010001, 0x7f010002, 0x7f010003, 0x7f010004 };\n" +
            "        public static final int TiledView_tileName = 2;\n" +
            "        public static final int TiledView_tilingEnum = 4;\n" +
            "        public static final int TiledView_tilingMode = 3;\n" +
            "        public static final int TiledView_tilingProperty = 0;\n" +
            "        public static final int TiledView_tilingResource = 1;\n" +
            "    }\n" +
            "}\n",

            // R values
            null,

            // R.txt
            "int[] styleable TiledView { 0x7f010000, 0x7f010001, 0x7f010002, 0x7f010003, 0x7f010004 }\n" +
            "int styleable TiledView_tileName 2\n" +
            "int styleable TiledView_tilingEnum 4\n" +
            "int styleable TiledView_tilingMode 3\n" +
            "int styleable TiledView_tilingProperty 0\n" +
            "int styleable TiledView_tilingResource 1\n"
        );
    }

    @Test
    public void testStyleables2() throws Exception {
        check(
            // Package
            "test.pkg",

            // R.java
            "/* AUTO-GENERATED FILE.  DO NOT MODIFY.\n" +
            " *\n" +
            " * This class was automatically generated by the\n" +
            " * aapt tool from the resource data it found.  It\n" +
            " * should not be modified by hand.\n" +
            " */\n" +
            "package test.pkg;\n" +
            "\n" +
            "public final class R {\n" +
            "    public static final class styleable {\n" +
            "        public static final int[] LimitedSizeLinearLayout = { 0x7f010000, 0x7f010001 };\n" +
            "        public static final int LimitedSizeLinearLayout_max_height = 1;\n" +
            "        public static final int LimitedSizeLinearLayout_max_width = 0;\n" +
            "    }\n" +
            "    public static final class xml {\n" +
            "        public static final int authenticator = 0x7f040000;\n" +
            "    }\n" +
            "}\n",

            // R values
            null,

            // R.txt
            "int[] styleable LimitedSizeLinearLayout { 0x7f010000, 0x7f010001 }\n" +
            "int styleable LimitedSizeLinearLayout_max_height 1\n" +
            "int styleable LimitedSizeLinearLayout_max_width 0\n" +
            "int xml authenticator 0x7f040000\n"
        );
    }

    @Test
    public void testMerge() throws Exception {
        check(
            // Package
            "test.pkg",

            // R.java
            "/* AUTO-GENERATED FILE.  DO NOT MODIFY.\n" +
            " *\n" +
            " * This class was automatically generated by the\n" +
            " * aapt tool from the resource data it found.  It\n" +
            " * should not be modified by hand.\n" +
            " */\n" +
            "package test.pkg;\n" +
            "\n" +
            "public final class R {\n" +
            "    public static final class drawable {\n" +
            "        public static final int foobar = 0x7f020000;\n" +
            "        public static final int ic_launcher = 0x7f020001;\n" +
            "    }\n" +
            "    public static final class string {\n" +
            "        public static final int app_name = 0x7f030000;\n" +
            "        public static final int lib1 = 0x7f030001;\n" +
            "    }\n" +
            "    public static final class style {\n" +
            "        public static final int AppBaseTheme = 0x7f040000;\n" +
            "        public static final int AppTheme = 0x7f040001;\n" +
            "    }\n" +
            "}\n",

            // R values
            "int drawable foobar 0x7f020000\n" +
            "int drawable ic_launcher 0x7f020001\n" +
            "int string app_name 0x7f030000\n" +
            "int string lib1 0x7f030001\n" +
            "int style AppBaseTheme 0x7f040000\n" +
            "int style AppTheme 0x7f040001\n",

            // R.txt 1
            "int drawable foobar 0x7fffffff\n" +
            "int drawable ic_launcher 0x7fffffff\n" +
            "int string app_name 0x7fffffff\n" +
            "int string lib1 0x7fffffff\n" +

            // R.txt 2
            "int string app_name 0x80000000\n" +
            "int string lib1 0x80000000\n" +
            "int style AppBaseTheme 0x80000000\n" +
            "int style AppTheme 0x80000000\n"
        );
    }
}
