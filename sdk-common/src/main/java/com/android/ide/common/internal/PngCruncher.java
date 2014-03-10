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

package com.android.ide.common.internal;

import com.android.annotations.NonNull;

import java.io.File;
import java.io.IOException;

/**
 * An object able to crunch a png.
 */
public interface PngCruncher {

    /**
     * Crunch a given file into another given file.
     *
     *
     * @param from the file to crunch
     * @param to the output file
     *
     * @throws java.io.IOException
     * @throws InterruptedException
     * @throws com.android.ide.common.internal.LoggedErrorException
     */
    void crunchPng(@NonNull File from, @NonNull File to)
            throws InterruptedException, LoggedErrorException, IOException;
}
