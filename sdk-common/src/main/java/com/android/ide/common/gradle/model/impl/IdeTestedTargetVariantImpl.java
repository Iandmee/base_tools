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
package com.android.ide.common.gradle.model.impl;

import com.android.annotations.NonNull;
import com.android.ide.common.gradle.model.IdeTestedTargetVariant;
import com.google.common.annotations.VisibleForTesting;
import java.io.Serializable;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;

/** Creates a deep copy of a `TestedTargetVariant`. */
public final class IdeTestedTargetVariantImpl implements IdeTestedTargetVariant, Serializable {
    // Increase the value when adding/removing fields or when changing the serialization/deserialization mechanism.
    private static final long serialVersionUID = 2L;

    @NonNull private final String myTargetProjectPath;
    @NonNull private final String myTargetVariant;
    private final int myHashCode;

    // Used for serialization by the IDE.
    @VisibleForTesting
    @SuppressWarnings("unused")
    public IdeTestedTargetVariantImpl() {
        myTargetProjectPath = "";
        myTargetVariant = "";

        myHashCode = 0;
    }

    public IdeTestedTargetVariantImpl(
            @NotNull String targetProjectPath, @NotNull String targetVariant) {
        myTargetProjectPath = targetProjectPath;
        myTargetVariant = targetVariant;

        myHashCode = calculateHashCode();
    }

    @Override
    @NonNull
    public String getTargetProjectPath() {
        return myTargetProjectPath;
    }

    @Override
    @NonNull
    public String getTargetVariant() {
        return myTargetVariant;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof IdeTestedTargetVariantImpl)) {
            return false;
        }
        IdeTestedTargetVariantImpl variants = (IdeTestedTargetVariantImpl) o;
        return Objects.equals(myTargetProjectPath, variants.myTargetProjectPath)
                && Objects.equals(myTargetVariant, variants.myTargetVariant);
    }

    @Override
    public int hashCode() {
        return myHashCode;
    }

    private int calculateHashCode() {
        return Objects.hash(myTargetProjectPath, myTargetVariant);
    }

    @Override
    public String toString() {
        return "IdeTestedTargetVariants{"
                + "myTargetProjectPath='"
                + myTargetProjectPath
                + '\''
                + ", myTargetVariant='"
                + myTargetVariant
                + '\''
                + "}";
    }
}
