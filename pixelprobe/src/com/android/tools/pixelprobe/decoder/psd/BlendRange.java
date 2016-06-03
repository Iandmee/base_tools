/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.tools.pixelprobe.decoder.psd;

import com.android.tools.chunkio.Chunk;
import com.android.tools.chunkio.Chunked;

/**
 * A blend range indicates the tonal range for a given channel.
 */
@Chunked
final class BlendRange {
    @Chunk(byteCount = 1)
    short srcBlackIn;
    @Chunk(byteCount = 1)
    short srcWhiteIn;
    @Chunk(byteCount = 1)
    short srcBlackOut;
    @Chunk(byteCount = 1)
    short srcWhiteOut;

    @Chunk(byteCount = 1)
    short dstBlackIn;
    @Chunk(byteCount = 1)
    short dstWhiteIn;
    @Chunk(byteCount = 1)
    short dstBlackOut;
    @Chunk(byteCount = 1)
    short dstWhiteOut;
}
