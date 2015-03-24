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
package com.android.tools.rpclib.binary;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.Map;

/**
 * An encoder of various primitive types. All types are encoded little-endian.
 */
public class Encoder {
  private final OutputStream out;
  private final Map<BinaryObject, Integer> encoded;
  private final byte buf[];

  public Encoder(@NotNull OutputStream out) {
    encoded = new HashMap<BinaryObject, Integer>();
    this.out = out;
    buf = new byte[8];
  }

  private void write(int count) throws IOException {
    out.write(buf, 0, count);
  }

  public void bool(boolean v) throws IOException {
    buf[0] = (byte)(v ? 1 : 0);
    write(1);
  }

  public void int8(byte v) throws IOException {
    buf[0] = v;
    write(1);
  }

  public void uint8(short v) throws IOException {
    buf[0] = (byte)(v & 0xff);
    write(1);
  }

  public void int16(short v) throws IOException {
    buf[0] = (byte)((v >>> 0) & 0xff);
    buf[1] = (byte)((v >>> 8) & 0xff);
    write(2);
  }

  public void uint16(int v) throws IOException {
    int16((short)(v & 0xffff));
  }

  public void int32(int v) throws IOException {
    buf[0] = (byte)((v >>> 0) & 0xff);
    buf[1] = (byte)((v >>> 8) & 0xff);
    buf[2] = (byte)((v >>> 16) & 0xff);
    buf[3] = (byte)((v >>> 24) & 0xff);
    write(4);
  }

  public void uint32(long v) throws IOException {
    int32((int)(v & 0xffffffff));
  }

  public void int64(long v) throws IOException {
    buf[0] = (byte)((v >>> 0) & 0xff);
    buf[1] = (byte)((v >>> 8) & 0xff);
    buf[2] = (byte)((v >>> 16) & 0xff);
    buf[3] = (byte)((v >>> 24) & 0xff);
    buf[4] = (byte)((v >>> 32) & 0xff);
    buf[5] = (byte)((v >>> 40) & 0xff);
    buf[6] = (byte)((v >>> 48) & 0xff);
    buf[7] = (byte)((v >>> 56) & 0xff);
    write(8);
  }

  public void uint64(long v) throws IOException {
    int64(v);
  }

  public void float32(float v) throws IOException {
    int32(Float.floatToIntBits(v));
  }

  public void float64(double v) throws IOException {
    int64(Double.doubleToLongBits(v));
  }

  public void string(String v) throws IOException {
    try {
      byte[] bytes = v.getBytes("UTF-8");
      int32((short)(bytes.length));
      for (byte b : bytes) {
        int8(b);
      }
    }
    catch (UnsupportedEncodingException e) {
      throw new RuntimeException(e); // Should never happen
    }
  }

  public void object(@Nullable BinaryObject obj) throws IOException {
    if (obj == null) {
      uint16(BinaryObject.NULL_ID);
      return;
    }

    if (encoded.containsKey(obj)) {
      int key = encoded.get(obj);
      uint16(key);
      return;
    }

    int key = encoded.size();
    encoded.put(obj, key);
    uint16(key);
    obj.type().encode(this);
    obj.encode(this);
  }

  public OutputStream stream() {
    return out;
  }
}
