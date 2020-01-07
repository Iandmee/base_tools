
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
package com.android.build.api.variant.impl
import com.android.build.api.variant.ActionableVariantObject
import com.android.build.api.variant.BuildTypedVariantFilterBuilder
import com.android.build.api.variant.VariantConfiguration
import com.android.build.gradle.internal.api.dsl.DslScope
import org.gradle.api.Action
import javax.inject.Inject

internal open class BuildTypedVariantFilterBuilderImpl<T> @Inject constructor(
    private val dslScope: DslScope,
    private val operations: VariantOperations<T>,
    private val buildType: String,
    private val flavorToDimensionList: List<Pair<String, String>> = listOf(),
    private val type: Class<T>
): BuildTypedVariantFilterBuilder<T> where T : ActionableVariantObject, T: VariantConfiguration {
    override fun withFlavor(flavorToDimension: Pair<String, String>, action: Action<T>) {
        operations.addFilteredAction(FilteredVariantOperation(
            specificType = type,
            buildType = buildType,
            flavorToDimensionData = flavorToDimensionList + listOf(flavorToDimension),
            action = action
        ))
    }

    override fun withFlavor(flavorToDimension: Pair<String, String>, action: T.() -> Unit) {
        withFlavor(flavorToDimension, Action { action(it) })
    }

    override fun withFlavor(flavorToDimension: Pair<String, String>): BuildTypedVariantFilterBuilder<T> {
        @Suppress("UNCHECKED_CAST")
        return dslScope.objectFactory.newInstance(
            BuildTypedVariantFilterBuilderImpl::class.java,
            dslScope,
            operations,
            buildType,
            flavorToDimensionList + listOf(flavorToDimension),
            type
        ) as BuildTypedVariantFilterBuilder<T>
    }
}
