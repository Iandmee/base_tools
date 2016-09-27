/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.builder.internal.packaging.zip;

import com.google.common.collect.ImmutableList;

import org.junit.Rule;
import org.junit.Test;

public class LinuxUnzipTest {

    private static final String FILE_NAME = "linux-zip.zip";
    private static final boolean TOOL_STORES_DIRECTORIES = true;
    private static final ImmutableList<String> COMMAND = ImmutableList.of("/usr/bin/unzip", "-v");
    private static final String REGEX =
            "^\\s*(?<size>\\d+)\\s+(?:Stored|Defl:N).*\\s(?<name>\\S+)\\S*$";

    @Rule public final ZipToolsTester mZipToolsTester =
            new ZipToolsTester(FILE_NAME, COMMAND, REGEX, TOOL_STORES_DIRECTORIES);

    @Test
    public void zfileReadsZipFile() throws Exception {
        mZipToolsTester.zfileReadsZipFile();
    }

    @Test
    public void toolReadsZfFile() throws Exception {
        mZipToolsTester.toolReadsZfFile();
    }

    @Test
    public void toolReadsAlignedZfFile() throws Exception {
        mZipToolsTester.toolReadsAlignedZfFile();
    }
}
