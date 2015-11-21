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

package com.android.utils;

/**
 * Utilities for working with ASM.
 */
public class AsmUtils {
    private AsmUtils() {}

    public static final String CONSTRUCTOR = "<init>";
    public static final String CLASS_INITIALIZER = "<clinit>";

    /**
     * Converts a class name from the Java language naming convention (foo.bar.baz) to the JVM
     * internal naming convention (foo/bar/baz).
     */
    public static String toInternalName(String className) {
        return className.replace('.', '/');
    }

    public static String getFullMemberName(String className, String memberName, String desc) {
        return String.format("%s.%s:%s", className, memberName, desc);
    }
}
