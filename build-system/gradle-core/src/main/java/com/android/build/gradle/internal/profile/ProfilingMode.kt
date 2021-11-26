/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.build.gradle.internal.profile

import com.android.utils.HelpfulEnumConverter

enum class ProfilingMode(val modeName: String?, val isDebuggable: Boolean?) {
    UNDEFINED(null, null),
    DEBUGGABLE("debuggable", true),
    PROFILEABLE("profileable", false);

    companion object {

        fun getProfilingModeType(modeName: String?): ProfilingMode {
            val converter = HelpfulEnumConverter(ProfilingMode::class.java)
            return converter.convert(modeName) ?: UNDEFINED
        }
    }
}
