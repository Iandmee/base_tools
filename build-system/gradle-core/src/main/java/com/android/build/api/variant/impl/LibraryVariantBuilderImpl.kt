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

import com.android.build.api.component.analytics.AnalyticsEnabledLibraryVariantBuilder
import com.android.build.api.dsl.LibraryExtension
import com.android.build.api.variant.ComponentIdentity
import com.android.build.api.variant.LibraryVariantBuilder
import com.android.build.api.variant.VariantBuilder
import com.android.build.gradle.internal.core.VariantDslInfo
import com.android.build.gradle.internal.services.ProjectServices
import com.android.build.gradle.internal.services.VariantApiServices
import com.google.wireless.android.sdk.stats.GradleBuildVariant
import javax.inject.Inject

open class LibraryVariantBuilderImpl @Inject constructor(
    variantDslInfo: VariantDslInfo<LibraryExtension>,
    variantConfiguration: ComponentIdentity,
    variantApiServices: VariantApiServices
) : VariantBuilderImpl(
    variantDslInfo,
    variantConfiguration,
    variantApiServices
), LibraryVariantBuilder {

    override var androidTestEnabled: Boolean
        get() = enableAndroidTest
        set(value) {
            enableAndroidTest = value
        }

    override var enableAndroidTest: Boolean = true

    override var enableTestFixtures: Boolean = variantDslInfo.testFixtures.enable

    override fun <T : VariantBuilder> createUserVisibleVariantObject(
            projectServices: ProjectServices,
            stats: GradleBuildVariant.Builder?
    ): T =
        if (stats == null) {
            this as T
        } else {
            projectServices.objectFactory.newInstance(
                AnalyticsEnabledLibraryVariantBuilder::class.java,
                this,
                stats
            ) as T
        }
}
