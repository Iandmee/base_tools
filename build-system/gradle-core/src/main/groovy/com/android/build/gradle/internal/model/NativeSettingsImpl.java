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

package com.android.build.gradle.internal.model;

import com.android.annotations.NonNull;
import com.android.builder.model.NativeSettings;

import java.io.Serializable;
import java.util.List;

/**
 * Implementation of {@link NativeSettings}.
 */
public class NativeSettingsImpl implements NativeSettings, Serializable {
    private static final long serialVersionUID = 1L;

    @NonNull
    String name;
    @NonNull
    List<String> compilerFlags;

    public NativeSettingsImpl(@NonNull String name, @NonNull List<String> compilerFlags) {
        this.name = name;
        this.compilerFlags = compilerFlags;
    }

    @Override
    @NonNull
    public String getName() {
        return name;
    }

    @Override
    @NonNull
    public List<String> getCompilerFlags() {
        return compilerFlags;
    }
}
