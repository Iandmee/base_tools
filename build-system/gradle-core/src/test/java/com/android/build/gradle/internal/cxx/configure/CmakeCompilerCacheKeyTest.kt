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

import com.android.builder.model.Version
import com.google.common.truth.Truth.assertThat

import org.junit.Test
import java.io.File

class CmakeCompilerCacheKeyTest {

    @Test
    fun toAndFromFile() {
        val key = CmakeCompilerCacheKey(
            ndkInstallationFolder = File("./ndk"),
            ndkSourceProperties = SdkSourceProperties(mapOf("x" to "y")),
            args = listOf("a", "b"))
        val file = File("file.json")
        key.toFile(file)
        val key2 = CmakeCompilerCacheKey.fromFile(file)
        assertThat(key2).isEqualTo(key)
        assertThat(key.gradlePluginVersion).isEqualTo(Version.ANDROID_GRADLE_PLUGIN_VERSION)
    }
}