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
package com.android.build.api.component.impl

import com.android.build.gradle.internal.component.ApkCreationConfig
import com.android.build.gradle.internal.core.VariantDslInfo
import com.android.build.gradle.internal.scope.GlobalScope
import com.android.build.gradle.options.ProjectOptions
import com.android.builder.model.CodeShrinker

abstract class AbstractApkTestCreationConfigImpl(
    override val config: ApkCreationConfig,
    projectOptions: ProjectOptions,
    globalScope: GlobalScope,
    variantDslInfo: VariantDslInfo

) : ApkCreationConfigImpl(config, projectOptions, globalScope, variantDslInfo) {
    override fun getCodeShrinker(): CodeShrinker? {
        config.testedConfig?.apply {
            if (variantType.isAar) return null
        }
        return super.getCodeShrinker()
    }
}
