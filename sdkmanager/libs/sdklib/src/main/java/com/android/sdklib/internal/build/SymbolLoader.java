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

package com.android.sdklib.internal.build;

import com.google.common.base.Charsets;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;
import com.google.common.io.Files;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 */
public class SymbolLoader {

    private final File mSymbolFile;
    private Table<String, String, SymbolEntry> mSymbols;

    public static class SymbolEntry {
        private final String mName;
        private final String mType;
        private final String mValue;

        public SymbolEntry(String name, String type, String value) {
            mName = name;
            mType = type;
            mValue = value;
        }

        public String getValue() {
            return mValue;
        }

        public String getName() {
            return mName;
        }

        public String getType() {
            return mType;
        }
    }

    public SymbolLoader(File symbolFile) {
        mSymbolFile = symbolFile;
    }

    public void load() throws IOException {
        List<String> lines = Files.readLines(mSymbolFile, Charsets.UTF_8);

        mSymbols = HashBasedTable.create();

        String currentLine = "";
        try {
            for (String line : lines) {
                currentLine = line;
                // format is "<type> <class> <name> <value>"
                // don't want to split on space as value could contain spaces.
                int pos = line.indexOf(' ');
                String type = line.substring(0, pos);
                int pos2 = line.indexOf(' ', pos + 1);
                String className = line.substring(pos + 1, pos2);
                int pos3 = line.indexOf(' ', pos2 + 1);
                String name = line.substring(pos2 + 1, pos3);
                String value = line.substring(pos3 + 1);

                mSymbols.put(className, name, new SymbolEntry(name, type, value));
            }
        } catch (Exception e) {
            // Catch both ArrayIndexOutOfBoundsException and StringIndexOutOfBoundsException
            throw new IOException("File format error reading " + mSymbolFile.getAbsolutePath()
                    + ": " + currentLine, e);
        }
    }

    Table<String, String, SymbolEntry> getSymbols() {
        return mSymbols;
    }
}
