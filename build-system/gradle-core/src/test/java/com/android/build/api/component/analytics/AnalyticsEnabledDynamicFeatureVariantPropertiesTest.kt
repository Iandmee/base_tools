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

package com.android.build.api.component.analytics

import com.android.build.api.variant.AaptOptions
import com.android.build.api.variant.ApkPackagingOptions
import com.android.build.api.variant.DynamicFeatureVariantProperties
import com.android.build.api.variant.JniLibsPackagingOptions
import com.android.build.api.variant.ResourcesPackagingOptions
import com.android.build.gradle.internal.fixtures.FakeObjectFactory
import com.android.tools.build.gradle.internal.profile.VariantPropertiesMethodType
import com.google.common.truth.Truth
import com.google.wireless.android.sdk.stats.GradleBuildVariant
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.MockitoAnnotations

class AnalyticsEnabledDynamicFeatureVariantPropertiesTest {
    @Mock
    lateinit var delegate: DynamicFeatureVariantProperties

    private val stats = GradleBuildVariant.newBuilder()
    private lateinit var proxy: AnalyticsEnabledDynamicFeatureVariantProperties

    @Before
    fun setup() {
        MockitoAnnotations.initMocks(this)
        proxy = AnalyticsEnabledDynamicFeatureVariantProperties(delegate, stats, FakeObjectFactory.factory)
    }

    @Test
    fun getAaptOptions() {
        val aaptOptions = Mockito.mock(AaptOptions::class.java)
        Mockito.`when`(delegate.aaptOptions).thenReturn(aaptOptions)
        Truth.assertThat(proxy.aaptOptions).isEqualTo(aaptOptions)

        Truth.assertThat(stats.variantApiAccess.variantPropertiesAccessCount).isEqualTo(1)
        Truth.assertThat(
            stats.variantApiAccess.variantPropertiesAccessList.first().type
        ).isEqualTo(VariantPropertiesMethodType.AAPT_OPTIONS_VALUE)
        Mockito.verify(delegate, Mockito.times(1))
            .aaptOptions
    }

    @Test
    fun aaptOptionsAction() {
        val function = { param : AaptOptions -> println(param) }
        proxy.aaptOptions(function)

        Truth.assertThat(stats.variantApiAccess.variantPropertiesAccessCount).isEqualTo(1)
        Truth.assertThat(
            stats.variantApiAccess.variantPropertiesAccessList.first().type
        ).isEqualTo(VariantPropertiesMethodType.AAPT_OPTIONS_ACTION_VALUE)
        Mockito.verify(delegate, Mockito.times(1))
            .aaptOptions(function)
    }

    @Test
    fun getPackagingOptions() {
        val packagingOptions = Mockito.mock(ApkPackagingOptions::class.java)
        val jniLibsPackagingOptions = Mockito.mock(JniLibsPackagingOptions::class.java)
        val resourcesPackagingOptions = Mockito.mock(ResourcesPackagingOptions::class.java)
        Mockito.`when`(packagingOptions.jniLibs).thenReturn(jniLibsPackagingOptions)
        Mockito.`when`(packagingOptions.resources).thenReturn(resourcesPackagingOptions)
        Mockito.`when`(delegate.packagingOptions).thenReturn(packagingOptions)
        // simulate a user configuring packaging options for jniLibs and resources
        proxy.packagingOptions.jniLibs
        proxy.packagingOptions.resources

        Truth.assertThat(stats.variantApiAccess.variantPropertiesAccessCount).isEqualTo(4)
        Truth.assertThat(
            stats.variantApiAccess.variantPropertiesAccessList.map { it.type }
        ).containsExactlyElementsIn(
            listOf(
                VariantPropertiesMethodType.PACKAGING_OPTIONS_VALUE,
                VariantPropertiesMethodType.JNI_LIBS_PACKAGING_OPTIONS_VALUE,
                VariantPropertiesMethodType.PACKAGING_OPTIONS_VALUE,
                VariantPropertiesMethodType.RESOURCES_PACKAGING_OPTIONS_VALUE
            )
        )
        Mockito.verify(delegate, Mockito.times(1)).packagingOptions
    }

    @Test
    fun packagingOptionsActions() {
        val packagingOptions = Mockito.mock(ApkPackagingOptions::class.java)
        val jniLibsPackagingOptions = Mockito.mock(JniLibsPackagingOptions::class.java)
        val resourcesPackagingOptions = Mockito.mock(ResourcesPackagingOptions::class.java)
        Mockito.`when`(packagingOptions.jniLibs).thenReturn(jniLibsPackagingOptions)
        Mockito.`when`(packagingOptions.resources).thenReturn(resourcesPackagingOptions)
        Mockito.`when`(delegate.packagingOptions).thenReturn(packagingOptions)
        val action: ApkPackagingOptions.() -> Unit = {
            this.jniLibs {}
            this.resources {}
        }
        proxy.packagingOptions(action)

        Truth.assertThat(stats.variantApiAccess.variantPropertiesAccessCount).isEqualTo(3)
        Truth.assertThat(
            stats.variantApiAccess.variantPropertiesAccessList.map { it.type }
        ).containsExactlyElementsIn(
            listOf(
                VariantPropertiesMethodType.PACKAGING_OPTIONS_ACTION_VALUE,
                VariantPropertiesMethodType.JNI_LIBS_PACKAGING_OPTIONS_ACTION_VALUE,
                VariantPropertiesMethodType.RESOURCES_PACKAGING_OPTIONS_ACTION_VALUE
            )
        )
        Mockito.verify(delegate, Mockito.times(1)).packagingOptions
    }
}
