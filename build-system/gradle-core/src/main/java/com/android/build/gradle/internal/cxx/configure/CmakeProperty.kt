/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.build.gradle.internal.cxx.configure

import com.android.build.gradle.internal.cxx.configure.CmakeProperty.*

/**
 * A subset of possible CMake properties.
 */
enum class CmakeProperty {
    ANDROID_ABI,
    ANDROID_GRADLE_BUILD_COMPILER_SETTINGS_CACHE_ENABLED,
    ANDROID_GRADLE_BUILD_COMPILER_SETTINGS_CACHE_RECORDED_VERSION,
    ANDROID_GRADLE_BUILD_COMPILER_SETTINGS_CACHE_USED,
    ANDROID_NDK,
    ANDROID_PLATFORM,
    C_TEST_WAS_RUN,
    CMAKE_ANDROID_ARCH_ABI,
    CMAKE_ANDROID_NDK,
    CMAKE_BUILD_TYPE,
    CMAKE_C11_COMPILE_FEATURES,
    CMAKE_C90_COMPILE_FEATURES,
    CMAKE_C99_COMPILE_FEATURES,
    CMAKE_C_ABI_COMPILED,
    CMAKE_C_COMPILE_FEATURES,
    CMAKE_C_COMPILER_ABI,
    CMAKE_C_COMPILER_FORCED,
    CMAKE_C_FLAGS,
    CMAKE_C_IMPLICIT_LINK_DIRECTORIES,
    CMAKE_C_IMPLICIT_LINK_LIBRARIES,
    CMAKE_C_SIZEOF_DATA_PTR,
    CMAKE_C_STANDARD_DEFAULT,
    CMAKE_CXX11_COMPILE_FEATURES,
    CMAKE_CXX14_COMPILE_FEATURES,
    CMAKE_CXX17_COMPILE_FEATURES,
    CMAKE_CXX98_COMPILE_FEATURES,
    CMAKE_CXX_ABI_COMPILED,
    CMAKE_CXX_COMPILE_FEATURES,
    CMAKE_CXX_COMPILER_ABI,
    CMAKE_CXX_COMPILER_FORCED,
    CMAKE_CXX_FLAGS,
    CMAKE_CXX_IMPLICIT_LINK_DIRECTORIES,
    CMAKE_CXX_IMPLICIT_LINK_LIBRARIES,
    CMAKE_CXX_SIZEOF_DATA_PTR,
    CMAKE_CXX_STANDARD_DEFAULT,
    CMAKE_EXPORT_COMPILE_COMMANDS,
    CMAKE_FIND_ROOT_PATH,
    CMAKE_INTERNAL_PLATFORM_ABI,
    CMAKE_LIBRARY_OUTPUT_DIRECTORY,
    CMAKE_LINKER,
    CMAKE_MAKE_PROGRAM,
    CMAKE_RUNTIME_OUTPUT_DIRECTORY,
    CMAKE_SIZEOF_VOID_P,
    CMAKE_SYSTEM_NAME,
    CMAKE_SYSTEM_VERSION,
    CMAKE_TOOLCHAIN_FILE,
    CXX_TEST_WAS_RUN
}

/**
 * These are flags that shouldn't matter when it comes to caching compiler check values.
 * In other words, they don't affect the result of CMake compiler detection.
 */
private val CMAKE_COMPILER_CHECK_CACHE_KEY_IGNORED_PROPERTY_LIST = listOf(
    CMAKE_BUILD_TYPE,
    CMAKE_EXPORT_COMPILE_COMMANDS,
    CMAKE_LIBRARY_OUTPUT_DIRECTORY,
    CMAKE_MAKE_PROGRAM,
    CMAKE_RUNTIME_OUTPUT_DIRECTORY,
    CMAKE_TOOLCHAIN_FILE
)

/**
 * String version of CMAKE_COMPILER_CHECK_CACHE_KEY_IGNORED_PROPERTY_LIST
 */
val CMAKE_COMPILER_CHECK_CACHE_KEY_IGNORED_STRING_LIST =
    CMAKE_COMPILER_CHECK_CACHE_KEY_IGNORED_PROPERTY_LIST.map { it.name }

/**
 * These are flags that are computed by compiler-checks. These are the value that is cached.
 */
private val CMAKE_COMPILER_CHECK_CACHE_VALUE_INCLUDE_LIST = listOf(
    C_TEST_WAS_RUN,
    CMAKE_C11_COMPILE_FEATURES,
    CMAKE_C90_COMPILE_FEATURES,
    CMAKE_C99_COMPILE_FEATURES,
    CMAKE_CXX11_COMPILE_FEATURES,
    CMAKE_CXX17_COMPILE_FEATURES,
    CMAKE_CXX98_COMPILE_FEATURES,
    CMAKE_CXX_ABI_COMPILED,
    CMAKE_CXX_COMPILER_ABI,
    CMAKE_C_ABI_COMPILED,
    CMAKE_C_COMPILER_ABI,
    CMAKE_C_COMPILE_FEATURES,
    CMAKE_INTERNAL_PLATFORM_ABI,
    CMAKE_CXX_SIZEOF_DATA_PTR,
    CMAKE_C_SIZEOF_DATA_PTR,
    CMAKE_SIZEOF_VOID_P,
    CMAKE_CXX14_COMPILE_FEATURES,
    CMAKE_CXX_COMPILE_FEATURES,
    CMAKE_C_IMPLICIT_LINK_LIBRARIES,
    CMAKE_CXX_IMPLICIT_LINK_LIBRARIES,
    CMAKE_C_IMPLICIT_LINK_LIBRARIES,
    CMAKE_CXX_IMPLICIT_LINK_DIRECTORIES,
    CMAKE_C_IMPLICIT_LINK_DIRECTORIES,
    CMAKE_CXX_STANDARD_DEFAULT,
    CMAKE_C_STANDARD_DEFAULT,
    CXX_TEST_WAS_RUN
)

/**
 * String version of CMAKE_COMPILER_CHECK_CACHE_VALUE_INCLUDE_LIST
 */
val CMAKE_COMPILER_CHECK_CACHE_VALUE_INCLUDED_STRING_LIST =
    CMAKE_COMPILER_CHECK_CACHE_VALUE_INCLUDE_LIST.map { it.name }

/**
 * These are values that *must* be in the cache for the cache to be valid.
 * If all of these properties are not in the prospective cache settings then we don't save the
 * settings so the cache won't be used.
 */
private val CMAKE_COMPILER_CHECK_CACHE_VALUE_REQUIRED = listOf(
    C_TEST_WAS_RUN,
    CMAKE_CXX_ABI_COMPILED,
    CMAKE_CXX_COMPILER_ABI,
    CMAKE_C_ABI_COMPILED,
    CMAKE_C_COMPILER_ABI,
    CMAKE_CXX_SIZEOF_DATA_PTR,
    CMAKE_C_SIZEOF_DATA_PTR,
    CMAKE_SIZEOF_VOID_P,
    CXX_TEST_WAS_RUN
)

/**
 * String version of CMAKE_COMPILER_CHECK_CACHE_VALUE_REQUIRED
 */
val CMAKE_COMPILER_CHECK_CACHE_VALUE_REQUIRED_STRINGS =
    CMAKE_COMPILER_CHECK_CACHE_VALUE_REQUIRED.map { it.name }


