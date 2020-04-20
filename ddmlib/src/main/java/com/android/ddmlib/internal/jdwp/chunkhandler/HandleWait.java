/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.ddmlib.internal.jdwp.chunkhandler;

import com.android.ddmlib.ClientData;
import com.android.ddmlib.ClientData.DebuggerStatus;
import com.android.ddmlib.Log;
import com.android.ddmlib.internal.MonitorThread;
import com.android.ddmlib.internal.ClientImpl;
import java.nio.ByteBuffer;

/**
 * Handle the "wait" chunk (WAIT). These are sent up when the client is waiting for something, e.g.
 * for a debugger to attach.
 */
public final class HandleWait extends ChunkHandler {

    public static final int CHUNK_WAIT = ChunkHandler.type("WAIT");

    private static final HandleWait mInst = new HandleWait();

    private HandleWait() {}

    /** Register for the packets we expect to get from the client. */
    public static void register(MonitorThread mt) {
        mt.registerChunkHandler(CHUNK_WAIT, mInst);
    }

    /** Client is ready. */
    @Override
    public void clientReady(ClientImpl client) {}

    /** Client went away. */
    @Override
    public void clientDisconnected(ClientImpl client) {}

    /** Chunk handler entry point. */
    @Override
    public void handleChunk(
            ClientImpl client, int type, ByteBuffer data, boolean isReply, int msgId) {

        Log.d("ddm-wait", "handling " + ChunkHandler.name(type));

        if (type == CHUNK_WAIT) {
            assert !isReply;
            handleWAIT(client, data);
        } else {
            handleUnknownChunk(client, type, data, isReply, msgId);
        }
    }

    /*
     * Handle a reply to our WAIT message.
     */
    private static void handleWAIT(ClientImpl client, ByteBuffer data) {
        byte reason;

        reason = data.get();

        Log.d("ddm-wait", "WAIT: reason=" + reason);

        ClientData cd = client.getClientData();
        synchronized (cd) {
            cd.setDebuggerConnectionStatus(DebuggerStatus.WAITING);
        }

        client.update(ClientImpl.CHANGE_DEBUGGER_STATUS);
    }
}

