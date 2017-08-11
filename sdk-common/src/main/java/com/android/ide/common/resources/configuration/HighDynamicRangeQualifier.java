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

package com.android.ide.common.resources.configuration;

import com.android.annotations.Nullable;
import com.android.resources.HighDynamicRange;
import com.android.resources.ResourceEnum;

public class HighDynamicRangeQualifier extends EnumBasedResourceQualifier {

    public static final String NAME = "Dynamic Range";

    @Nullable private HighDynamicRange mValue = null;

    public HighDynamicRangeQualifier() {}

    public HighDynamicRangeQualifier(@Nullable HighDynamicRange value) {
        mValue = value;
    }

    public HighDynamicRange getValue() {
        return mValue;
    }

    @Override
    ResourceEnum getEnumValue() {
        return mValue;
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String getShortName() {
        return "HDR";
    }

    @Override
    public int since() {
        return 26;
    }

    @Override
    public boolean checkAndSet(String value, FolderConfiguration config) {
        HighDynamicRange enumValue = HighDynamicRange.getEnum(value);
        if (enumValue != null) {
            HighDynamicRangeQualifier qualifier = new HighDynamicRangeQualifier(enumValue);
            config.setHighDynamicRangeQualifier(qualifier);
            return true;
        }

        return false;
    }
}
