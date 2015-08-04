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

package com.android.builder.shrinker;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.File;

/**
 * {@link ClassVisitor} that adds visited classes, methods and fields to a {@link ShrinkerGraph}.
 */
public class ClassStructureVisitor<T> extends ClassVisitor {

    private final File mClassFile;
    private final ShrinkerGraph<T> mGraph;

    private String mName;

    public ClassStructureVisitor(ShrinkerGraph<T> graph, File classFile, ClassVisitor cv) {
        super(Opcodes.ASM5, cv);
        mClassFile = classFile;
        mGraph = graph;
    }

    @Override
    public void visit(int version, int access, String name, String signature, String superName,
            String[] interfaces) {
        mName = name;
        mGraph.addClass(name, superName, interfaces, mClassFile);
        super.visit(version, access, name, signature, superName, interfaces);
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String desc, String signature,
            String[] exceptions) {
        mGraph.addMember(mName, name, desc);
        return super.visitMethod(access, name, desc, signature, exceptions);
    }

    @Override
    public FieldVisitor visitField(int access, String name, String desc, String signature,
            Object value) {
        mGraph.addMember(mName, name, desc);
        return super.visitField(access, name, desc, signature, value);
    }
}
