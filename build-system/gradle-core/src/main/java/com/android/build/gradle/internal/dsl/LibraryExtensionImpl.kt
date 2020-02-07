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

package com.android.build.gradle.internal.dsl

import com.android.build.api.component.GenericFilteredComponentActionRegistrar
import com.android.build.api.component.impl.GenericFilteredComponentActionRegistrarImpl
import com.android.build.api.dsl.LibraryBuildFeatures
import com.android.build.api.dsl.LibraryExtension
import com.android.build.api.variant.LibraryVariant
import com.android.build.api.variant.LibraryVariantProperties
import com.android.build.gradle.internal.CompileOptions
import com.android.build.gradle.internal.services.DslServices
import com.android.build.gradle.internal.coverage.JacocoOptions
import org.gradle.api.NamedDomainObjectContainer

/** Internal implementation of the 'new' DSL interface */
class LibraryExtensionImpl(
    dslServices: DslServices,
    buildTypes: NamedDomainObjectContainer<BuildType>,
    defaultConfig: DefaultConfig,
    productFlavors: NamedDomainObjectContainer<ProductFlavor>,
    signingConfigs: NamedDomainObjectContainer<SigningConfig>
) :
    CommonExtensionImpl<
            LibraryBuildFeatures,
            BuildType,
            DefaultConfig,
            ProductFlavor,
            SigningConfig,
            LibraryVariant<LibraryVariantProperties>,
            LibraryVariantProperties>(
        dslServices,
        buildTypes,
        defaultConfig,
        productFlavors,
        signingConfigs
    ),
    LibraryExtension<
            AaptOptions,
            AbiSplitOptions,
            AdbOptions,
            BuildType,
            CmakeOptions,
            CompileOptions,
            DataBindingOptions,
            DefaultConfig,
            DensitySplitOptions,
            ExternalNativeBuild,
            JacocoOptions,
            LintOptions,
            NdkBuildOptions,
            ProductFlavor,
            SigningConfig,
            Splits,
            TestOptions,
            TestOptions.UnitTestOptions> {

    override val buildFeatures: LibraryBuildFeatures =
        dslServices.newInstance(LibraryBuildFeaturesImpl::class.java)

    @Suppress("UNCHECKED_CAST")
    override val onVariants: GenericFilteredComponentActionRegistrar<LibraryVariant<LibraryVariantProperties>>
        get() = dslServices.newInstance(
            GenericFilteredComponentActionRegistrarImpl::class.java,
            dslServices,
            variantOperations,
            LibraryVariant::class.java
        ) as GenericFilteredComponentActionRegistrar<LibraryVariant<LibraryVariantProperties>>
    @Suppress("UNCHECKED_CAST")
    override val onVariantProperties: GenericFilteredComponentActionRegistrar<LibraryVariantProperties>
        get() = dslServices.newInstance(
            GenericFilteredComponentActionRegistrarImpl::class.java,
            dslServices,
            variantPropertiesOperations,
            LibraryVariantProperties::class.java
        ) as GenericFilteredComponentActionRegistrar<LibraryVariantProperties>
}