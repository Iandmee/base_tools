/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.build.api.variant

import com.android.build.api.component.ComponentProperties
import org.gradle.api.Incubating
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider

/**
 * Parent interface for all types of variants.
 */
@Incubating
interface VariantProperties: ComponentProperties {

    /**
     * Variant's application ID as present in the final manifest file of the APK.
     *
     * Some type of variants allows this to be writeable but for some it's only read-only.
     */
    val applicationId: Provider<String>

}