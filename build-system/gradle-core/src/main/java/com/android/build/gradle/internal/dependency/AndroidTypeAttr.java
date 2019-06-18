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

package com.android.build.gradle.internal.dependency;

import static com.android.builder.core.VariantTypeKt.ATTR_AAR;
import static com.android.builder.core.VariantTypeKt.ATTR_APK;
import static com.android.builder.core.VariantTypeKt.ATTR_FEATURE;

import org.gradle.api.attributes.Attribute;

/** Type for Build Type attributes in Gradle's configuration objects. */
public interface AndroidTypeAttr extends org.gradle.api.Named {
    Attribute<AndroidTypeAttr> ATTRIBUTE = Attribute.of(AndroidTypeAttr.class);

    String APK = ATTR_APK;
    String AAR = ATTR_AAR;
    String FEATURE = ATTR_FEATURE;
}
