/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.build.api.component.impl.features

import com.android.build.api.variant.Renderscript
import com.android.build.api.variant.impl.DirectoryEntry
import com.android.build.api.variant.impl.TaskProviderBasedDirectoryEntryImpl
import com.android.build.gradle.internal.component.ConsumableCreationConfig
import com.android.build.gradle.internal.component.features.RenderscriptCreationConfig
import com.android.build.gradle.internal.core.dsl.ConsumableComponentDslInfo
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.services.VariantServices

class RenderscriptCreationConfigImpl(
    private val component: ConsumableCreationConfig,
    private val dslInfo: ConsumableComponentDslInfo,
    private val internalServices: VariantServices,
    override val renderscriptTargetApi: Int
): RenderscriptCreationConfig {

    override val renderscript: Renderscript by lazy {
        internalServices.newInstance(Renderscript::class.java).also {
            it.supportModeEnabled.set(dslInfo.renderscriptSupportModeEnabled)
            it.supportModeBlasEnabled.set(dslInfo.renderscriptSupportModeBlasEnabled)
            it.ndkModeEnabled.set(dslInfo.renderscriptNdkModeEnabled)
            it.optimLevel.set(dslInfo.renderscriptOptimLevel)
        }
    }
    override val dslRenderscriptNdkModeEnabled: Boolean
        get() = dslInfo.renderscriptNdkModeEnabled

    override fun addRenderscriptSources(
        sourceSets: MutableList<DirectoryEntry>,
    ) {
        if (!renderscript.ndkModeEnabled.get()
            && component.artifacts.get(InternalArtifactType.RENDERSCRIPT_SOURCE_OUTPUT_DIR).isPresent
        ) {
            sourceSets.add(
                TaskProviderBasedDirectoryEntryImpl(
                    name = "generated_renderscript",
                    directoryProvider = component.artifacts.get(
                        InternalArtifactType.RENDERSCRIPT_SOURCE_OUTPUT_DIR
                    ),
                )
            )
        }
    }
}
