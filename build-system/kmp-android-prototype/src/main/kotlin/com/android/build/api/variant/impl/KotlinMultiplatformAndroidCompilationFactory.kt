/*
 * Copyright (C) 2023 The Android Open Source Project
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

import com.android.utils.appendCapitalized
import org.gradle.api.NamedDomainObjectFactory
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.mpp.external.ExternalKotlinCompilationDescriptor
import org.jetbrains.kotlin.gradle.plugin.mpp.external.createCompilation

class KotlinMultiplatformAndroidCompilationFactory(
    private val target: KotlinMultiplatformAndroidTargetImpl,
    private val kotlinExtension: KotlinMultiplatformExtension
): NamedDomainObjectFactory<KotlinMultiplatformAndroidCompilationImpl> {

    override fun create(name: String): KotlinMultiplatformAndroidCompilationImpl {
        return target.createCompilation {
            compilationName = name
            defaultSourceSet = kotlinExtension.sourceSets.getByName(
                target.targetName.appendCapitalized(name)
            )
            decoratedKotlinCompilationFactory =
                ExternalKotlinCompilationDescriptor.DecoratedKotlinCompilationFactory(
                    ::KotlinMultiplatformAndroidCompilationImpl
                )
            compileTaskName = "compile".appendCapitalized(
                target.targetName.appendCapitalized(name)
            )
        }
    }
}
