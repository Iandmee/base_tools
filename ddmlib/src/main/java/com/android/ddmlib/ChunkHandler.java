/*
 * Copyright (C) 2007 The Android Open Source Project
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

package com.android.ddmlib;

import com.android.ddmlib.DebugPortManager.IDebugPortProvider;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Subclass this with a class that handles one or more chunk types.
 */
abstract class ChunkHandler {

    public static final int CHUNK_HEADER_LEN = 8;   // 4-byte type, 4-byte len
    public static final ByteOrder CHUNK_ORDER = ByteOrder.BIG_ENDIAN;

    public static final int CHUNK_FAIL = type("FAIL");

    ChunkHandler() {}

    /**
     * Client is ready.  The monitor thread calls this method on all
     * handlers when the client is determined to be DDM-aware (usually
     * after receiving a HELO response.)
     *
     * The handler can use this opportunity to initialize client-side
     * activity.  Because there's a fair chance we'll want to send a
     * message to the client, this method can throw an IOException.
     */
    abstract void clientReady(Client client) throws IOException;

    /**
     * Client has gone away.  Can be used to clean up any resources
     * associated with this client connection.
     */
    abstract void clientDisconnected(Client client);

    /**
     * Handle an incoming chunk.  The data, of chunk type "type", begins
     * at the start of "data" and continues to data.limit().
     *
     * If "isReply" is set, then "msgId" will be the ID of the request
     * we sent to the client.  Otherwise, it's the ID generated by the
     * client for this event.  Note that it's possible to receive chunks
     * in reply packets for which we are not registered.
     *
     * The handler may not modify the contents of "data".
     */
    abstract void handleChunk(Client client, int type,
        ByteBuffer data, boolean isReply, int msgId);

    /**
     * Handle chunks not recognized by handlers.  The handleChunk() method
     * in sub-classes should call this if the chunk type isn't recognized.
     */
    protected void handleUnknownChunk(Client client, int type,
        ByteBuffer data, boolean isReply, int msgId) {
        if (type == CHUNK_FAIL) {
            int errorCode, msgLen;
            String msg;

            errorCode = data.getInt();
            msgLen = data.getInt();
            msg = getString(data, msgLen);
            Log.w("ddms", "WARNING: failure code=" + errorCode + " msg=" + msg);
        } else {
            Log.w("ddms", "WARNING: received unknown chunk " + name(type)
                + ": len=" + data.limit() + ", reply=" + isReply
                + ", msgId=0x" + Integer.toHexString(msgId));
        }
        Log.w("ddms", "         client " + client + ", handler " + this);
    }


    /**
     * Utility function to copy a String out of a ByteBuffer.
     *
     * This is here because multiple chunk handlers can make use of it,
     * and there's nowhere better to put it.
     */
    public static String getString(ByteBuffer buf, int len) {
        char[] data = new char[len];
        for (int i = 0; i < len; i++)
            data[i] = buf.getChar();
        return new String(data);
    }

    /**
     * Utility function to copy a String into a ByteBuffer.
     */
    static void putString(ByteBuffer buf, String str) {
        int len = str.length();
        for (int i = 0; i < len; i++)
            buf.putChar(str.charAt(i));
    }

    /**
     * Convert a 4-character string to a 32-bit type.
     */
    static int type(String typeName) {
        int val = 0;

        if (typeName.length() != 4) {
            Log.e("ddms", "Type name must be 4 letter long");
            throw new RuntimeException("Type name must be 4 letter long");
        }

        for (int i = 0; i < 4; i++) {
            val <<= 8;
            val |= (byte) typeName.charAt(i);
        }

        return val;
    }

    /**
     * Convert an integer type to a 4-character string.
     */
    static String name(int type) {
        char[] ascii = new char[4];

        ascii[0] = (char) ((type >> 24) & 0xff);
        ascii[1] = (char) ((type >> 16) & 0xff);
        ascii[2] = (char) ((type >> 8) & 0xff);
        ascii[3] = (char) (type & 0xff);

        return new String(ascii);
    }

    /**
     * Allocate a ByteBuffer with enough space to hold the JDWP packet
     * header and one chunk header in addition to the demands of the
     * chunk being created.
     *
     * "maxChunkLen" indicates the size of the chunk contents only.
     */
    static ByteBuffer allocBuffer(int maxChunkLen) {
        ByteBuffer buf =
            ByteBuffer.allocate(JdwpPacket.JDWP_HEADER_LEN + 8 +maxChunkLen);
        buf.order(CHUNK_ORDER);
        return buf;
    }

    /**
     * Return the slice of the JDWP packet buffer that holds just the
     * chunk data.
     */
    static ByteBuffer getChunkDataBuf(ByteBuffer jdwpBuf) {
        ByteBuffer slice;

        assert jdwpBuf.position() == 0;

        jdwpBuf.position(JdwpPacket.JDWP_HEADER_LEN + CHUNK_HEADER_LEN);
        slice = jdwpBuf.slice();
        slice.order(CHUNK_ORDER);
        jdwpBuf.position(0);

        return slice;
    }

    /**
     * Write the chunk header at the start of the chunk.
     *
     * Pass in the byte buffer returned by JdwpPacket.getPayload().
     */
    static void finishChunkPacket(JdwpPacket packet, int type, int chunkLen) {
        ByteBuffer buf = packet.getPayload();

        buf.putInt(0x00, type);
        buf.putInt(0x04, chunkLen);

        packet.finishPacket(CHUNK_HEADER_LEN + chunkLen);
    }

    /**
     * Check that the client is opened with the proper debugger port for the
     * specified application name, and if not, reopen it.
     * @param client
     * @param uiThread
     * @param appName
     * @return
     */
    protected static Client checkDebuggerPortForAppName(Client client, String appName) {
        IDebugPortProvider provider = DebugPortManager.getProvider();
        if (provider != null) {
            Device device = client.getDeviceImpl();
            int newPort = provider.getPort(device, appName);

            if (newPort != IDebugPortProvider.NO_STATIC_PORT &&
                    newPort != client.getDebuggerListenPort()) {

                AndroidDebugBridge bridge = AndroidDebugBridge.getBridge();
                if (bridge != null) {
                    DeviceMonitor deviceMonitor = bridge.getDeviceMonitor();
                    if (deviceMonitor != null) {
                        deviceMonitor.addClientToDropAndReopen(client, newPort);
                        client = null;
                    }
                }
            }
        }

        return client;
    }
}

