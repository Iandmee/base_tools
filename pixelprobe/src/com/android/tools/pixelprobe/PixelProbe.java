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

package com.android.tools.pixelprobe;

import java.io.BufferedInputStream;
import java.io.InputStream;

/**
 * The PixelProbe class exposes functions to decode input streams
 * as images. If the desired format is not specified, PixelProbe
 * will attempt to guess the format of the stream's data.
 */
public final class PixelProbe {
    private PixelProbe() {
    }

    /**
     * Decodes an image from the specified input stream.
     * This method will attempt to guess the image format by reading
     * the beginning of the stream. It is therefore recommended to
     * pass an InputStream that supports mark/reset. If the specified
     * stream does not support mark/reset, this method will wrap the
     * stream with a BufferedInputStream.
     *
     * This method does not close the stream.
     *
     * The returned image is always non-null but you must check
     * the return value of Image.isValid() before attempting to use
     * it. If the image is marked invalid, an error occurred while
     * decoding the stream.
     *
     * @param in The input stream to decode an image from
     *
     * @return An Image instance, never null.
     */
    public static Image probe(InputStream in) {
        // Make sure we have rewind capabilities to run Decoder.accept()
        if (!in.markSupported()) {
            in = new BufferedInputStream(in);
        }

        Decoder decoder = Decoders.find(in);
        if (decoder == null) {
            return new Image();
        }

        return createImageFromStream(decoder, in);
    }

    /**
     * Decodes an image from the specified input stream, using the
     * specified format. The format can be a file extension or a
     * descriptive name. Examples: "png", "jpg", "jpeg", "psd",
     * "photoshop". Format matching is case insensitive. If a
     * suitable decoder cannot be found, this method will delegate
     * to @{link #probe(InputStream)}.
     *
     * This method does not close the stream.
     *
     * The returned image is always non-null but you must check
     * the return value of Image.isValid() before attempting to use
     * it. If the image is marked invalid, an error occurred while
     * decoding the stream.
     *
     * @param in The input stream to decode an image from
     * @param format The expected format of the image to decode from the stream
     *
     * @return An Image instance, never null.
     */
    public static Image probe(String format, InputStream in) {
        Decoder decoder = Decoders.find(format);
        if (decoder == null) {
            return probe(in);
        }

        return createImageFromStream(decoder, in);
    }

    private static Image createImageFromStream(Decoder decoder, InputStream in) {
        if (decoder == null) {
            return new Image();
        }
        try {
            return decoder.decode(in);
        } catch (DecoderException e) {
            return new Image();
        }
    }

    //public static void main(String[] args) throws IOException {
    //    InputStream in = new BufferedInputStream(new FileInputStream(args[0]));
    //    Image image = probe(in);
    //    if (image.isValid()) {
    //        System.out.println("image = " + image);
    //    }
    //    in.close();
    //}
}
