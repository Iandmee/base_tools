/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.build.gradle.internal.pipeline;

import static org.hamcrest.CoreMatchers.containsString;

import com.google.common.collect.ImmutableSet;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class BitMaskTestUtilsTest {

    @Rule
    public ExpectedException exception = ExpectedException.none();

    @Test
    public void checkValidCase() {
        BitMaskTestUtils.checkScopeBitMaskUnique(ImmutableSet.of(1,2,4,8,16,32), Integer::intValue);
    }

    @Test
    public void checkInvalidCase() {
        exception.expect(AssertionError.class);
        exception.expectMessage(containsString("34 [100010]"));
        exception.expectMessage(containsString("2 [10]"));
        exception.expectMessage(containsString("are not unique"));
        BitMaskTestUtils.checkScopeBitMaskUnique(ImmutableSet.of(1,2,4,8,16,34), Integer::intValue);
    }

    @Test
    public void checkEmptyMask() {
        exception.expect(AssertionError.class);
        exception.expectMessage(containsString("Bit mask for 0 is zero"));
        BitMaskTestUtils.checkScopeBitMaskUnique(ImmutableSet.of(0), Integer::intValue);
    }

}
