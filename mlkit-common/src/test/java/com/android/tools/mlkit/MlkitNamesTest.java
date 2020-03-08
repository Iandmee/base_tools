/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.tools.mlkit;

import static org.junit.Assert.assertEquals;

import java.io.File;
import org.junit.Test;

public class MlkitNamesTest {

    @Test
    public void computeModelClassName_validName_justReturn() {
        assertEquals(
                MlkitNames.computeModelClassName(new File("valid_model.tflite")), "ValidModel");
    }

    @Test
    public void computeModelClassName_nameWithInvalidCharacters_correctIt() {
        assertEquals(
                MlkitNames.computeModelClassName(new File(" valid_model%$.tflite")), "ValidModel");
    }

    @Test
    public void computeModelClassName_nameAllWithInvalidCharacters_returnModelWithHashcode() {
        assertEquals(MlkitNames.computeModelClassName(new File(" %$.tflite")), "Model40");
    }

    @Test
    public void computeModelClassName_nameStartWithDigit_correctIt() {
        assertEquals(MlkitNames.computeModelClassName(new File("012.tflite")), "Model012");
    }

    @Test
    public void computeModelClassName_sameFileInDifferentDir_returnSameName() {
        File file1 = new File("dir1", "model.tflite");
        File file2 = new File("dir2", "model.tflite");

        assertEquals(
                MlkitNames.computeModelClassName(file1), MlkitNames.computeModelClassName(file2));
    }
}
