/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.tools.chunkio;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.Collection;

@SuppressWarnings({ "unused", "WeakerAccess" })
public final class ChunkUtils {
    private ChunkUtils() {
    }

    public static void checkState(boolean condition, String format, Object... args) {
        if (!condition) throw new ChunkException(String.format(format, args));
    }

    public static <T> String join(Collection<T> list, String delimiter) {
        StringBuilder builder = new StringBuilder();

        int count = 0;
        for (T element : list) {
            builder.append(element.toString());
            if (count != list.size() - 1) {
                builder.append(delimiter);
            }
            count++;
        }

        return builder.toString();
    }

    public static boolean readBoolean(RangedInputStream in, long byteCount) throws IOException {
        boolean b;
        switch ((int) byteCount) {
            case -1:
            case 1:
                b = in.readByte() != 0;
                break;
            case 2:
                b = in.readShort() != 0;
                break;
            case 4:
                b = in.readInt() != 0;
                break;
            case 8:
                b = in.readLong() != 0;
                break;
            default:
                b = in.readByte() != 0;
                skip(in, byteCount - 1);
                break;
        }
        return b;
    }

    public static byte readByte(RangedInputStream in, long byteCount) throws IOException {
        byte b;
        switch ((int) byteCount) {
            case -1:
            case 1:
                b = in.readByte();
                break;
            default:
                b = in.readByte();
                skip(in, byteCount - 1);
                break;
        }
        return b;
    }

    public static char readChar(RangedInputStream in, long byteCount) throws IOException {
        char c;
        switch ((int) byteCount) {
            case 1:
                c = (char) (in.readByte() & 0xff);
                break;
            case -1:
            case 2:
                c = in.readChar();
                break;
            default:
                c = in.readChar();
                skip(in, byteCount - 2);
                break;
        }
        return c;
    }

    public static double readDouble(RangedInputStream in, long byteCount) throws IOException {
        double d;
        switch ((int) byteCount) {
            case 4:
                d = in.readFloat();
                break;
            case -1:
            case 8:
                d = in.readDouble();
                break;
            default:
                d = in.readDouble();
                skip(in, byteCount - 8);
                break;
        }
        return d;
    }

    public static float readFloat(RangedInputStream in, long byteCount) throws IOException {
        float f;
        switch ((int) byteCount) {
            case -1:
            case 4:
                f = in.readFloat();
                break;
            default:
                f = in.readFloat();
                skip(in, byteCount - 4);
                break;
        }
        return f;
    }

    public static int readInt(RangedInputStream in, long byteCount) throws IOException {
        int i;
        switch ((int) byteCount) {
            case 1:
                i = in.readUnsignedByte();
                break;
            case 2:
                i = in.readUnsignedShort();
                break;
            case -1:
            case 4:
                i = in.readInt();
                break;
            default:
                i = in.readInt();
                skip(in, byteCount - 4);
                break;
        }
        return i;
    }

    public static long readLong(RangedInputStream in, long byteCount) throws IOException {
        long l;
        switch ((int) byteCount) {
            case 1:
                l = in.readUnsignedByte();
                break;
            case 2:
                l = in.readUnsignedShort();
                break;
            case 4:
                l = in.readInt() & 0xffffffffL;
                break;
            case -1:
            case 8:
                l = in.readLong();
                break;
            default:
                l = in.readLong();
                skip(in, byteCount - 8);
                break;
        }
        return l;
    }

    public static short readShort(RangedInputStream in, long byteCount) throws IOException {
        short s;
        switch ((int) byteCount) {
            case 1:
                s = (short) (in.readByte() & 0xff);
                break;
            case -1:
            case 2:
                s = in.readShort();
                break;
            default:
                s = in.readShort();
                skip(in, byteCount - 2);
                break;
        }
        return s;
    }

    public static void skip(RangedInputStream in, long byteCount) throws IOException {
        if (byteCount > 0) {
            long skipped = 0;
            long toSkip = byteCount;

            while (skipped < byteCount) {
                long s = in.skip(toSkip);
                if (s < 0) break;
                skipped += s;
                toSkip -= s;
            }
        }
    }

    static long copy(InputStream from, OutputStream to, int bufferSize) throws IOException {
        byte[] buf = new byte[bufferSize];

        long total = 0;
        while (true) {
            int read = from.read(buf);
            if (read < 0) break;
            to.write(buf, 0, read);
            total += read;
        }

        return total;
    }

    public static byte[] readUnboundedByteArray(RangedInputStream in, int bufferSize)
            throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        copy(in, out, bufferSize);
        return out.toByteArray();
    }

    public static byte[] readByteArray(RangedInputStream in, long byteCount) throws IOException {
        byte[] data = new byte[(int) byteCount];
        in.readFully(data);
        return data;
    }

    public static String readString(RangedInputStream in, long byteCount, Charset charset)
            throws IOException {
        return new String(readByteArray(in, byteCount), charset);
    }

    public static byte[] readByteArray(RangedInputStream in, long byteCount, int bufferSize)
            throws IOException {
        if (byteCount >= 0) {
            return readByteArray(in, byteCount);
        } else {
            return readUnboundedByteArray(in, bufferSize);
        }
    }
}
