/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.tools.lint.gradle.api

import java.io.File

interface VariantInputs {
    /** The variant name */
    val name: String

    /** The lint rule jars  */
    val ruleJars: List<File>

    /** The merged manifest of the current module  */
    val mergedManifest: File

    /** The manifest merger report file, if any */
    val manifestMergeReport: File?
}
