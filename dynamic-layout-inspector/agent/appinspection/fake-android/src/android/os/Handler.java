/*
 * Copyright (C) 2021 The Android Open Source Project
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

package android.os;

import androidx.annotation.NonNull;

// Note: This class must be written in Java, as Kotlin otherwise creates a companion object for the
// static methods, which it references even when the methods are marked @JvmStatic if called from
// Kotlin source (which the inspector is). This causes a runtime exception on the device in its
// Java version, where there is no Companion object.
public final class Handler {

    @NonNull
    public static Handler createAsync(@NonNull Looper looper) {
        return new Handler(looper);
    }

    @NonNull private final Looper mLooper;

    public Handler(@NonNull Looper looper) {
        mLooper = looper;
    }

    @NonNull
    public Looper getLooper() {
        return mLooper;
    }

    public boolean post(@NonNull Runnable r) {
        return mLooper.post(r);
    }
}
