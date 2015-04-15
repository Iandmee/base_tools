/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.build.gradle.internal.dsl;

import static com.android.build.OutputFile.NO_FILTER;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.OutputFile;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;

import java.util.Set;

/**
 * Data for per-ABI splits.
 */
public class AbiSplitOptions extends SplitOptions {

    private static final String[] ABI_LIST = new String[] {
            "armeabi", "armeabi-v7a", "arm64-v8a", "x86", "x86_64", "mips", "mips64" };

    private boolean universalApk = false;

    @Override
    protected Set<String> getDefaultValues() {
        return Sets.newHashSet(ABI_LIST);
    }

    @Override
    protected ImmutableSet<String> getAllowedValues() {
        return ImmutableSet.copyOf(ABI_LIST);
    }

    /**
     * Whether to create an APK with all available ABIs.
     */
    public boolean isUniversalApk() {
        return universalApk;
    }

    public void setUniversalApk(boolean universalApk) {
        this.universalApk = universalApk;
    }

    @NonNull
    @Override
    public Set<String> getApplicableFilters() {
        Set<String> list = super.getApplicableFilters();

        // if universal, and splitting enabled, then add an entry with no filter.
        if (isEnable() && universalApk) {
            list.add(NO_FILTER);
        }

        return list;
    }

    /**
     * Returns the list of actual abi filters, each value of the collection is guaranteed to be non
     * null and of the possible API value.
     * @param allFilters list of applicable filters {@see #getApplicationFilters}
     */
    @NonNull
    public static ImmutableSet<String> getAbiFilters(@NonNull Set<String> allFilters) {
        ImmutableSet.Builder<String> filters = ImmutableSet.builder();
        for (@Nullable String abi : allFilters) {
            // use object equality since abi can be null.
            //noinspection StringEquality
            if (abi != OutputFile.NO_FILTER) {
                filters.add(abi);
            }
        }
        return filters.build();
    }
}
