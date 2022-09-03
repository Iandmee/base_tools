/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.adblib.tools

/**
 * Various Kotlin functions to help writing tests in "JavaBridgeTest" (which is a Java file)
 */
object JavaBridgeTestUtils {
    @Suppress("RedundantSuspendModifier")  // For testing purposes
    @JvmStatic
    suspend fun immediateResultCoroutine(value: Int): Int = value * 2

    @Suppress("RedundantSuspendModifier")  // For testing purposes
    @JvmStatic
    suspend fun immediateExceptionCoroutine(message: String) {
        throw Exception(message)
    }
}