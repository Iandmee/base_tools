/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.fakeadbserver.devicecommandhandlers.ddmsHandlers;

import com.android.annotations.NonNull;
import com.android.fakeadbserver.ClientState;
import com.android.fakeadbserver.DeviceState;
import java.io.IOException;
import java.io.OutputStream;

public interface DDMPacketHandler {

    /**
     * Interface for fake debugger to handle incoming packets
     *
     * @param device The device associated with the client
     * @param client The client associated with the connection
     * @param packet The packet that is being handled
     * @param oStream The stream to write the response to
     * @return If true the fake debugger should continue accepting packets, if false it should
     *     terminate the session
     */
    boolean handlePacket(
            @NonNull DeviceState device,
            @NonNull ClientState client,
            @NonNull DdmPacket packet,
            @NonNull OutputStream oStream);

    default void replyDdmFail(@NonNull OutputStream oStream, int packetId) throws IOException {
        // Android seems to always reply to invalid DDM commands with an empty JDWP reply packet
        JdwpPacket packet = new JdwpPacket(packetId, true, (short) 0, new byte[0], 0, 0);
        packet.write(oStream);
    }

}
