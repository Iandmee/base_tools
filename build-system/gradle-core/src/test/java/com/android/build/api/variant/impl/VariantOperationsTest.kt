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

import com.android.build.api.component.impl.FilteredComponentAction
import com.android.build.api.variant.ApplicationVariant
import com.android.build.api.variant.ApplicationVariantProperties
import com.android.build.api.variant.LibraryVariant
import com.android.build.api.variant.Variant
import com.google.common.truth.Truth
import org.gradle.api.Action
import org.junit.Test
import org.mockito.Mockito
import java.lang.RuntimeException
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.pow
import kotlin.test.fail

/**
 * Tests for [VariantOperations]
 */
class VariantOperationsTest {

    @Test
    fun unfilteredActionsTest() {
        val counter = AtomicInteger(0)
        val operations = VariantOperations<ApplicationVariant<ApplicationVariantProperties>>()
        for (i in 1..5) {
            operations.addAction(Action { counter.getAndAdd(10.0.pow(i - 1).toInt())})
        }
        @Suppress("UNCHECKED_CAST")
        val variant = createVariant(ApplicationVariant::class.java) as ApplicationVariant<ApplicationVariantProperties>

        operations.executeActions(variant)
        Truth.assertThat(counter.get()).isEqualTo(11111)
    }

    @Test
    fun singleFilteredActionTest() {
        val counter = AtomicInteger(0)
        val operations = VariantOperations<ApplicationVariant<ApplicationVariantProperties>>()
        @Suppress("UNCHECKED_CAST")
        operations.addFilteredAction(
            FilteredComponentAction(
                specificType = ApplicationVariant::class.java,
                action = Action { counter.incrementAndGet() }) as FilteredComponentAction<ApplicationVariant<ApplicationVariantProperties>>
        )

        @Suppress("UNCHECKED_CAST")
        val variant = createVariant(ApplicationVariant::class.java) as ApplicationVariant<ApplicationVariantProperties>
        operations.executeActions(variant)
        Truth.assertThat(counter.get()).isEqualTo(1)
    }

    @Test
    fun multipleActionsTest() {
        val counter = AtomicInteger(0)
        val operations = VariantOperations<Variant<*>>()

        operations.addAction(Action { counter.incrementAndGet()})

        operations.addFilteredAction(
            FilteredComponentAction(
                specificType = LibraryVariant::class.java,
                action = Action { counter.getAndAdd(10) }
            )
        )

        operations.addFilteredAction(
            FilteredComponentAction(
                specificType = ApplicationVariant::class.java,
                action = Action { counter.getAndAdd(100) }
            )
        )

        val variant = createVariant(ApplicationVariant::class.java)
        operations.executeActions(variant)

        Truth.assertThat(counter.get()).isEqualTo(101)
    }

    @Test
    fun actionsOrderTest() {
        val orderList = mutableListOf<Int>()
        val operations = VariantOperations<Variant<*>>()

        operations.addFilteredAction(
            FilteredComponentAction(
                specificType = ApplicationVariant::class.java,
                action = Action { orderList.add(1) }
            )
        )
        operations.addAction(Action { orderList.add(2) })

        operations.addFilteredAction(
            FilteredComponentAction(
                specificType = ApplicationVariant::class.java,
                action = Action { orderList.add(3) }
            )
        )

        val variant = createVariant(ApplicationVariant::class.java)
        operations.executeActions(variant)

        Truth.assertThat(orderList).isEqualTo(listOf(1, 2, 3))
    }

    @Test
    fun actionInvokedTooLate() {
        val operations = VariantOperations<Variant<*>>()

        operations.executeActions(Mockito.mock(Variant::class.java))
        try {
            operations.addAction( Action { variant -> println(variant.name) })
            fail("Expected exception not raised")
        } catch(e: RuntimeException) {
            Truth.assertThat(e.message).contains("It is too late to add actions")
        }
    }

    private fun <T : Variant<*>> createVariant(
        @Suppress("UNCHECKED_CAST") variantClass: Class<T> = Variant::class.java as Class<T>
    ): T {
        val variant = Mockito.mock(variantClass)
        return variant
    }
}