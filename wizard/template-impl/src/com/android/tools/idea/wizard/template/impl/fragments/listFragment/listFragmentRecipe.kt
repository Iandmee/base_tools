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

package com.android.tools.idea.wizard.template.impl.fragments.listFragment

import com.android.tools.idea.wizard.template.Language
import com.android.tools.idea.wizard.template.ModuleTemplateData
import com.android.tools.idea.wizard.template.RecipeExecutor
import com.android.tools.idea.wizard.template.impl.activities.common.addAllKotlinDependencies
import com.android.tools.idea.wizard.template.impl.activities.common.src.app_package.dummy.dummyContentJava
import com.android.tools.idea.wizard.template.impl.activities.common.src.app_package.dummy.dummyContentKt
import com.android.tools.idea.wizard.template.impl.fragments.listFragment.res.layout.fragmentListXml
import com.android.tools.idea.wizard.template.impl.fragments.listFragment.res.layout.itemListContentXml
import com.android.tools.idea.wizard.template.impl.fragments.listFragment.res.values.dimensXml
import com.android.tools.idea.wizard.template.impl.fragments.listFragment.src.app_package.listFragmentJava
import com.android.tools.idea.wizard.template.impl.fragments.listFragment.src.app_package.listFragmentKt
import com.android.tools.idea.wizard.template.impl.fragments.listFragment.src.app_package.recyclerViewAdapterJava
import com.android.tools.idea.wizard.template.impl.fragments.listFragment.src.app_package.recyclerViewAdapterKt

fun RecipeExecutor.listFragmentRecipe(
  moduleData: ModuleTemplateData,
  packageName: String,
  fragmentClass: String,
  columnCount: ColumnCount,
  fragmentLayout: String,
  fragmentLayoutList: String,
  adapterClassName: String
) {
  val (projectData, srcOut, resOut, _) = moduleData
  val buildApi = moduleData.apis.buildApi
  val useAndroidX = moduleData.projectTemplateData.androidXSupport
  val ktOrJavaExt = projectData.language.extension
  val applicationPackage = projectData.applicationPackage
  addAllKotlinDependencies(moduleData)

  addDependency("com.android.support:support-v4:${buildApi}.+")
  addDependency("com.android.support:recyclerview-v7:${buildApi}.+")

  save(fragmentListXml(fragmentClass, fragmentLayout, packageName, useAndroidX), resOut.resolve("layout/${fragmentLayoutList}.xml"))
  save(itemListContentXml(), resOut.resolve("layout/${fragmentLayout}.xml"))

  val columnCountNumber = columnCount.ordinal + 1
  val listFragment = when (projectData.language) {
    Language.Java -> listFragmentJava(adapterClassName, applicationPackage, columnCountNumber, fragmentClass, fragmentLayoutList, packageName,
                                      useAndroidX)
    Language.Kotlin -> listFragmentKt(adapterClassName, applicationPackage, columnCountNumber, fragmentClass, fragmentLayoutList, packageName,
                                      useAndroidX)
  }
  save(listFragment, srcOut.resolve("${fragmentClass}.${ktOrJavaExt}"))

  val recyclerViewAdapter = when (projectData.language) {
    Language.Java -> recyclerViewAdapterJava(adapterClassName, fragmentLayout, packageName, useAndroidX)
    Language.Kotlin -> recyclerViewAdapterKt(adapterClassName, applicationPackage, fragmentLayout, packageName, useAndroidX)
  }
  save(recyclerViewAdapter, srcOut.resolve("${adapterClassName}.${ktOrJavaExt}"))

  val dummyContent = when (projectData.language) {
    Language.Java -> dummyContentJava(packageName)
    Language.Kotlin -> dummyContentKt(packageName)
  }
  save(dummyContent, srcOut.resolve("dummy/DummyContent.${ktOrJavaExt}"))

  open(srcOut.resolve("${fragmentClass}.${ktOrJavaExt}"))
  open(resOut.resolve("layout/${fragmentLayoutList}.xml"))

  mergeXml(dimensXml(), resOut.resolve("values/dimens.xml"))
}
