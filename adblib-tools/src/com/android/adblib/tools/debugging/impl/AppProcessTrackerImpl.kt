/*
 * Copyright (C) 2023 The Android Open Source Project
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

import com.android.adblib.AppProcessEntry
import com.android.adblib.ConnectedDevice
import com.android.adblib.property
import com.android.adblib.scope
import com.android.adblib.selector
import com.android.adblib.thisLogger
import com.android.adblib.tools.AdbLibToolsProperties.JDWP_PROCESS_TRACKER_CLOSE_NOTIFICATION_DELAY
import com.android.adblib.tools.AdbLibToolsProperties.TRACK_APP_RETRY_DELAY
import com.android.adblib.tools.debugging.AppProcess
import com.android.adblib.tools.debugging.AppProcessTracker
import com.android.adblib.tools.debugging.rethrowCancellation
import com.android.adblib.utils.createChildScope
import com.android.adblib.withPrefix
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.io.EOFException

internal class AppProcessTrackerImpl(
    override val device: ConnectedDevice
) : AppProcessTracker {

    private val session
        get() = device.session

    private val logger = thisLogger(device.session)
        .withPrefix("${device.session} - $device -")

    private val processesMutableFlow = MutableStateFlow<List<AppProcess>>(emptyList())

    private val trackProcessesJob: Job by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        scope.launch {
            trackProcesses()
        }
    }

    override val scope = device.scope.createChildScope(isSupervisor = true)

    override val appProcessFlow = processesMutableFlow.asStateFlow()
        get() {
            // Note: We rely on "lazy" to ensure the tracking coroutine is launched only once
            trackProcessesJob
            return field
        }

    private suspend fun trackProcesses() {
        val processMap = ProcessMap<AppProcessImpl>(
            onRemove = { appProcess ->
                logger.debug { "Removing process ${appProcess.pid} from process map" }
                closeAppProcess(appProcess)
            }
        )
        var deviceDisconnected = false
        try {
            session.deviceServices
                .trackApp(device.selector)
                .retryWhen { throwable, _ ->
                    // We want to retry the `trackApp` request as long as the device is connected.
                    // But we also want to end the flow when the device has been disconnected.
                    if (!scope.isActive) {
                        logger.info { "Tracker service ending because device is disconnected" }
                        deviceDisconnected = true
                        false // Don't retry, let exception through
                    } else {
                        // Retry after emitting empty list
                        if (throwable is EOFException) {
                            logger.info { "Tracker services ended with expected EOF exception, retrying" }
                        } else {
                            logger.info(throwable) { "Tracker ended unexpectedly ($throwable), retrying" }
                        }
                        // When disconnected, assume we have no processes
                        emit(emptyList())
                        delay(session.property(TRACK_APP_RETRY_DELAY).toMillis())
                        true // Retry
                    }
                }.collect { appEntryList ->
                    logger.debug { "Received a new list of processes: $appEntryList" }
                    updateProcessMap(processMap, appEntryList)
                    processesMutableFlow.emit(processMap.values.toList())
                }
        } catch (t: Throwable) {
            t.rethrowCancellation()
            if (deviceDisconnected) {
                logger.debug(t) { "Ignoring exception $t because device has been disconnected" }
            } else {
                throw t
            }
        } finally {
            logger.debug { "Clearing process map" }
            processMap.clear()
            processesMutableFlow.value = emptyList()
        }
    }

    private fun closeAppProcess(appProcess: AppProcessImpl) {
        val delayMillis = session.property(JDWP_PROCESS_TRACKER_CLOSE_NOTIFICATION_DELAY).toMillis()
        if (delayMillis > 0) {
            scope.launch {
                withTimeoutOrNull(delayMillis) {
                    appProcess.awaitReadyToClose()
                } ?: run {
                    logger.info { "App process was not ready to close within $delayMillis milliseconds" }
                }
            }.invokeOnCompletion {
                appProcess.close()
            }
        } else {
            appProcess.close()
        }
    }

    private fun updateProcessMap(map: ProcessMap<AppProcessImpl>, list: List<AppProcessEntry>) {
        val pids = list.map { it.pid }
        map.update(pids, valueFactory = { pid ->
            logger.debug { "Adding process $pid to process map" }

            val entry = list.first { it.pid == pid }
            AppProcessImpl(device, entry).also {
                it.startMonitoring()
            }
        })
    }
}
