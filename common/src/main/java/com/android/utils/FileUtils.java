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

package com.android.utils;

import static com.google.common.base.Preconditions.checkArgument;

import com.android.annotations.NonNull;
import com.google.common.base.Charsets;
import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.Iterables;
import com.google.common.hash.Hashing;
import com.google.common.io.Files;

import java.io.File;
import java.io.IOException;

public final class FileUtils {

    private static final Joiner PATH_JOINER = Joiner.on(File.separatorChar);
    private static final Joiner COMMA_SEPARATED_JOINER = Joiner.on(", ");
    private static final Joiner UNIX_NEW_LINE_JOINER = Joiner.on('\n');

    public static void deleteFolder(@NonNull final File folder) throws IOException {
        if (!folder.exists()) {
            return;
        }
        File[] files = folder.listFiles();
        if (files != null) { // i.e. is a directory.
            for (final File file : files) {
                deleteFolder(file);
            }
        }
        if (!folder.delete()) {
            throw new IOException(String.format("Could not delete folder %s", folder));
        }
    }

    public static void emptyFolder(@NonNull final File folder) throws IOException {
        deleteFolder(folder);
        if (!folder.mkdirs()) {
            throw new IOException(String.format("Could not create empty folder %s", folder));
        }
    }

    public static void copyFile(@NonNull final File from, @NonNull final File toDir)
            throws IOException {
        File to = new File(toDir, from.getName());
        if (from.isDirectory()) {
            if (!to.exists()) {
                if (!to.mkdirs()) {
                    throw new IOException(String.format("Could not create directory %s", to));
                }
            }

            File[] children = from.listFiles();
            if (children != null) {
                for (File child : children) {
                    copyFile(child, to);
                }
            }
        } else if (from.isFile()) {
            Files.copy(from, to);
        }
    }

    public static void mkdirs(@NonNull File folder) {
        if (!folder.exists() && !folder.mkdirs()) {
            throw new RuntimeException("Cannot create directory " + folder);
        }
    }

    public static void delete(@NonNull File file) throws IOException {
        boolean result = file.delete();
        if (!result) {
            throw new IOException("Failed to delete " + file.getAbsolutePath());
        }
    }

    public static void deleteIfExists(@NonNull File file) throws IOException {
        boolean result = file.delete();
        if (!result && file.exists()) {
            throw new IOException("Failed to delete " + file.getAbsolutePath());
        }
    }

    @NonNull
    public static File join(@NonNull File dir, @NonNull String... paths) {
        return new File(dir, PATH_JOINER.join(paths));
    }

    @NonNull
    public static String join(@NonNull String... paths) {
        return PATH_JOINER.join(paths);
    }

    /**
     * Loads a text file forcing the line separator to be of Unix style '\n' rather than being
     * Windows style '\r\n'.
     */
    @NonNull
    public static String loadFileWithUnixLineSeparators(@NonNull File file) throws IOException {
        return UNIX_NEW_LINE_JOINER.join(Files.readLines(file, Charsets.UTF_8));
    }

    @NonNull
    public static String relativePath(@NonNull File file, @NonNull File dir) {
        checkArgument(file.isFile(), "%s is not a file.", file.getPath());
        checkArgument(dir.isDirectory(), "%s is not a directory.", dir.getPath());

        String path = dir.toURI().relativize(file.toURI()).getPath();

        if (File.separatorChar != '/') {
            path = path.replace('/', File.separatorChar);
        }

        return path;
    }

    @NonNull
    public static String sha1(@NonNull File file) throws IOException {
        return Hashing.sha1().hashBytes(Files.toByteArray(file)).toString();
    }

    @NonNull
    public static FluentIterable<File> getAllFiles(@NonNull File dir) {
        return Files.fileTreeTraverser().preOrderTraversal(dir).filter(Files.isFile());
    }

    @NonNull
    public static String getNamesAsCommaSeparatedList(@NonNull Iterable<File> files) {
        return COMMA_SEPARATED_JOINER.join(Iterables.transform(files, GET_NAME));
    }

    private static final Function<File, String> GET_NAME = new Function<File, String>() {
        @Override
        public String apply(File file) {
            return file.getName();
        }
    };
}
