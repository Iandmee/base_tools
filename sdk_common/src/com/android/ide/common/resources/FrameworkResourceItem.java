/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.android.ide.common.resources;

/**
 * A custom {@link ResourceItem} for resources provided by the framework.
 *
 * The main change is that {@link #isEditableDirectly()} returns false.
 */
class FrameworkResourceItem extends ResourceItem {

    FrameworkResourceItem(String name) {
        super(name);
    }

    @Override
    public boolean isEditableDirectly() {
        return false;
    }

    @Override
    public String toString() {
        return "FrameworkResourceItem [mName=" + getName() + ", mFiles=" //$NON-NLS-1$ //$NON-NLS-2$
                + getSourceFileList() + "]"; //$NON-NLS-1$
    }
}
