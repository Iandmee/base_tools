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
import com.android.build.api.variant.FlavoredVariantFilterBuilder
import com.android.build.api.variant.GenericVariantFilterBuilder
import com.android.build.api.variant.TypedVariantFilterBuilder
import com.android.build.api.variant.VariantConfiguration
import com.android.build.gradle.internal.api.dsl.DslScope
import org.gradle.api.Action
import java.util.regex.Pattern
import javax.inject.Inject

internal open class GenericVariantFilterBuilderImpl<T> @Inject constructor(
    private val dslScope: DslScope,
    private val operations: VariantOperations<T>,
    private val type: Class<T>
): GenericVariantFilterBuilder<T>  where T: ActionableVariantObject, T: VariantConfiguration {

    override fun withBuildType(buildType: String, action: Action<T>) {
        operations.addFilteredAction(
            FilteredVariantOperation(
                specificType = type,
                buildType = buildType,
                action = action
            )
        )
    }

    override fun withBuildType(buildType: String, action: T.() -> Unit) {
        operations.addFilteredAction(
            FilteredVariantOperation(
                specificType = type,
                buildType = buildType,
                action = Action { action(it) }
            )
        )
    }

    override fun <U : T> withType(subType: Class<U>): TypedVariantFilterBuilder<U> {
        @Suppress("UNCHECKED_CAST")
        return dslScope.objectFactory.newInstance(
            TypedVariantFilterBuilderImpl::class.java,
            dslScope,
            operations,
            subType
        ) as TypedVariantFilterBuilder<U>
    }

    override fun withBuildType(buildType: String): BuildTypedVariantFilterBuilder<T> {
        @Suppress("UNCHECKED_CAST")
        return dslScope.objectFactory.newInstance(
            BuildTypedVariantFilterBuilderImpl::class.java,
            dslScope,
            operations,
            buildType,
            listOf<Pair<String, String>>(),
            type
        ) as BuildTypedVariantFilterBuilder<T>
    }

    override fun withFlavor(flavorToDimension: Pair<String, String>): FlavoredVariantFilterBuilder<T> {
        @Suppress("UNCHECKED_CAST")
        return dslScope.objectFactory.newInstance(
            FlavoredVariantQueryFilterImpl::class.java,
            dslScope,
            operations,
            listOf(flavorToDimension),
            type
        ) as FlavoredVariantFilterBuilder<T>
    }

    override fun withFlavor(flavorToDimension: Pair<String, String>, action: T.() -> Unit) {
        operations.addFilteredAction(
            FilteredVariantOperation(
                specificType = type,
                flavorToDimensionData = listOf(flavorToDimension),
                action = Action { action(it) }
            )
        )
    }

    override fun withFlavor(flavorToDimension: Pair<String, String>, action: Action<T>) {
        operations.addFilteredAction(
            FilteredVariantOperation(
                specificType = type,
                flavorToDimensionData = listOf(flavorToDimension),
                action = action
            )
        )
    }

    override fun withName(pattern: Pattern, action: Action<T>) {
        operations.addFilteredAction(
            FilteredVariantOperation(
                specificType = type,
                variantNamePattern = pattern,
                action = action
            ))
    }

    override fun withName(name: String, action: Action<T>) {
        operations.addFilteredAction(
            FilteredVariantOperation(
                specificType = type,
                variantName = name,
                action = action
            ))
    }

    override fun withName(name: String, action: (T) -> Unit) {
        operations.addFilteredAction(
            FilteredVariantOperation(
                specificType = type,
                variantName = name,
                action = Action<T> { action(it) }
            )
        )
    }
}
