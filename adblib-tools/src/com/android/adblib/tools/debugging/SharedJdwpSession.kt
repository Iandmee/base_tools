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
package com.android.adblib.tools.debugging

import com.android.adblib.AdbChannel
import com.android.adblib.AdbSession
import com.android.adblib.AutoShutdown
import com.android.adblib.ConnectedDevice
import com.android.adblib.CoroutineScopeCache
import com.android.adblib.tools.debugging.SharedJdwpSessionFilter.FilterId
import com.android.adblib.tools.debugging.impl.SharedJdwpSessionImpl
import com.android.adblib.tools.debugging.packets.JdwpPacketView
import com.android.adblib.tools.debugging.packets.withPayload
import com.android.adblib.tools.debugging.utils.NoDdmsPacketFilterFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.take
import java.io.EOFException

/**
 * A thread-safe version of [JdwpSession] that consumes packets on-demand via [newPacketReceiver]
 *
 * * For sending packets, the [sendPacket] methods works the same way as the underlying
 *   [JdwpSession.sendPacket], i.e. it is thread-safe and automatically handle the JDWP
 *   handshake. One difference is that writing to the underlying [socket][AdbChannel]
 *   is performed in a custom [CoroutineScope] so that cancellation of the caller
 *   (e.g. timeout) does not close the underlying socket. Closing the underlying socket
 *   would mean all other consumers of this JDWP session would be unable to perform
 *   any other operation on it.
 *
 * * For receiving packets, the [newPacketReceiver] method allows callers to register a
 *   [JdwpPacketReceiver] that exposes a [Flow] of [JdwpPacketView] for collecting *all*
 *   packets received from the JDWP session. Receivers are called sequentially and should
 *   handle their exceptions. Similar to [sendPacket] behavior, any I/O performed on the
 *   underlying [AdbChannel] is executed in a custom [CoroutineScope] so that cancellation
 *   of receivers coroutine does not close the underlying [socket][AdbChannel].
 */
interface SharedJdwpSession : AutoShutdown {

    /**
     * The [ConnectedDevice] this [SharedJdwpSession] is connected to.
     */
    val device: ConnectedDevice

    /**
     * The process ID this [SharedJdwpSession] handles
     */
    val pid: Int

    /**
     * Sends a [JdwpPacketView] to the underlying [JdwpSession].
     *
     * Note this method can block until the underlying communication channel
     * "send" buffer has enough room to store the packet.
     */
    suspend fun sendPacket(packet: JdwpPacketView)

    /**
     * Creates a [JdwpPacketReceiver] to collect [JdwpPacketView] coming from the
     * underlying [JdwpSession].
     *
     * ### Usage
     *
     *       session.newReceiver()
     *          .withName("Foo") // An arbitrary name used for debugging
     *          .onActivation {
     *              // Receiver has been activated and is guaranteed to receive all
     *              // received packets from this point on
     *          }
     *          .receive { packet ->
     *              // Receiver is active and a packet has been received.
     *              // The receiver has exclusive access to the packet until this block
     *              // ends.
     *          }
     *
     * ### Notes
     *
     * A [JdwpPacketReceiver] is initially **inactive**, i.e. does not collect packets and
     * does not make this [SharedJdwpSession] start consuming packets from the underlying
     * [JdwpSession]. A [JdwpPacketReceiver] is **activated** by calling [JdwpPacketReceiver.flow]
     * (or the [JdwpPacketReceiver.receive] shortcut).
     *
     * * All active [JdwpPacketReceiver]s are guaranteed to be invoked sequentially, meaning
     *   they can freely use any field of the [JdwpPacketView], as well as consume the
     *   [JdwpPacketView.withPayload] without any explicit synchronization. This also
     *   implies that **receivers should process packets quickly** to prevent blocking
     *   other receivers. Conceptually, the [SharedJdwpSession] works like this
     *
     *
     *    while(!EOF) {
     *      val packet = collect one JdwpPacketView from the JdwpSession
     *      activeReceiverFlowCollectors.forEach {
     *        it.emit(packet)
     *      }
     *    }
     *
     * * Active [JdwpPacketReceiver]s are cancelled if this session is [closed][close].
     *
     * * Active [JdwpPacketReceiver]s are notified of the termination of the underlying [JdwpSession]
     *   with a [Throwable]. A "normal" termination is an [EOFException].
     *
     *   @see JdwpPacketReceiver
     */
    suspend fun newPacketReceiver(): JdwpPacketReceiver

    /**
     * Returns a unique [JDWP packet ID][JdwpPacketView.id] to use for sending
     * a [JdwpPacketView], typically a [command packet][JdwpPacketView.isCommand],
     * in this session. Each call returns a new unique value.
     *
     * See [JdwpSession.nextPacketId]
     */
    fun nextPacketId(): Int

    /**
     * Add a [JdwpPacketView] to the list of "replay packets", i.e. the list of [JdwpPacketView]
     * that each new [receiver][newPacketReceiver] receives before any other packet from the
     * underlying [JdwpSession].
     */
    suspend fun addReplayPacket(packet: JdwpPacketView)

    companion object {
        private val sessionInitKey = CoroutineScopeCache.Key<SessionInit>(
            "SharedJdwpSession.sessionInit")

        private class SessionInit(private val session: AdbSession) {
            // Use "by lazy" to ensure the code is run only once
            private val lazyInit by lazy {
                session.addSharedJdwpSessionFilterFactory(NoDdmsPacketFilterFactory())
            }

            fun initOnceOnly() {
                lazyInit
            }
        }

        internal fun create(jdwpSession: JdwpSession, pid: Int): SharedJdwpSession {
            // Add the DdmsPacketFilterFactory to the list of active filters of the AdbSession
            // (in a very round-about way to make sure the filter is added only once per
            // AdbSession instance).
            //TODO: Make this configurable
            jdwpSession.device.session.cache.getOrPut(sessionInitKey) {
                SessionInit(jdwpSession.device.session)
            }.initOnceOnly()

            return SharedJdwpSessionImpl(jdwpSession, pid)
        }
    }
}

/**
 * Provides access to a [Flow] of [JdwpPacketView]
 *
 * @see SharedJdwpSession.newPacketReceiver
 */
abstract class JdwpPacketReceiver {

    var name: String = ""
        private set

    protected var activation: suspend () -> Unit = { }
        private set

    var filterId: FilterId? = null

    /**
     * Sets an arbitrary name for this receiver
     */
    fun withName(name: String): JdwpPacketReceiver {
        this.name = name
        return this
    }

    /**
     * Applies a [FilterId] to this [JdwpPacketReceiver] so its [flow] does not emit
     * [JdwpPacketView] instances filtered by the corresponding [SharedJdwpSessionFilter].
     */
    fun withFilter(filterId: FilterId): JdwpPacketReceiver {
        this.filterId = filterId
        return this
    }

    /**
     * Sets a [block] that is invoked when this receiver is activated, but before
     * any [JdwpPacketView] is received.
     *
     * Note: [block] is executed on the [AdbSession.ioDispatcher] dispatcher
     */
    fun onActivation(block: suspend () -> Unit): JdwpPacketReceiver {
        activation = block
        return this
    }

    /**
     * Starts receiving [packets][JdwpPacketView] from the underlying [JdwpSession] and invokes
     * [receiver] on [AdbSession.ioDispatcher] for each received packet.
     *
     * ### Detailed behavior
     *
     * * First, [onActivation] is launched concurrently and is guaranteed to be invoked **after**
     * [receiver] is ready to be invoked. This means [onActivation] can use
     * [SharedJdwpSession.sendPacket] that will be processed in [receiver].
     * * Then, all replay packets from [SharedJdwpSession.addReplayPacket] are sent to [receiver]
     * * Then, the underlying [SharedJdwpSession] is activated if needed, i.e. [JdwpPacketView]
     * are read from the underlying [JdwpSession], and [receiver] is invoked on
     * [AdbSession.ioDispatcher] for each [JdwpPacketView] received from the underlying
     * [JdwpSession], except for the packets filtered by the (optional) [SharedJdwpSessionFilter]
     * specified in [withFilter].
     *
     * ### Accessing packet payload
     *
     * The payload of the [JdwpPacketView] passed to [receiver] is guaranteed to be valid only
     * for the duration of the [receiver] call. This is because, most of the time, the
     * [JdwpPacketView] payload is directly connected to the underlying network socket and
     * will be invalidated once the next [JdwpPacketView] is read from that socket.
     *
     * ### Exceptions
     *
     * * This function returns when the underlying [SharedJdwpSession] reaches EOF.
     * * This function re-throws any other exception from the underlying [SharedJdwpSession].
     */
    abstract suspend fun receive(receiver: suspend (JdwpPacketView) -> Unit)

    /**
     * Wraps [JdwpPacketReceiver.receive] into a [Flow] of [JdwpPacketView].
     *
     * ### Performance
     *
     * To ensure all [JdwpPacketView] instances of the flow are guaranteed to be valid in
     * downstream flows (e.g. [`take(n)`][Flow.take] or [`buffer(n)`][Flow.buffer]), as well as
     * after the flow completes, [JdwpPacketView.toOffline] is invoked on each packet of the flow,
     * so there is a cost in terms of memory usage versus using the [receive] method.
     */
    abstract fun flow(): Flow<JdwpPacketView>
}
