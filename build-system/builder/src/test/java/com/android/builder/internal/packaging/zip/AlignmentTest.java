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

import static junit.framework.TestCase.assertEquals;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.android.utils.FileUtils;
import com.google.common.base.Charsets;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.util.Random;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class AlignmentTest {
    @Rule
    public TemporaryFolder mTemporaryFolder = new TemporaryFolder();

    @Test
    public void addAlignedFile() throws Exception {
        File newZFile = new File(mTemporaryFolder.getRoot(), "test.zip");

        byte testBytes[] = "This is some text.".getBytes(Charsets.US_ASCII);

        ZFile zf = new ZFile(newZFile);
        zf.getAlignmentRules().add(new AlignmentRule(Pattern.compile(".*\\.txt"), 1024));
        zf.add("test.txt", new ByteArrayInputStream(testBytes), false);
        zf.close();

        byte found[] = FileUtils.readSegment(newZFile, 1024, testBytes.length);
        assertArrayEquals(testBytes, found);
    }

    @Test
    public void addNonAlignedFile() throws Exception {
        File newZFile = new File(mTemporaryFolder.getRoot(), "test.zip");

        byte testBytes[] = "This is some text.".getBytes(Charsets.US_ASCII);

        ZFile zf = new ZFile(newZFile);
        zf.getAlignmentRules().add(new AlignmentRule(Pattern.compile(".*\\.txt"), 1024));
        zf.add("test.txt.foo", new ByteArrayInputStream(testBytes), false);
        zf.close();

        assertTrue(newZFile.length() < 1024);
    }

    @Test
    public void realignSingleFile() throws Exception {
        File newZFile = new File(mTemporaryFolder.getRoot(), "test.zip");

        byte testBytes0[] = "Text number 1".getBytes(Charsets.US_ASCII);
        byte testBytes1[] = "Text number 2, which is actually 1".getBytes(Charsets.US_ASCII);

        ZFile zf = new ZFile(newZFile);
        zf.add("file1.txt", new ByteArrayInputStream(testBytes1), false);
        zf.add("file0.txt", new ByteArrayInputStream(testBytes0), false);
        zf.close();

        StoredEntry se0 = zf.get("file0.txt");
        assertNotNull(se0);
        long offset0 = se0.getCentralDirectoryHeader().getOffset();

        StoredEntry se1 = zf.get("file1.txt");
        assertNotNull(se1);

        assertTrue(newZFile.length() < 1024);

        zf.getAlignmentRules().add(new AlignmentRule(Pattern.compile(".*\\.txt"), 1024));
        se1.realign();
        zf.close();

        se0 = zf.get("file0.txt");
        assertNotNull(se0);
        assertEquals(offset0, se0.getCentralDirectoryHeader().getOffset());

        se1 = zf.get("file1.txt");
        assertNotNull(se1);
        assertTrue(se1.getCentralDirectoryHeader().getOffset() > 950);
        assertTrue(se1.getCentralDirectoryHeader().getOffset() < 1024);
        assertArrayEquals(testBytes1, FileUtils.readSegment(newZFile, 1024, testBytes1.length));

        assertTrue(newZFile.length() > 1024);
    }

    @Test
    public void realignFile() throws Exception {
        File newZFile = new File(mTemporaryFolder.getRoot(), "test.zip");

        byte testBytes0[] = "Text number 1".getBytes(Charsets.US_ASCII);
        byte testBytes1[] = "Text number 2, which is actually 1".getBytes(Charsets.US_ASCII);

        ZFile zf = new ZFile(newZFile);
        zf.add("file0.txt", new ByteArrayInputStream(testBytes0), false);
        zf.add("file1.txt", new ByteArrayInputStream(testBytes1), false);
        zf.close();

        assertTrue(newZFile.length() < 1024);

        zf.getAlignmentRules().add(new AlignmentRule(Pattern.compile(".*\\.txt"), 1024));
        zf.realign();
        zf.close();

        StoredEntry se0 = zf.get("file0.txt");
        assertNotNull(se0);
        long off0 = 1024;

        StoredEntry se1 = zf.get("file1.txt");
        assertNotNull(se1);
        long off1 = 2048;

        /*
         * ZFile does not guarantee any order.
         */
        if (se1.getCentralDirectoryHeader().getOffset() <
                se0.getCentralDirectoryHeader().getOffset()) {
            off0 = 2048;
            off1 = 1024;
        }

        assertArrayEquals(testBytes0, FileUtils.readSegment(newZFile, off0, testBytes0.length));
        assertArrayEquals(testBytes1, FileUtils.readSegment(newZFile, off1, testBytes1.length));
    }

    @Test
    public void realignAlignedEntry() throws Exception {
        File newZFile = new File(mTemporaryFolder.getRoot(), "test.zip");

        byte testBytes[] = "This is some text.".getBytes(Charsets.US_ASCII);

        ZFile zf = new ZFile(newZFile);
        zf.getAlignmentRules().add(new AlignmentRule(Pattern.compile(".*\\.txt"), 1024));
        zf.add("test.txt", new ByteArrayInputStream(testBytes), false);
        zf.close();

        assertArrayEquals(testBytes, FileUtils.readSegment(newZFile, 1024, testBytes.length));

        int flen = (int) newZFile.length();

        StoredEntry entry = zf.get("test.txt");
        assertNotNull(entry);
        assertFalse(entry.realign());

        zf.close();
        assertEquals(flen, (int) newZFile.length());
        assertArrayEquals(testBytes, FileUtils.readSegment(newZFile, 1024, testBytes.length));
    }

    @Test
    public void alignmentRulesDoNotAffectAddedFiles() throws Exception {
        File newZFile = new File(mTemporaryFolder.getRoot(), "test.zip");

        byte testBytes0[] = "Text number 1".getBytes(Charsets.US_ASCII);
        byte testBytes1[] = "Text number 2, which is actually 1".getBytes(Charsets.US_ASCII);

        ZFile zf = new ZFile(newZFile);
        zf.add("file0.txt", new ByteArrayInputStream(testBytes0), false);
        zf.finishAllBackgroundTasks();
        zf.getAlignmentRules().add(new AlignmentRule(Pattern.compile(".*\\.txt"), 1024));
        zf.add("file1.txt", new ByteArrayInputStream(testBytes1), false);
        zf.close();

        StoredEntry se0 = zf.get("file0.txt");
        assertNotNull(se0);
        assertEquals(0, se0.getCentralDirectoryHeader().getOffset());

        StoredEntry se1 = zf.get("file1.txt");
        assertNotNull(se1);
        assertArrayEquals(testBytes1, FileUtils.readSegment(newZFile, 1024, testBytes1.length));
    }

    @Test
    public void realignStreamedZip() throws Exception {
        File zipFile = new File(mTemporaryFolder.getRoot(), "test.zip");

        byte[] pattern = new byte[1024];
        new Random().nextBytes(pattern);

        String name = "";
        ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipFile));
        try {
            for (int j = 0; j < 10; j++) {
                name = name + "a";
                ZipEntry ze = new ZipEntry(name);
                zos.putNextEntry(ze);
                for (int i = 0; i < 1000; i++) {
                    zos.write(pattern);
                }
            }
        } finally {
            zos.close();
        }

        ZFile zf = new ZFile(zipFile);
        zf.getAlignmentRules().add(new AlignmentRule(Pattern.compile(".*"), 10));
        zf.realign();
        zf.close();
    }
}
