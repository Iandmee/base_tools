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
package com.android.adblib

import com.android.adblib.impl.InactiveCoroutineScopeCache
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.transform

/**
 * Tracks devices that are currently [connected][ConnectedDevice] to the ADB server
 * corresponding to a given [session].
 */
interface ConnectedDevicesTracker {

    /**
     * The [session][AdbSession] this [ConnectedDevicesTracker] belongs to
     */
    val session: AdbSession

    /**
     * The [StateFlow] of currently [connected devices][ConnectedDevice]. The flow remains
     * active as long as the [session] is active. Once the session is closed, the flow value
     * changes to an empty list and never updates again.
     */
    val connectedDevices: StateFlow<List<ConnectedDevice>>
}

/**
 * Returns a [ConnectedDevice] instance for a given [selector], or throws a
 * [NoSuchElementException] if the device is not currently connected.
 */
suspend fun ConnectedDevicesTracker.device(selector: DeviceSelector): ConnectedDevice {
    val serialNumber = try {
        this.session.hostServices.getSerialNo(selector)
    } catch (e: AdbFailResponseException) {
        throw NoSuchElementException("Device $selector is not currently connected")
    }
    return this.device(serialNumber)
}

/**
 * Returns a [ConnectedDevice] instance for a given [serialNumber], or throws a
 * [NoSuchElementException] if the device is not currently connected.
 */
fun ConnectedDevicesTracker.device(serialNumber: String): ConnectedDevice {
    return this.connectedDevices.value.firstOrNull { it.serialNumber == serialNumber }
        ?: throw NoSuchElementException("Device $serialNumber is not currently connected")
}

/**
 * Waits for a device with the given [serialNumber] to appear in the list of
 * [ConnectedDevicesTracker.connectedDevices].
 */
suspend fun ConnectedDevicesTracker.waitForDevice(serialNumber: String): ConnectedDevice {
    // Do a quick scan on the current state first (more efficient), then wait on the StateFlow.
    return connectedDevices.value.firstOrNull { it.serialNumber == serialNumber } ?: run {
        connectedDevices.transform { devices ->
            emit(devices.firstOrNull { device -> device.serialNumber == serialNumber })
        }.filterNotNull().first()
    }
}

/**
 * Returns the [CoroutineScopeCache] associated to the connected device with the
 * given [serialNumber]. If the device is not connected when this method is called,
 * a "no-op" cache is returned.
 *
 * Note: when the device is disconnected, the cache becomes inactive, i.e. it is emptied,
 * closed and never re-activated, even if a device with the same serial number is
 * reconnected.
 */
fun ConnectedDevicesTracker.deviceCache(serialNumber: String): CoroutineScopeCache {
    return try {
        this.device(serialNumber).cache
    } catch(e: NoSuchElementException) {
        return InactiveCoroutineScopeCache
    }
}

/**
 * Returns the [CoroutineScopeCache] associated to the connected device with the
 * given [selector]. If the device is not connected when this method is called,
 * a "no-op" cache is returned.
 *
 * Note: when the device is disconnected, the cache becomes inactive, i.e. it is emptied,
 * closed and never re-activated, even if a device with the same serial number is
 * reconnected.
 */
suspend fun ConnectedDevicesTracker.deviceCache(selector: DeviceSelector): CoroutineScopeCache {
    return try {
        this.device(selector).cache
    } catch(e: NoSuchElementException) {
        return InactiveCoroutineScopeCache
    }
}
