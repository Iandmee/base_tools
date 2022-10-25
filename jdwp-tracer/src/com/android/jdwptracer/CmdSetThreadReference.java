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
package com.android.jdwptracer;

import java.nio.ByteBuffer;

class CmdSetThreadReference extends CmdSet {

    protected CmdSetThreadReference() {
        super(11, "THREAD_REF");

        add(1, "Name");
        add(2, "Suspend");
        add(3, "Resume");
        add(4, "Status");
        add(5, "ThreadGroup");
        add(
                6,
                "Frames",
                CmdSetThreadReference::parseFramesCmd,
                CmdSetThreadReference::parseFramesReply);
        add(7, "FrameCount");
        add(8, "OwnerMonitors");
        add(9, "CurrentContendedMonitor");
        add(10, "Stop");
        add(11, "Interrupt");
        add(12, "SuspendCount");
        add(13, "OwnerMonitorStackDepthInfo");
        add(14, "ForceEarlyReturn");
    }

    private static Message parseFramesCmd(ByteBuffer byteBuffer, MessageReader reader) {
        Message message = Message.cmdMessage(byteBuffer);

        long threadID = reader.getThreadID(byteBuffer);
        int startFrame = reader.getInt(byteBuffer);
        int length = reader.getInt(byteBuffer);

        message.addCmdArg("threadID", Long.toUnsignedString(threadID));
        message.addCmdArg("startFrame", Integer.toString(startFrame));
        message.addCmdArg("length", Integer.toString(length));

        return message;
    }

    private static Message parseFramesReply(ByteBuffer byteBuffer, MessageReader reader) {
        Message message = Message.replyMessage(byteBuffer);

        int frames = reader.getInt(byteBuffer);

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < frames; i++) {
            long frameId = reader.getFrameID(byteBuffer);
            reader.getLocation(byteBuffer);

            if (i != 0) sb.append(",");
            sb.append(Long.toUnsignedString(frameId));
        }

        message.addReplyArg("frames", sb.toString());

        return message;
    }
}
