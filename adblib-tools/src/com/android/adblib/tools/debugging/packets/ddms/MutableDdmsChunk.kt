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
package com.android.adblib.tools.debugging.packets.ddms

import com.android.adblib.AdbInputChannel
import com.android.adblib.tools.debugging.packets.PayloadProvider

/**
 * A mutable version of [DdmsChunkView], to be used when creating DDMS "chunks"
 */
internal class MutableDdmsChunk : DdmsChunkView {

    override var type: DdmsChunkType = DdmsChunkType.NULL

    override var length: Int = 0

    var payloadProvider: PayloadProvider = PayloadProvider.emptyPayload()

    override suspend fun acquirePayload(): AdbInputChannel {
        return payloadProvider.acquirePayload()
    }

    override suspend fun releasePayload() {
        payloadProvider.releasePayload()
    }

    override fun toString(): String {
        return "DDMS Chunk: type=$type (${type.value}), length=$length"
    }
}
