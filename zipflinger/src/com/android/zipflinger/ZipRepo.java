/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.zipflinger;

import com.android.annotations.NonNull;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Map;

public class ZipRepo implements Closeable {

    private final ZipMap zipMap;
    private final FileChannel channel;
    private final File file;

    public ZipRepo(@NonNull String filePath) throws IOException {
        this(ZipMap.from(new File(filePath), false, Zip64.Policy.ALLOW));
    }

    public ZipRepo(@NonNull File file) throws IOException {
        this(ZipMap.from(file, false, Zip64.Policy.ALLOW));
    }

    public ZipRepo(@NonNull Path path) throws IOException {
        this(ZipMap.from(path.toFile(), false, Zip64.Policy.ALLOW));
    }

    public ZipRepo(@NonNull ZipMap zipMap) throws IOException {
        this.zipMap = zipMap;
        this.channel = FileChannel.open(zipMap.getFile().toPath(), StandardOpenOption.READ);
        this.file = zipMap.getFile();
    }

    @NonNull
    public Map<String, Entry> getEntries() {
        return zipMap.getEntries();
    }

    @NonNull
    ZipMap getZipMap() {
        return zipMap;
    }

    @Override
    public void close() throws IOException {
        channel.close();
    }

    // Is it the caller's responsibility to close() the returned InputStream.
    @NonNull
    public InputStream getContent(@NonNull String entryName) throws IOException {
        Entry entry = zipMap.getEntries().get(entryName);
        if (entry == null) {
            String msg = String.format("No entry '%s' in file '%s'", entryName, file);
            throw new IllegalArgumentException(msg);
        }

        Location payloadLocation = entry.getPayloadLocation();
        InputStream inputStream = new PayloadInputStream(channel, payloadLocation);

        if (!entry.isCompressed()) {
            return inputStream;
        }

        return Compressor.wrapToInflate(inputStream);
    }
}
