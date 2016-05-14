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

package com.android.tools.chunkio.codegen;

import javax.lang.model.element.Modifier;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

public final class ParameterDef {
    private final String mName;
    private final TypeDef mType;
    private final Set<Modifier> mModifiers;

    private ParameterDef(Builder builder) {
        mName = builder.mName;
        mType = builder.mType;
        mModifiers = Utils.immutableCopy(builder.mModifiers);
    }

    public static Builder builder(Type type, String name, Modifier... modifiers) {
        return new Builder(type, name).addModifiers(modifiers);
    }

    public static Builder builder(TypeDef type, String name, Modifier... modifiers) {
        return new Builder(type, name).addModifiers(modifiers);
    }

    void emit(CodeGenerator generator) throws IOException {
        generator.emitModifiers(mModifiers);
        generator.emit("$T $L", mType, mName);
    }

    public static final class Builder {
        private final String mName;
        private TypeDef mType;
        private final Set<Modifier> mModifiers = new LinkedHashSet<>();

        private Builder(Type type, String name) {
            mName = name;
            mType = TypeDef.of(type);
        }

        private Builder(TypeDef type, String name) {
            mType = type;
            mName = name;
        }

        Builder addModifiers(Modifier... modifiers) {
            Collections.addAll(mModifiers, modifiers);
            return this;
        }

        ParameterDef build() {
            return new ParameterDef(this);
        }
    }
}
