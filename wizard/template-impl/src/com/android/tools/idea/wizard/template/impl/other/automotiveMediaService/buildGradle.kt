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

package com.android.tools.idea.wizard.template.impl.other.automotiveMediaService

import com.android.tools.idea.wizard.template.getMaterialComponentName
import com.android.tools.idea.wizard.template.renderIf

fun buildGradle(
  buildApiString: String?,
  generateKotlin: Boolean,
  kotlinVersion: String,
  minApi: Int,
  targetApi: Int,
  useAndroidX: Boolean
): String {
  val kotlinDependenciesBlock = renderIf(generateKotlin) {"""
    implementation "org.jetbrains.kotlin:kotlin-stdlib:$kotlinVersion"
  """}

  return """
apply plugin: "com.android.library"
${renderIf(generateKotlin) {"""
apply plugin: "kotlin-android"
apply plugin: "kotlin-android-extensions"
"""}}

android {
    compileSdkVersion ${buildApiString?.toIntOrNull() ?: "\"$buildApiString\""}
    
    defaultConfig {
        minSdkVersion $minApi
        targetSdkVersion $targetApi
        versionCode 1
        versionName "1.0"

        testInstrumentationRunner "${getMaterialComponentName("android.support.test.runner.AndroidJUnitRunner", useAndroidX)}"
    }
}

dependencies {
$kotlinDependenciesBlock
}"""
}
