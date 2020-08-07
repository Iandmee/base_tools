/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.build.gradle.internal.cxx.model

import com.android.build.gradle.internal.cxx.settings.CMakeSettingsConfiguration
import com.android.utils.FileUtils.join
import java.io.File

/**
 * Holds immutable per-ABI configuration specific to CMake needed for JSON generation.
 */
interface CxxCmakeAbiModel {

    /**
     * The base output folder for CMake wrapping outputs.
     * ex, $moduleRootFolder/.cxx/cxx/debug/armeabi-v7a
     */
    val cmakeWrappingBaseFolder: File

    /**
     * The base output folder for CMake-generated build artifacts.
     * ex, $moduleRootFolder/.cxx/cmake/debug/armeabi-v7a
     */
    val cmakeArtifactsBaseFolder: File

    /**
     * The effective CMakeSettings
     */
    val effectiveConfiguration : CMakeSettingsConfiguration

    /**
     * Log of the conversation with CMake server.
     */
    val cmakeServerLogFile: File
}

/**
 * The location of compile_commands.json if it is generated by CMake.
 *   ex, $moduleRootFolder/.cxx/cmake/debug/armeabi-v7a/compile_commands.json
 */
val CxxCmakeAbiModel.compileCommandsJsonFile: File
    get() = join(cmakeArtifactsBaseFolder,"compile_commands.json")

/**
 * Used by CMake compiler settings cache. This is the generated CMakeLists.txt file that
 * calls back to the user's CMakeLists.txt. The wrapping of CMakeLists.txt allows us
 * to insert additional functionality like save compiler settings to a file.
 */
val CxxCmakeAbiModel.cmakeListsWrapperFile: File
    get() = join(cmakeWrappingBaseFolder, "CMakeLists.txt")

/**
 * Used by CMake compiler settings cache. This is the generated toolchain file that
 * calls back to the user's original toolchain file. The wrapping of toolchain allows us
 * to insert additional functionality such as looking for pre-existing cached compiler
 * settings and using them.
 */
val CxxCmakeAbiModel.toolchainWrapperFile: File
    get() = join(cmakeWrappingBaseFolder, "android_gradle_build.toolchain.cmake")

/**
 * Each of the user's CMake properties are written to a file so that they can be
 * introspected after the configuration. For example, this is how we get the user's
 * compiler settings.
 */
val CxxCmakeAbiModel.buildGenerationStateFile: File
    get() = join(cmakeWrappingBaseFolder, "build_generation_state.json")

/**
 * Compiler settings cache key. It should contain everything that describes how compiler
 * settings are chosen.
 */
val CxxCmakeAbiModel.cacheKeyFile: File
    get() = join(cmakeWrappingBaseFolder, "compiler_cache_key.json")

/**
 * Contains information about whether the compiler settings cache was used by CMake.
 */
val CxxCmakeAbiModel.compilerCacheUseFile: File
    get() = join(cmakeWrappingBaseFolder, "compiler_cache_use.json")

/**
 * Contains information about whether the compiler settings cache was written by gradle
 * plugin.
 */
val CxxCmakeAbiModel.compilerCacheWriteFile: File
    get() = join(cmakeWrappingBaseFolder, "compiler_cache_write.json")

/**
 * Contains toolchain settings copied from the cache.
 */
val CxxCmakeAbiModel.toolchainSettingsFromCacheFile: File
    get() = join(cmakeWrappingBaseFolder, "compiler_settings_cache.cmake")

/**
 * The CMake file API query folder.
 *   ex, $moduleRootFolder/.cxx/cmake/debug/armeabi-v7a/.cmake/api/v1/query/client-agp
 */
val CxxCmakeAbiModel.clientQueryFolder: File
    get() = join(cmakeArtifactsBaseFolder,".cmake/api/v1/query/client-agp")

/**
 * The CMake file API reply folder.
 *   ex, $moduleRootFolder/.cxx/cmake/debug/armeabi-v7a/.cmake/api/v1/reply
 */
val CxxCmakeAbiModel.clientReplyFolder: File
    get() = join(cmakeArtifactsBaseFolder,".cmake/api/v1/reply")
