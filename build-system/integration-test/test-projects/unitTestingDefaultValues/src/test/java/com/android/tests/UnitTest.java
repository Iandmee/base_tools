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

package com.android.tests;

import static org.junit.Assert.*;

import android.util.ArrayMap;
import android.os.Debug;
import org.junit.Test;

public class UnitTest {
    @Test
    public void defaultValues() {
        ArrayMap map = new ArrayMap();

        // Check different return types.
        map.clear();
        assertEquals(0, map.size());
        assertEquals(false, map.isEmpty());
        assertNull(map.keySet());

        // Check a static method as well.
        assertEquals(0, Debug.getGlobalAllocCount());
    }
}
