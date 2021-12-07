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

package com.android.build.api.variant.impl

import com.android.build.api.dsl.PackagingOptions
import com.android.build.api.variant.ResourcesPackaging
import com.android.build.gradle.internal.packaging.defaultExcludes
import com.android.build.gradle.internal.packaging.defaultMerges
import com.android.build.gradle.internal.services.VariantServices

open class ResourcesPackagingImpl(
    private val dslPackagingOptions: PackagingOptions,
    variantServices: VariantServices
) : ResourcesPackaging {

    override val excludes =
        variantServices.setPropertyOf(String::class.java) { getBaseExcludes() }

    override val pickFirsts =
        variantServices.setPropertyOf(String::class.java) {
            dslPackagingOptions.pickFirsts.union(dslPackagingOptions.resources.pickFirsts)
        }

    override val merges =
        variantServices.setPropertyOf(String::class.java) {
            // the union of dslPackagingOptions.merges and dslPackagingOptions.resources.merges,
            // minus the default patterns removed from either of them.
            dslPackagingOptions.merges
                .union(dslPackagingOptions.resources.merges)
                .minus(
                    defaultMerges.subtract(dslPackagingOptions.merges)
                        .union(defaultMerges.subtract(dslPackagingOptions.resources.merges))
                )
        }

    // the union of dslPackagingOptions.excludes and dslPackagingOptions.resources.excludes, minus
    // the default patterns removed from either of them.
    protected fun getBaseExcludes(): Set<String> =
        dslPackagingOptions.excludes
            .union(dslPackagingOptions.resources.excludes)
            .minus(
                defaultExcludes.subtract(dslPackagingOptions.excludes)
                    .union(defaultExcludes.subtract(dslPackagingOptions.resources.excludes))
            )
}
