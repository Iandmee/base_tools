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

package com.android.build.gradle.internal.dependency

import org.gradle.api.attributes.Attribute
import org.gradle.api.attributes.AttributeContainer
import org.gradle.api.attributes.LibraryElements

/** Contains attributes of different types. */
class AndroidAttributes(
    private val stringAttributes: Map<Attribute<String>, String>? = null,
    private val libraryElementsAttribute: LibraryElements? = null
) {

    constructor(stringAttribute: Pair<Attribute<String>, String>) : this(mapOf(stringAttribute))

    fun addAttributesToContainer(container: AttributeContainer) {
        stringAttributes?.let {
            for ((key, value) in it) {
                container.attribute(key, value)
            }
        }
        libraryElementsAttribute?.let {
            container.attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, it)
        }
    }
}