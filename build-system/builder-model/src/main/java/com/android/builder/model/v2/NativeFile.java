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

package com.android.builder.model.v2;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import java.io.File;

/**
 * A native source file with compile settings.
 */
public interface NativeFile {

    /** The source file. */
    @NonNull
    File getFilePath();

    /** The name of a {@link NativeSettings} for the source file. */
    @NonNull
    String getSettingsName();

    /** The working directory for the compiler. */
    @Nullable
    File getWorkingDirectory();
}
