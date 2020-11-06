/*
 * Copyright (C) 2019 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http:/** www.apache.org/licenses/LICENSE-2.0 */
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.build.gradle.internal.cxx.model

import com.android.build.gradle.internal.core.Abi
import com.android.build.gradle.internal.ndk.Stl
import com.android.build.gradle.tasks.NativeBuildSystem
import org.gradle.api.file.FileCollection
import java.io.File

/**
 * Holds immutable ABI-level information for C/C++ build and sync, see README.md
 */
data class CxxVariantModel(
    /**
     * Arguments passed to CMake or ndk-build
     *   ex, android.defaultConfig.externalNativeBuild.arguments '-DMY_PROP=1'
     */
    val buildSystemArgumentList: List<String>,

    /**
     * C flags forwarded to compiler
     *   ex, android.defaultConfig.externalNativeBuild.cFlags '-DTHIS_IS_C=1'
     */
    val cFlagsList: List<String>,

    /**
     * C++ flags forwarded to compiler
     *   ex, android.defaultConfig.externalNativeBuild.cppFlags '-DTHIS_IS_CPP=1'
     */
    val cppFlagsList: List<String>,

    /**
     * The name of the variant
     *   ex, debug
     */
    val variantName: String,

    /**
     * Base folder for .o files
     *   ex, $moduleRootFolder/build/intermediates/cmake/debug/obj
     */
    val objFolder: File,

    /**
     * Base folder for .so files
     *   ex, $moduleRootFolder/build/intermediates/cmake/debug/lib
     */
    val soFolder: File,

    /**
     * Whether this variant build is debuggable
     */
    val isDebuggableEnabled: Boolean,

    /**
     * The list of valid ABIs for this variant
     */
    val validAbiList : List<Abi>,

    /**
     * The list of build targets.
     *  ex, android.defaultConfig.externalNativeBuild.targets "my-target"
     */
    val buildTargetSet : Set<String>,

    /**
     * The list of implicit build targets determined by other parts of the build.
     *
     * Currently the only use of this is for forcing static libraries to build if they are named in
     * an android.prefab block.
     */
    val implicitBuildTargetSet : Set<String>,

    /**  The CMakeSettings.json configuration
     *      ex, android
     *          .defaultConfig
     *          .cmake
     *          .externalNativeBuild
     *          .configuration 'my-configuration' */
    val cmakeSettingsConfiguration : String,

    /**
     * The module that this variant is part of
     */
    val module: CxxModuleModel,

    /**
     * Path to the Prefab jar to use.
     */
    val prefabClassPathFileCollection: FileCollection?,

    /**
     * Paths to the unprocessed prefab package directories extracted from the AAR.
     *
     * For example: jsoncpp/build/.transforms/$SHA/jsoncpp/prefab
     */
    val prefabPackageDirectoryListFileCollection: FileCollection?,

    /**
     * Path to the prefab output to be passed to the native build system.
     *
     * For example: app/.cxx/cmake/debug/prefab
     */
    val prefabDirectory: File,

    /**
     * If present, the type of the STL.
     */
    val stlType: String,
)

