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

package android.graphics;

import androidx.annotation.NonNull;
import java.io.IOException;
import java.io.OutputStream;

/**
 * This class is included for testing of LayoutInspectorService.
 *
 * <p>Only the methods needed for LayoutInspectorService is included.
 */
public class Picture {
    private static CanvasFactory ourCanvasFactory;
    private final Canvas mCanvas;
    private byte[] mBytes;

    public Picture() {
        mCanvas = ourCanvasFactory.createCanvas(this);
    }

    public static void setCanvasFactory(@NonNull CanvasFactory canvasFactory) {
        ourCanvasFactory = canvasFactory;
    }

    public void setImage(@NonNull byte[] bytes) {
        mBytes = bytes;
    }

    @SuppressWarnings("unused")
    public Canvas beginRecording(int width, int height) {
        return mCanvas;
    }

    public void endRecording() {}

    public void writeToStream(@NonNull OutputStream stream) throws IOException {
        stream.write(mBytes);
    }

    public interface CanvasFactory {
        Canvas createCanvas(@NonNull Picture picture);
    }
}
