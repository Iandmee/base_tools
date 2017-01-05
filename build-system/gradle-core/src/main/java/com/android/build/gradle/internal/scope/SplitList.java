/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.build.gradle.internal.scope;

import com.android.annotations.NonNull;
import com.android.build.OutputFile;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import org.apache.commons.io.FileUtils;
import org.gradle.api.file.FileCollection;

/**
 * Singleton object per variant that holds the list of splits declared by the DSL or discovered.
 */
public class SplitList {

    /**
     * FileCollection for a single file with the persisted split list
     */
    private final FileCollection persistedList;

    /**
     * Split list cache, valid only during this build.
     */
    private ImmutableList<Record> records;

    public SplitList(FileCollection persistedList) {
        this.persistedList = persistedList;
    }

    public FileCollection getFileCollection() {
        return persistedList;
    }

    public synchronized Set<String> getFilters(OutputFile.FilterType splitType) throws IOException {
        if (records == null) {
            String gson = FileUtils.readFileToString(persistedList.getSingleFile());
            load(gson);
        }
        Optional<Record> record = records.stream()
                .filter(r -> r.splitType.equals(splitType.name())).findFirst();
        return record.isPresent()
                ? record.get().values
                : ImmutableSet.of();
    }

    public Set<String> getResourcesSplit() throws IOException {
        ImmutableSet.Builder<String> allFilters = ImmutableSet.builder();
        allFilters.addAll(getFilters(OutputFile.FilterType.DENSITY));
        allFilters.addAll(getFilters(OutputFile.FilterType.LANGUAGE));
        return allFilters.build();
    }

    public synchronized void save(
            @NonNull File outputFile,
            @NonNull Set<String> densityFilters,
            @NonNull Set<String> languageFilters,
            @NonNull Set<String> abiFilters) throws IOException {

        records = ImmutableList.of(
                new Record(OutputFile.FilterType.DENSITY, densityFilters),
                new Record(OutputFile.FilterType.LANGUAGE, languageFilters),
                new Record(OutputFile.FilterType.ABI, abiFilters));

        Gson gson = new Gson();
        String listOfFilters = gson.toJson(records);
        FileUtils.write(outputFile, listOfFilters);
    }

    @SuppressWarnings("unchecked")
    private void load(String persistedData) throws IOException {
        Gson gson = new Gson();
        Type collectionType = new TypeToken<ArrayList<Record>>(){}.getType();
        records = ImmutableList.copyOf(
                (Collection<? extends Record>) gson.fromJson(persistedData, collectionType));
    }

    /**
     * Internal records to save split names and types.
     */
    private static final class Record {
        private final String splitType;
        private final Set<String> values;

        private Record(OutputFile.FilterType splitType, Set<String> values) {
            this.splitType = splitType.name();
            this.values = values;
        }
    }
}
