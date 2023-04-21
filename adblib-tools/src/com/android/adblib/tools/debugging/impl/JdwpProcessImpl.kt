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
package com.android.adblib.tools.debugging.impl

import com.android.adblib.AdbSession
import com.android.adblib.ConnectedDevice
import com.android.adblib.CoroutineScopeCache
import com.android.adblib.property
import com.android.adblib.scope
import com.android.adblib.thisLogger
import com.android.adblib.tools.AdbLibToolsProperties.JDWP_SESSION_FIRST_PACKET_ID
import com.android.adblib.tools.debugging.AtomicStateFlow
import com.android.adblib.tools.debugging.JdwpProcess
import com.android.adblib.tools.debugging.JdwpProcessProperties
import com.android.adblib.tools.debugging.JdwpSession
import com.android.adblib.tools.debugging.SharedJdwpSession
import com.android.adblib.tools.debugging.utils.ReferenceCountedResource
import com.android.adblib.tools.debugging.utils.withResource
import com.android.adblib.utils.closeOnException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * Implementation of [JdwpProcess]
 */
internal class JdwpProcessImpl(
    session: AdbSession,
    override val device: ConnectedDevice,
    override val pid: Int
) : JdwpProcess, AutoCloseable {

    private val logger = thisLogger(session)

    private val stateFlow = AtomicStateFlow(MutableStateFlow(JdwpProcessProperties(pid)))

    override val cache = CoroutineScopeCache.create(device.scope)

    override val propertiesFlow = stateFlow.asStateFlow()

    /**
     * Provides concurrent and on-demand access to the `jdwp` session of the device.
     *
     * We use a [ReferenceCountedResource] to ensure only one session is created at a time,
     * while at the same time allowing multiple consumers to access the jdwp session concurrently.
     *
     * We currently have 2 consumers:
     * * A [JdwpProcessPropertiesCollector] that opens a jdwp session for a few seconds to collect
     *   the process properties (package name, process name, etc.)
     * * A [JdwpSessionProxy] that opens a jdwp session "on demand" when a Java debugger wants
     *   to connect to the process on the device.
     *
     * Typically, both consumers don't overlap, but if a debugger tries to attach to the process
     * just after its creation, before we are done collecting properties, the [JdwpSessionProxy]
     * ends up trying to open a jdwp session before [JdwpProcessPropertiesCollector] is done
     * collecting process properties. When this happens, we open a single JDWP connection that
     * is used for collecting process properties and for a debugging session. The connection
     * lasts until the debugging session ends.
     */
    private val jdwpSessionRef = ReferenceCountedResource(session, session.host.ioDispatcher) {
        JdwpSession.openJdwpSession(device, pid, session.property(JDWP_SESSION_FIRST_PACKET_ID))
            .closeOnException { jdwpSession ->
                SharedJdwpSession.create(jdwpSession, pid)
            }
    }

    private val propertyCollector = JdwpProcessPropertiesCollector(device, scope, pid, jdwpSessionRef)

    private val jdwpSessionProxy = JdwpSessionProxy(device, pid, jdwpSessionRef)

    private val lazyStartMonitoring by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        scope.launch {
            jdwpSessionProxy.execute(stateFlow)
        }
        scope.launch {
            propertyCollector.execute(stateFlow)
        }
    }

    /**
     * Whether the [SharedJdwpSession] is currently in use (testing only)
     */
    val isJdwpSessionRetained: Boolean
        get() = jdwpSessionRef.isRetained

    fun startMonitoring() {
        lazyStartMonitoring
    }

    override suspend fun <T> withJdwpSession(block: suspend SharedJdwpSession.() -> T): T {
        return jdwpSessionRef.withResource {
            it.block()
        }
    }

    override fun close() {
        val msg = "Closing coroutine scope of JDWP process $pid"
        logger.debug { msg }
        jdwpSessionRef.close()
        cache.close()
    }
}
