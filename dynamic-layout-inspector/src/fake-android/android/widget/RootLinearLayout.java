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

package android.widget;

import android.content.Context;
import android.view.AttachInfo;

/**
 * This class is included for testing of LayoutInspectorService.
 *
 * <p>The class SkiaQWorkaround will look for a "mAttachInfo" field on the root view that the
 * LayoutInspectorService is gathering information about. A test can use this class to simulate the
 * existence of this field. The field is removed in the android.jar we are compiling against.
 */
public class RootLinearLayout extends LinearLayout {
    private AttachInfo mAttachInfo;

    public RootLinearLayout(Context context) {
        super(context);
    }
}
