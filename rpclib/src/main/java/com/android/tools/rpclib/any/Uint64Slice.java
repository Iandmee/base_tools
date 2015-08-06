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
 *
 * THIS FILE WAS GENERATED BY codergen. EDIT WITH CARE.
 */
package com.android.tools.rpclib.any;

import org.jetbrains.annotations.NotNull;

import com.android.tools.rpclib.binary.BinaryClass;
import com.android.tools.rpclib.binary.BinaryID;
import com.android.tools.rpclib.binary.BinaryObject;
import com.android.tools.rpclib.binary.Decoder;
import com.android.tools.rpclib.binary.Encoder;
import com.android.tools.rpclib.binary.Namespace;

import java.io.IOException;

final class Uint64Slice extends Box implements BinaryObject {
    @Override
    public Object unwrap() {
        return getValue();
    }

    //<<<Start:Java.ClassBody:1>>>
    long[] mValue;

    // Constructs a default-initialized {@link Uint64Slice}.
    public Uint64Slice() {}


    public long[] getValue() {
        return mValue;
    }

    public Uint64Slice setValue(long[] v) {
        mValue = v;
        return this;
    }

    @Override @NotNull
    public BinaryClass klass() { return Klass.INSTANCE; }

    private static final byte[] IDBytes = {-62, -6, -110, 106, -31, -19, 85, -55, 84, -22, 18, -74, -71, 103, 45, 62, -115, 28, 65, -123, };
    public static final BinaryID ID = new BinaryID(IDBytes);

    static {
        Namespace.register(ID, Klass.INSTANCE);
    }
    public static void register() {}
    //<<<End:Java.ClassBody:1>>>
    public enum Klass implements BinaryClass {
        //<<<Start:Java.KlassBody:2>>>
        INSTANCE;

        @Override @NotNull
        public BinaryID id() { return ID; }

        @Override @NotNull
        public BinaryObject create() { return new Uint64Slice(); }

        @Override
        public void encode(@NotNull Encoder e, BinaryObject obj) throws IOException {
            Uint64Slice o = (Uint64Slice)obj;
            e.uint32(o.mValue.length);
            for (int i = 0; i < o.mValue.length; i++) {
                e.uint64(o.mValue[i]);
            }
        }

        @Override
        public void decode(@NotNull Decoder d, BinaryObject obj) throws IOException {
            Uint64Slice o = (Uint64Slice)obj;
            o.mValue = new long[d.uint32()];
            for (int i = 0; i <o.mValue.length; i++) {
                o.mValue[i] = d.uint64();
            }
        }
        //<<<End:Java.KlassBody:2>>>
    }
}
