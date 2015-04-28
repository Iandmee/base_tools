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

package com.android.ide.common.res2;

import com.android.annotations.NonNull;

import java.io.File;

/**
 * {@link DataFile} for preprocessing resources.
 *
 * <p>Source is the "input" resource from the merged resources directory. Data items can be one of:
 * <ul>
 *     <li>The same file as source, if there's no need to preprocess it.
 *     <li>One or more generated "replacement" files, from the generated resources directory.
*  </ul>
 */
public class PreprocessDataFile extends DataFile<PreprocessDataItem> {
    PreprocessDataFile(@NonNull File file, FileType fileType) {
        super(file, fileType);
    }
}
