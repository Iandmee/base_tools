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

package com.android.tools.chunkio.codegen;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

final class Utils {
    private Utils() {
    }

    static <T> List<T> immutableCopy(List<T> list) {
        return Collections.unmodifiableList(new ArrayList<>(list));
    }

    static <T> Set<T> immutableCopy(Set<T> set) {
        return Collections.unmodifiableSet(new LinkedHashSet<>(set));
    }
}
