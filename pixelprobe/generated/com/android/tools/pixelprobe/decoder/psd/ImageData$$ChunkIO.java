package com.android.tools.pixelprobe.decoder.psd;

import com.android.tools.chunkio.ChunkUtils;
import com.android.tools.chunkio.RangedInputStream;
import java.io.IOException;
import java.util.LinkedList;

final class ImageData$$ChunkIO {
    static ImageData read(RangedInputStream in, LinkedList<Object> stack) throws IOException {
        ImageData imageData = new ImageData();
        stack.addFirst(imageData);

        int size = 0;
        long byteCount = 0;

        imageData.compression = CompressionMethod.values()[
                Math.max(0, Math.min(in.readUnsignedShort(), CompressionMethod.values().length - 1))];
        imageData.data = ChunkUtils.readUnboundedByteArray(in, 131072);

        stack.removeFirst();
        return imageData;
    }
}
