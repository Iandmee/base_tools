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

import com.android.adblib.AdbInputChannel
import com.android.adblib.AdbLogger
import com.android.adblib.AdbSession
import com.android.adblib.ConnectedDevice
import com.android.adblib.scope
import com.android.adblib.serialNumber
import com.android.adblib.thisLogger
import com.android.adblib.tools.debugging.JdwpPacketReceiver
import com.android.adblib.tools.debugging.JdwpSession
import com.android.adblib.tools.debugging.SharedJdwpSession
import com.android.adblib.tools.debugging.SharedJdwpSessionMonitor
import com.android.adblib.tools.debugging.utils.AdbBufferedInputChannel
import com.android.adblib.tools.debugging.packets.JdwpPacketConstants
import com.android.adblib.tools.debugging.packets.JdwpPacketView
import com.android.adblib.tools.debugging.packets.impl.EphemeralJdwpPacket
import com.android.adblib.tools.debugging.packets.impl.PayloadProvider
import com.android.adblib.tools.debugging.packets.impl.PayloadProviderFactory
import com.android.adblib.tools.debugging.utils.toOffline
import com.android.adblib.tools.debugging.packets.impl.withPayload
import com.android.adblib.tools.debugging.packets.withPayload
import com.android.adblib.tools.debugging.rethrowCancellation
import com.android.adblib.tools.debugging.sharedJdwpSessionMonitorFactoryList
import com.android.adblib.tools.debugging.utils.MutableSerializedSharedFlow
import com.android.adblib.tools.debugging.utils.SerializedSharedFlow
import com.android.adblib.tools.debugging.utils.SupportsOffline
import com.android.adblib.utils.ResizableBuffer
import com.android.adblib.utils.createChildScope
import com.android.adblib.withPrefix
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.EOFException
import java.nio.ByteBuffer
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit

/**
 * Implementation of [SharedJdwpSession]
 */
internal class SharedJdwpSessionImpl(
    private val jdwpSession: JdwpSession,
    override val pid: Int
) : SharedJdwpSession {

    private val session: AdbSession
        get() = device.session

    override val device: ConnectedDevice
        get() = jdwpSession.device

    private val logger =
        thisLogger(session).withPrefix("device='${device.serialNumber}' pid=$pid: ")

    /**
     * The scope used to run coroutines for sending and receiving packets
     */
    private val scope = device.scope.createChildScope(isSupervisor = true)

    /**
     * The class that ensures [sendPacket] is thread-safe and also safe
     * from coroutine cancellation.
     */
    private val packetSender = PacketSender(scope, this)

    /**
     * The list of [JdwpPacketView] to replay to each new [JdwpPacketReceiver]
     */
    private val replayPackets = CopyOnWriteArrayList<JdwpPacketView>()

    /**
     * The [MutableSerializedSharedFlow] used to [MutableSerializedSharedFlow.emit] JDWP packets
     * to all active [JdwpPacketReceiver] instances.
     *
     * Note: We use [Any] as the type parameter, but we only emit either [Throwable] or
     * [JdwpPacketView] instances. We don't use [Result] to avoid extra allocations.
     */
    private val jdwpPacketSharedFlow = MutableSerializedSharedFlow<Any>()

    /**
     * The [SharedJdwpSessionMonitor] to invoke when sending or receiving [JdwpPacketView] packets,
     * or `null` if there are no active monitors from [AdbSession.sharedJdwpSessionMonitorFactoryList].
     *
     * Note: We aggregate all monitors so that we can have an efficient `null` check in the common
     * case where there are no active [SharedJdwpSessionMonitor].
     */
    private val jdwpMonitor = createAggregateJdwpMonitor(
        session.sharedJdwpSessionMonitorFactoryList.mapNotNull { factory ->
            factory.create(this)
        })

    private val jdwpFilter = SharedJdwpSessionFilterEngine(this)

    init {
        scope.launch {
            sendReceivedPackets()
        }
    }

    override suspend fun sendPacket(packet: JdwpPacketView) {
        packetSender.sendPacket(packet)
    }

    override suspend fun newPacketReceiver(): JdwpPacketReceiver {
        return JdwpPacketReceiverImpl(this)
    }

    override fun nextPacketId(): Int {
        return jdwpSession.nextPacketId()
    }

    override suspend fun addReplayPacket(packet: JdwpPacketView) {
        logger.verbose { "Adding JDWP replay packet '$packet'" }
        val clone = packet.toOffline()
        replayPackets.add(clone)
    }

    override suspend fun shutdown() {
        jdwpSession.shutdown()
    }

    override fun close() {
        logger.debug { "Closing session" }
        val exception = CancellationException("Shared JDWP session closed")
        scope.cancel(exception)
        jdwpSession.close()
        jdwpMonitor?.close()
        jdwpFilter.close()
        jdwpPacketSharedFlow.close()
    }

    /**
     * Asynchronous long-running coroutine that forwards packets read from [jdwpSession]
     * to active [jdwpPacketSharedFlow]
     */
    private suspend fun sendReceivedPackets() {
        withContext(CoroutineName("Shared JDWP Session: Send received packets job")) {
            val workBuffer = ResizableBuffer()
            val sharedPayloadProviderFactory = SharedPayloadProviderFactory(session, scope)
            while (true) {

                // Wait until we have at least one active receiver
                jdwpPacketSharedFlow.subscriptionCount.waitUntil { it > 0 }

                logger.verbose { "Waiting for next JDWP packet from session" }
                val sessionPacket = try {
                    jdwpSession.receivePacket()
                } catch (throwable: Throwable) {
                    // Cancellation cancels the jdwp session scope, which cancel the scope
                    // of all receivers, so they will terminate with cancellation too.
                    throwable.rethrowCancellation()

                    // Reached EOF, flow terminates
                    if (throwable is EOFException) {
                        logger.debug { "JDWP session has ended with EOF" }
                    }
                    logger.verbose(throwable) { "Emitting JDWP session exception '$throwable' to shared flow of receivers" }
                    jdwpPacketSharedFlow.emit(throwable)
                    sendFailureUntilCancelled(throwable)
                    break
                }

                // "Emit" a thread-safe version of "sessionPacket" to all receivers
                sessionPacket.withPayload { sessionPacketPayload ->
                    // Note: "sessionPacketPayload" is an input channel directly connected to the
                    // underlying connection.
                    // We create a scoped payload channel so that
                    // 1) the payload is thread-safe for all receivers, and
                    // 2) cancellation of any receiver does not close the underlying socket
                    // 3) "shutdown" does not close the underlying PayloadProvider (that is taken
                    // care of by the JdwpSession class)
                    val payloadProvider =
                        sharedPayloadProviderFactory.create(sessionPacket, sessionPacketPayload)
                    EphemeralJdwpPacket.fromPacket(sessionPacket, payloadProvider).use { packet ->

                        jdwpMonitor?.onReceivePacket(packet)
                        logger.verbose { "Emitting session packet $packet to shared flow of receivers" }
                        jdwpPacketSharedFlow.emit(packet)
                        jdwpFilter.afterReceivePacket(packet)

                        // Packet should not be used after this, because payload of the jdwp packet
                        // from the underlying jdwp session is about to become invalid.
                        packet.shutdown(workBuffer)
                    }
                }
            }
        }
    }

    /**
     * Once the underlying [JdwpSession] has ended with an exception, keep sending
     * that exception to any new or existing receiver.
     */
    private suspend fun sendFailureUntilCancelled(throwable: Throwable) {
        // We use our "scope" context so that we are cancelled when this session
        // is closed.
        withContext(scope.coroutineContext) {
            // Any time there is a change in subscription (i.e. a new collector or a
            // completed collector), we (re-)emit the exception to all receivers.
            jdwpPacketSharedFlow.subscriptionCount.collect {
                if (it > 0) {
                    logger.verbose { "Sending failure to active receivers ($throwable)" }
                    jdwpPacketSharedFlow.emit(throwable)
                }
            }
        }
    }

    private suspend fun <T> StateFlow<T>.waitUntil(predicate: suspend (T) -> Boolean) {
        this.first { predicate(it) }
    }

    private class JdwpPacketReceiverImpl(
        private val jdwpSession: SharedJdwpSessionImpl
    ) : JdwpPacketReceiver() {

        override suspend fun receive(receiver: suspend (JdwpPacketView) -> Unit) {
            withContext(jdwpSession.session.ioDispatcher) {
                coroutineScope {
                    val receiveScope = this
                    val receiveLogger = jdwpSession.logger.withPrefix("Receiver '$name': ")
                    val sharedFlow = jdwpSession.jdwpPacketSharedFlow
                    val activationJobStartPacket = StartPacket()
                    val activationJobStartSignal = CompletableDeferred<Unit>()

                    // Launch a coroutine that invokes the "activation" when the collector is ready
                    val activationJob = receiveScope.launchActivationJob(
                        receiveLogger,
                        sharedFlow,
                        activationJobStartPacket,
                        activationJobStartSignal
                    )

                    // Collect the shared flow and emit to the local flow
                    collectSharedFlow(
                        receiver,
                        receiveLogger,
                        sharedFlow,
                        activationJobStartPacket,
                        activationJobStartSignal
                    )

                    // We reached EOF: Send a custom "EOFCancellationException" to terminate the
                    // "activation" coroutine (in case it has not completed yet)
                    activationJob.cancel(EOFCancellationException("Reached EOF"))
                }
            }
        }

        override fun flow(): Flow<JdwpPacketView> {
            return channelFlow {
                val workBuffer = ResizableBuffer()
                receive { packet ->
                    // Make the packet "offline" (i.e. read payload in memory if needed) to
                    // ensure it is safe to use in downstream flows (e.g. filtering,
                    // buffering, etc)
                    send(packet.toOffline(workBuffer))
                }
            }
        }

        /**
         * Launch the [activation] callback as a child of this [CoroutineScope] that runs
         * concurrently with the [sharedFlow] collector, **ensuring** the collector is started
         * (see [activationJobStartSignal]), so that the collector won't miss replies to
         * JDWP packets the [activation] callback may send.
         *
         * Note on exceptions: All exceptions, except for [EOFCancellationException], should
         * be propagated to this [CoroutineScope].
         */
        private fun CoroutineScope.launchActivationJob(
            receiveLogger: AdbLogger,
            sharedFlow: MutableSerializedSharedFlow<Any>,
            activationJobStartPacket: StartPacket,
            activationJobStartSignal: CompletableDeferred<Unit>
        ): Job {
            return launch {
                // Send `startPacket` to the shared flow until the new collector acknowledges
                // it has received it (by completing `deferredStart`).
                // Note that to avoid a race condition (the collector may take a few millis
                // to start), we need to retry sending `startPacket` until it is acknowledged.
                while (true) {
                    ensureActive()
                    sharedFlow.emit(activationJobStartPacket)
                    if (activationJobStartSignal.isCompleted) {
                        receiveLogger.verbose { "calling 'activation' callback" }
                        this@JdwpPacketReceiverImpl.activation()
                        break
                    }
                }
            }.also {
                it.invokeOnCompletion { throwable ->
                    when (throwable) {
                        null -> {
                            // The "activation" job completed successfully: nothing to do
                        }

                        is EOFCancellationException -> {
                            // The flow completed successfully with EOF: nothing to do
                        }

                        is CancellationException -> {
                            // The "activation" job was cancelled for some other reason than
                            // EOF: propagate cancellation to parent scope (i.e. the flow)
                            this@launchActivationJob.cancel(throwable)
                        }

                        else -> {
                            // Any other exception is a "non-cancellation" exception, and it
                            // is automatically propagated to the parent scope (i.e. the flow)
                        }
                    }
                }
            }
        }

        /**
         * Collects [sharedFlow], emitting [JdwpPacketView] using [safeEmit] to ensure serialized
         * invocations of all active collectors.
         */
        private suspend fun collectSharedFlow(
            receiver: suspend (JdwpPacketView) -> Unit,
            receiveLogger: AdbLogger,
            sharedFlow: SerializedSharedFlow<Any>,
            startPacket: StartPacket,
            activationJobStartSignal: CompletableDeferred<Unit>
        ) {
            sharedFlow
                .takeWhile { anyValue ->
                    receiveLogger.verbose { "Processing value from shared flow $anyValue" }
                    when(anyValue) {
                        is JdwpPacketView -> {
                            @Suppress("UnnecessaryVariable") // readability
                            val packet = anyValue
                            when {
                                // This is the "StartPacket" send to this collector by its
                                // "activation", wake up the "activation" coroutine and emit
                                // replay packets to downstream flow.
                                packet === startPacket -> {
                                    receiveLogger.verbose { "Deferred starts is completed" }
                                    activationJobStartSignal.complete(Unit)
                                    receiveLogger.verbose { "Emitting ${jdwpSession.replayPackets.size} replay packet(s)" }
                                    jdwpSession.replayPackets.forEach { replyPacket ->
                                        safeEmit(receiver, receiveLogger, replyPacket)
                                    }
                                }

                                packet is StartPacket -> {
                                    // This is a "StartPacket" sent to another collector, ignore it
                                }

                                else -> {
                                    // This is a "real" JDWP packet, emit it to the downstream flow
                                    safeEmit(receiver, receiveLogger, packet)
                                }
                            }
                            true // Keep collecting the shared flow
                        }

                        is EOFException -> {
                            receiveLogger.debug { "EOF reached, ending flow" }
                            false // We are done collecting the shared flow
                        }

                        is Throwable -> {
                            throw anyValue
                        }

                        else -> {
                            receiveLogger.error("Unknown value in share flow: $anyValue")
                            throw IllegalStateException("Invalid value in flow: $anyValue")
                        }
                    }
                }
                .collect()
        }

        private suspend fun safeEmit(
            receiver: suspend (JdwpPacketView) -> Unit,
            receiveLogger: AdbLogger,
            packet: JdwpPacketView
        ) {
            if (jdwpSession.jdwpFilter.filterReceivedPacket(filterId, packet)) {
                receiveLogger.verbose { "Emitting packet flow: $packet" }
                receiver(packet)
            } else {
                receiveLogger.verbose { "Skipping packet due to filter: $packet" }
            }
        }

        private class EOFCancellationException(message: String) : CancellationException(message)

        /**
         * A "Fake" [JdwpPacketView] used as a signal to synchronize [launchActivationJob] and
         * [collectSharedFlow].
         */
        private class StartPacket : JdwpPacketView {

            override val length: Int
                get() = JdwpPacketConstants.PACKET_HEADER_LENGTH
            override val id: Int
                get() = -10
            override val flags: Int
                get() = 0
            override val cmdSet: Int
                get() = 0
            override val cmd: Int
                get() = 0
            override val errorCode: Int
                get() = 0

            override suspend fun acquirePayload(): AdbInputChannel {
                return AdbBufferedInputChannel.empty()
            }

            override fun releasePayload() {
                // Nothing to do
            }

            override suspend fun toOffline(workBuffer: ResizableBuffer): JdwpPacketView {
                return this
            }
        }
    }

    private class SharedPayloadProviderFactory(
        session: AdbSession,
        private val scope: CoroutineScope
    ) : PayloadProviderFactory(session) {

        override fun createLargePacketProvider(
            packet: JdwpPacketView,
            packetPayload: AdbInputChannel,
            packetPayloadLength: Int
        ): PayloadProvider {
            return ScopedPayloadProvider(scope, packetPayload)
        }

    }

    /**
     * A [PayloadProvider] that wraps a [payload][AdbInputChannel] using a
     * [ScopedAdbBufferedInputChannel] so that cancellation of pending read operations
     * never close the initial [payload][AdbInputChannel].
     */
    private class ScopedPayloadProvider(
        /**
         * The [CoroutineScope] used to asynchronously read from the [payload]. This scope
         * should be active as long as [payload] is active.
         */
        scope: CoroutineScope,
        /**
         * The [AdbInputChannel] being wrapped.
         */
        payload: AdbInputChannel
    ) : PayloadProvider {

        private var closed = false

        /**
         * Locks access to [scopedPayload] to guarantee there is at most a single reader
         */
        private val mutex = Mutex()

        /**
         * The [ScopedAdbBufferedInputChannel] wrapping the original [AdbInputChannel], to ensure
         * cancellation of [AdbInputChannel.read] operations don't close the [AdbInputChannel].
         */
        private var scopedPayload = ScopedAdbBufferedInputChannel(scope, payload)

        override suspend fun acquirePayload(): AdbInputChannel {
            throwIfClosed()
            scopedPayload.waitForPendingRead()
            mutex.lock()
            scopedPayload.rewind()
            return scopedPayload
        }

        override fun releasePayload() {
            mutex.unlock()
        }

        override suspend fun shutdown(workBuffer: ResizableBuffer) {
            closed = true
            mutex.withLock {
                scopedPayload.waitForPendingRead()
            }
        }

        override suspend fun toOffline(workBuffer: ResizableBuffer): PayloadProvider {
            return withPayload {
                PayloadProvider.forInputChannel(scopedPayload.toOffline(workBuffer))
            }
        }

        override fun close() {
            closed = true
            scopedPayload.close()
        }

        private fun throwIfClosed() {
            if (closed) {
                throw IllegalStateException("Payload is not available anymore because the provider has been closed")
            }
        }

        /**
         * An [AdbBufferedInputChannel] that reads from another [AdbBufferedInputChannel] in a custom
         * [CoroutineScope] so that cancellation does not affect the initial [bufferedInput].
         */
        private class ScopedAdbBufferedInputChannel(
            private val scope: CoroutineScope,
            input: AdbInputChannel
        ) : AdbBufferedInputChannel, SupportsOffline<AdbBufferedInputChannel> {

            private var currentReadJob: Job? = null

            /**
             * Ensure we have a [AdbBufferedInputChannel] so we support
             * [AdbBufferedInputChannel.rewind]
             */
            private val bufferedInput = if (input is AdbBufferedInputChannel) {
                input
            } else {
                AdbBufferedInputChannel.forInputChannel(input)
            }

            override suspend fun rewind() {
                throwIfPendingRead()
                bufferedInput.rewind()
            }

            override suspend fun finalRewind() {
                throw IllegalStateException("finalRewind should never be called on ${this::class}")
            }

            override suspend fun read(buffer: ByteBuffer, timeout: Long, unit: TimeUnit): Int {
                return scopedRead { bufferedInput.read(buffer) }
            }

            override suspend fun readExactly(buffer: ByteBuffer, timeout: Long, unit: TimeUnit) {
                return scopedRead { bufferedInput.readExactly(buffer) }
            }

            suspend fun waitForPendingRead() {
                currentReadJob?.join()
            }

            override suspend fun toOffline(workBuffer: ResizableBuffer): AdbBufferedInputChannel {
                return scopedRead {
                    bufferedInput.toOffline(workBuffer)
                }
            }

            override fun close() {
                // We cancel any pending job if there is one, so that we support prompt
                // cancellation if the owner of this instance if cancelled. In non-exceptional
                // code paths, "currentReadJob" is already completed (because of the
                // "waitForPendingRead" call) and the "cancel" call below is a no-op.
                currentReadJob?.cancel("${this::class} has been closed")
            }

            private suspend inline fun <R> scopedRead(crossinline reader: suspend () -> R): R {
                throwIfPendingRead()

                // Start new job in parent scope and wait for its completion
                return scope.async {
                    reader()
                }.also { job ->
                    currentReadJob = job
                }.await().also {
                    currentReadJob = null
                }
            }

            private fun throwIfPendingRead() {
                check(!(currentReadJob?.isActive ?: false)) {
                    "Operation is not supported if there is a pending read operation"
                }
            }
        }
    }

    /**
     * Send [JdwpPacketView] to the underlying [jdwpSession] in such a way that cancellation
     * of the [sendPacket] coroutine does not cancel the underlying socket connection.
     */
    private class PacketSender(
        private val scope: CoroutineScope,
        private val sharedJdwpSession: SharedJdwpSessionImpl
    ) {

        suspend fun sendPacket(packet: JdwpPacketView) {
            scope.async {
                sharedJdwpSession.jdwpMonitor?.onSendPacket(packet)
                sharedJdwpSession.jdwpFilter.beforeSendPacket(packet)
                sharedJdwpSession.jdwpSession.sendPacket(packet)
            }.await()
        }
    }

    companion object {

        private fun createAggregateJdwpMonitor(jdwpMonitors: List<SharedJdwpSessionMonitor>): SharedJdwpSessionMonitor? {
            return if (jdwpMonitors.isEmpty()) {
                null
            } else {
                object : SharedJdwpSessionMonitor {
                    override suspend fun onSendPacket(packet: JdwpPacketView) {
                        jdwpMonitors.forEach {
                            it.onSendPacket(packet)
                        }
                    }

                    override suspend fun onReceivePacket(packet: JdwpPacketView) {
                        jdwpMonitors.forEach {
                            it.onReceivePacket(packet)
                        }
                    }

                    override fun close() {
                        jdwpMonitors.forEach {
                            it.close()
                        }
                    }
                }
            }
        }
    }
}
