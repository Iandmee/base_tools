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

import org.gradle.api.Action
import org.gradle.api.Incubating
import java.util.regex.Pattern

/**
 * [Variant] or [VariantProperties] filter with a specific build type.
 */
@Incubating
interface BuildTypedVariantFilterBuilder<T> {
    /**
     * Filters [T] instances with a flavor
     *
     * @param flavorToDimension the flavor name to flavor dimension
     * @param action [Action] to perform on each filtered variant.
     */
    fun withFlavor(flavorToDimension: Pair<String, String>, action: Action<T>)

    /**
     * Filters [T] instances with a flavor
     *
     * @param flavorToDimension the flavor name to flavor dimension
     * @param action [Action] to perform on each filtered variant.
     */
    fun withFlavor(flavorToDimension: Pair<String, String>, action: T.() -> Unit)

    /**
     * Filters [T] instances with a flavor, and return the same filter instance for further product
     * flavor filtering.
     *
     * @param flavorToDimension the flavor name to flavor dimension
     * @param action [Action] to perform on each filtered variant.
     */
    fun withFlavor(flavorToDimension: Pair<String, String>): BuildTypedVariantFilterBuilder<T>
}
