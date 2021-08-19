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

package com.android.tools.utp.plugins.deviceprovider.gradle

/**
 * Class to handle interactions with devices via ADB to the Gradle Device Launcher
 */
interface GradleAdbManager {
    /** Sets the path to the adb executable */
    fun configure(adbPath: String)

    /** Returns the list of serials for all online devices.*/
    fun getAllSerials(): List<String>

    /** Returns whether the given device has booted or not.*/
    fun isBootLoaded(deviceSerial: String): Boolean

    /** Returns the id associated with the corresponding serial */
    fun getId(deviceSerial: String): String?

    /** Attempts to close the serial from adb. */
    fun closeDevice(deviceSerial: String)
}
