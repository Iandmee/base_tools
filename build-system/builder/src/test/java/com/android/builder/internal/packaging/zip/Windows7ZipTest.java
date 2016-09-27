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

public class Windows7ZipTest {

    private static final String FILE_NAME = "windows-7zip.zip";
    private static final int NUM_ENTRIES = 6;
    private static final ImmutableList<String> COMMAND =
            ImmutableList.of("c:\\Program Files\\7-Zip\\7z.exe", "l");
    private static final String REGEX =
            "^(?:\\S+\\s+){3}(?<size>\\d+)\\s+\\d+\\s+(?<name>\\S+)\\s*$";

    @Rule public final ZipToolsTester mZipToolsTester = new ZipToolsTester();

    @Test
    public void zfileReadsZipFile() throws Exception {
        mZipToolsTester.zfileReadsZipFile(FILE_NAME, NUM_ENTRIES);
    }

    @Test
    public void toolReadsZfFile() throws Exception {
        mZipToolsTester.toolReadsZfFile(COMMAND, REGEX);
    }

    @Test
    public void toolReadsAlignedZfFile() throws Exception {
        mZipToolsTester.toolReadsAlignedZfFile(COMMAND, REGEX);
    }
}
