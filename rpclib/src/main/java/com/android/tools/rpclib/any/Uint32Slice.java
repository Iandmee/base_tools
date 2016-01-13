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

import com.android.tools.rpclib.schema.*;
import org.jetbrains.annotations.NotNull;

import com.android.tools.rpclib.binary.BinaryClass;
import com.android.tools.rpclib.binary.BinaryObject;
import com.android.tools.rpclib.binary.Decoder;
import com.android.tools.rpclib.binary.Encoder;
import com.android.tools.rpclib.binary.Namespace;

import java.io.IOException;

final class Uint32Slice extends Box implements BinaryObject {
    @Override
    public Object unwrap() {
        return getValue();
    }

    //<<<Start:Java.ClassBody:1>>>
    private int[] mValue;

    // Constructs a default-initialized {@link Uint32Slice}.
    public Uint32Slice() {}


    public int[] getValue() {
        return mValue;
    }

    public Uint32Slice setValue(int[] v) {
        mValue = v;
        return this;
    }

    @Override @NotNull
    public BinaryClass klass() { return Klass.INSTANCE; }


    private static final Entity ENTITY = new Entity("any", "uint32Slice", "", "");

    static {
        ENTITY.setFields(new Field[]{
            new Field("Value", new Slice("", new Primitive("uint32", Method.Uint32))),
        });
        Namespace.register(Klass.INSTANCE);
    }
    public static void register() {}
    //<<<End:Java.ClassBody:1>>>
    public enum Klass implements BinaryClass {
        //<<<Start:Java.KlassBody:2>>>
        INSTANCE;

        @Override @NotNull
        public Entity entity() { return ENTITY; }

        @Override @NotNull
        public BinaryObject create() { return new Uint32Slice(); }

        @Override
        public void encode(@NotNull Encoder e, BinaryObject obj) throws IOException {
            Uint32Slice o = (Uint32Slice)obj;
            e.uint32(o.mValue.length);
            for (int i = 0; i < o.mValue.length; i++) {
                e.uint32(o.mValue[i]);
            }
        }

        @Override
        public void decode(@NotNull Decoder d, BinaryObject obj) throws IOException {
            Uint32Slice o = (Uint32Slice)obj;
            o.mValue = new int[d.uint32()];
            for (int i = 0; i <o.mValue.length; i++) {
                o.mValue[i] = d.uint32();
            }
        }
        //<<<End:Java.KlassBody:2>>>
    }
}
