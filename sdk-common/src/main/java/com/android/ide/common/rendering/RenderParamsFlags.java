/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.ide.common.rendering;

import com.android.ide.common.rendering.api.RenderParams;
import com.android.ide.common.rendering.api.SessionParams.Key;

/**
 * This contains all known keys for the {@link RenderParams#setFlag(Key, Object)}.
 * <p/>
 * LayoutLib has it's own copy of this class which may be newer or older than this one.
 * <p/>
 * Constants should never be modified or removed from this class.
 */
public final class RenderParamsFlags {

    public static final Key<String> FLAG_KEY_ROOT_TAG =
        new Key<String>("rootTag", String.class);
    public static final Key<Boolean> FLAG_KEY_DISABLE_BITMAP_CACHING =
        new Key<Boolean>("disableBitmapCaching", Boolean.class);
    public static final Key<Boolean> FLAG_KEY_RENDER_ALL_DRAWABLE_STATES =
        new Key<Boolean>("renderAllDrawableStates", Boolean.class);
    /**
     * To tell LayoutLib that the IDE supports RecyclerView.
     * <p/>
     * Default is false.
     */
    public static final Key<Boolean> FLAG_KEY_RECYCLER_VIEW_SUPPORT =
        new Key<Boolean>("recyclerViewSupport", Boolean.class);

    // Disallow instances.
    private RenderParamsFlags() {}
}
