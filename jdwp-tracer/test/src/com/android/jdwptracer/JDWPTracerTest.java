/*
 * Copyright (C) 2023 The Android Open Source Project
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
import org.junit.Assert;
import org.junit.Test;

public class JDWPTracerTest {

    @Test
    public void packetHeaderParsing() {
        AssertableLog logs = new AssertableLog();
        JDWPTracer tracer = new JDWPTracer(true, logs);
        ByteBuffer badPacket = ByteBuffer.allocate(0);

        try {
            tracer.addPacket(badPacket);
        } catch (Exception e) {
            Assert.fail();
        }

        Assert.assertEquals(logs.getWarnings().size(), 1);
    }
}