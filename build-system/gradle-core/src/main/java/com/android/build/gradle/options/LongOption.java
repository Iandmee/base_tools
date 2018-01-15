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

package com.android.build.gradle.options;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.internal.errors.DeprecationReporter;

public enum LongOption implements Option<Long> {
    DEPRECATED_NDK_COMPILE_LEASE("android.deprecatedNdkCompileLease"),
    ;

    @NonNull private final String propertyName;
    @Nullable private final DeprecationReporter.DeprecationTarget deprecationTarget;

    LongOption(@NonNull String propertyName) {
        this(propertyName, null);
    }

    LongOption(
            @NonNull String propertyName,
            @Nullable DeprecationReporter.DeprecationTarget deprecationTarget) {
        this.propertyName = propertyName;
        this.deprecationTarget = deprecationTarget;
    }

    @Override
    @NonNull
    public final String getPropertyName() {
        return propertyName;
    }

    @Nullable
    @Override
    public final Long getDefaultValue() {
        return null;
    }

    @NonNull
    @Override
    public final Long parse(@NonNull Object value) {
        if (value instanceof CharSequence) {
            try {
                return Long.parseLong(value.toString());
            } catch (NumberFormatException ignored) {
                // Throws below.
            }
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        throw new IllegalArgumentException(
                "Cannot parse project property "
                        + this.getPropertyName()
                        + "='"
                        + value
                        + "' of type '"
                        + value.getClass()
                        + "' as long.");
    }

    @Override
    public boolean isDeprecated() {
        return (deprecationTarget != null);
    }

    @Nullable
    @Override
    public DeprecationReporter.DeprecationTarget getDeprecationTarget() {
        return deprecationTarget;
    }
}
