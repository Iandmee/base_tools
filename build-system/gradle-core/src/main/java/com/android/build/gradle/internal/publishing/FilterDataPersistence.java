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

package com.android.build.gradle.internal.publishing;

import com.android.build.gradle.internal.tasks.FileSupplier;
import com.android.build.gradle.internal.tasks.SplitFileSupplier;
import com.google.common.collect.ImmutableList;
import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.util.List;

/**
 * Persistence utility to save metadata about split files generated by the build. This metadata will
 * be the split type, the split identifier and the resulting split APK file name.
 */
public class FilterDataPersistence {

    private final String packageId;
    private final List<Record> records;

    public FilterDataPersistence(String packageId,
            ImmutableList<Record> records) {
        this.packageId = packageId;
        this.records = records;
    }

    public String getPackageId() {
        return packageId;
    }

    public ImmutableList<Record> getFilterData() {
        return ImmutableList.copyOf(records);
    }

    public static class Record {
        public final String filterType;
        public final String filterIdentifier;
        public final String splitFileName;

        private Record(String filterType, String filterIdentifier, String splitFileName) {
            this.filterType = filterType;
            this.filterIdentifier = filterIdentifier;
            this.splitFileName = splitFileName;
        }
    }

    public static void persist(
            String packageID,
            List<? extends FileSupplier> fileSuppliers,
            Writer writer) throws IOException {
        Gson gson = new Gson();
        ImmutableList.Builder<Record> records = ImmutableList.builder();
        for (FileSupplier fileSupplier : fileSuppliers) {
            if (fileSupplier instanceof SplitFileSupplier) {
                records.add(new Record(
                        ((SplitFileSupplier) fileSupplier).getFilterData().getFilterType(),
                        ((SplitFileSupplier) fileSupplier).getFilterData().getIdentifier(),
                        fileSupplier.get().getName()));
            }
        }
        String recordsAsString = gson.toJson(
                new FilterDataPersistence(packageID, records.build()));
        try {
            writer.append(recordsAsString);
        } finally {
            writer.close();
        }
    }

    public static FilterDataPersistence load(Reader reader) throws IOException {
        Gson gson = new Gson();
        Type recordType = new TypeToken<FilterDataPersistence>() {}.getType();
        return gson.fromJson(reader, recordType);
    }
}
