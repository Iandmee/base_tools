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
package com.android.adblib.tools.testutils

import com.android.adblib.AdbChannel
import com.android.adblib.AdbServerChannelProvider
import com.android.adblib.AdbSession
import com.android.adblib.AdbSessionHost
import com.android.adblib.ConnectedDevice
import com.android.adblib.SOCKET_CONNECT_TIMEOUT_MS
import com.android.adblib.connectedDevicesTracker
import com.android.adblib.isOnline
import com.android.adblib.serialNumber
import com.android.adblib.testingutils.CloseablesRule
import com.android.adblib.testingutils.FakeAdbServerProvider
import com.android.adblib.testingutils.FakeAdbServerProviderRule
import com.android.adblib.testingutils.TestingAdbSessionHost
import com.android.fakeadbserver.DeviceState
import com.android.fakeadbserver.devicecommandhandlers.SyncCommandHandler
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import org.hamcrest.CoreMatchers
import org.junit.Assert
import org.junit.Rule
import org.junit.rules.ExpectedException
import java.time.Duration
import java.util.concurrent.TimeUnit

open class AdbLibToolsTestBase {

    @JvmField
    @Rule
    val fakeAdbRule = FakeAdbServerProviderRule {
        installDefaultCommandHandlers()
        installDeviceHandler(SyncCommandHandler())
    }

    protected val fakeAdb get() = fakeAdbRule.fakeAdb
    protected val session get() = fakeAdbRule.adbSession
    protected val hostServices get() = session.hostServices
    protected val deviceServices get() = session.deviceServices

    @JvmField
    @Rule
    val closeables = CloseablesRule()

    @JvmField
    @Rule
    var exceptionRule: ExpectedException = ExpectedException.none()

    protected fun <T : AutoCloseable> registerCloseable(item: T): T {
        return closeables.register(item)
    }

    protected fun createDisconnectedSession(): AdbSession {
        val host = registerCloseable(TestingAdbSessionHost())
        val channelProvider = object: AdbServerChannelProvider {
            override suspend fun createChannel(timeout: Long, unit: TimeUnit): AdbChannel {
                throw NotImplementedError("A disconnected session does not support channels")
            }
        }
        return registerCloseable(AdbSession.create(
            host,
            channelProvider,
            Duration.ofMillis(SOCKET_CONNECT_TIMEOUT_MS)
        ))
    }

    protected fun addFakeDevice(fakeAdb: FakeAdbServerProvider, api: Int): DeviceState {
        val fakeDevice =
            fakeAdb.connectDevice(
                "1234",
                "test1",
                "test2",
                "model",
                api.toString(),
                DeviceState.HostConnectionType.USB
            )
        fakeDevice.deviceStatus = DeviceState.DeviceStatus.ONLINE
        return fakeDevice
    }

    protected fun <T: Any> setHostPropertyValue(host: AdbSessionHost, property: AdbSessionHost.Property<T>, value: T) {
        (host as TestingAdbSessionHost).setPropertyValue(property, value)
    }

    protected inline fun <reified T> assertThrows(block: () -> Unit) {
        try {
            block()
        } catch(t: Throwable) {
            Assert.assertThat(t, CoreMatchers.instanceOf(T::class.java))
            return
        }
        Assert.fail("Expected: An exception instance of ${T::class}, but got no exception instead")
    }
}

internal suspend fun waitForOnlineConnectedDevice(
    session: AdbSession,
    serialNumber: String
): ConnectedDevice {
    return session.connectedDevicesTracker.connectedDevices
        .mapNotNull { connectedDevices ->
            connectedDevices.firstOrNull { device ->
                device.isOnline && device.serialNumber == serialNumber
            }
        }.first()
}
