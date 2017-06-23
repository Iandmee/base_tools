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

package com.android.build.gradle.external.cmake.server;

import com.android.annotations.NonNull;
import java.io.IOException;
import java.util.List;

/**
 * Implementation of version 1 of Cmake server for Cmake versions 3.7.1. Cmake server or a long
 * running mode which allows a client to configure and request buildsystem information generated by
 * Cmake. More info: https://cmake.org/cmake/help/v3.7/manual/cmake-server.7.html
 */
//TODO(kravindran) - Rename the class before submitting
public class ServerV1 implements Server {

    ServerV1() {}

    @Override
    public boolean connect() throws IOException {
        return false;
    }

    @Override
    public void disconnect() {}

    @Override
    public boolean isConnected() {
        return false;
    }

    @Override
    public List<ProtocolVersion> getSupportedVersion() {
        return null;
    }

    @Override
    public HandshakeResult handshake(@NonNull HandshakeRequest handshakeRequest)
            throws IOException {
        return null;
    }

    @Override
    public ConfigureCommandResult configure(String... cacheArguments) throws IOException {
        return null;
    }

    @Override
    public ComputeResult compute() throws IOException {
        return null;
    }

    @Override
    public CodeModel codemodel() throws IOException {
        return null;
    }

    @Override
    public CacheResult cache() throws IOException {
        return null;
    }

    @Override
    public GlobalSettings globalSettings() throws IOException {
        return null;
    }

    @Override
    public String getCCompilerExecutable() {
        return null;
    }

    @Override
    public String getCppCompilerExecutable() {
        return null;
    }
}
