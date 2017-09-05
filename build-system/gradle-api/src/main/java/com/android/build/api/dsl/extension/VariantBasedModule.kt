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

package com.android.build.api.dsl.extension

import com.android.build.api.dsl.model.BuildType
import com.android.build.api.dsl.model.ProductFlavor
import com.android.build.api.dsl.options.SigningConfig
import com.android.build.api.dsl.variant.Variant
import com.android.build.api.variant.VariantFilter
import org.gradle.api.Action
import org.gradle.api.DomainObjectSet
import org.gradle.api.NamedDomainObjectContainer

/** Partial extension properties for modules that have flavors  */
interface VariantBasedModule {

    /** Default config, shared by all flavors.  */
    var defaultConfig: ProductFlavor

    fun defaultConfig(action: Action<ProductFlavor>)

    /** Build types used by this project.  */
    val buildTypes: NamedDomainObjectContainer<BuildType>

    fun buildTypes(action: Action<NamedDomainObjectContainer<BuildType>>)

    /** List of flavor dimensions.  */
    var flavorDimensionList: List<String>

    /** All product flavors used by this project.  */
    val productFlavors: NamedDomainObjectContainer<ProductFlavor>

    fun productFlavors(action: Action<NamedDomainObjectContainer<ProductFlavor>>)

    /** Signing configs used by this project.  */
    val signingConfigs: NamedDomainObjectContainer<SigningConfig>

    fun signingConfigs(action: Action<NamedDomainObjectContainer<SigningConfig>>)

    /** Name of the variant to publish.  */
    var defaultPublishConfig: String

    /** Filter to determine which variants to build.  */
    fun variantFilter(action: Action<VariantFilter>)

    /**
     * a Callback object to register callbacks for variants
     */
    val variants: VariantCallbacks
}
