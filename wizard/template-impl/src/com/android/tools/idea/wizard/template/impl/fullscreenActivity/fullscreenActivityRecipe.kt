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


import com.android.tools.idea.wizard.template.Language
import com.android.tools.idea.wizard.template.ModuleTemplateData
import com.android.tools.idea.wizard.template.RecipeExecutor
import com.android.tools.idea.wizard.template.activityToLayout
import com.android.tools.idea.wizard.template.getMaterialComponentName
import com.android.tools.idea.wizard.template.impl.common.addAllKotlinDependencies
import com.android.tools.idea.wizard.template.impl.common.generateThemeStyles
import com.android.tools.idea.wizard.template.impl.fullscreenActivity.androidManifestXml
import com.android.tools.idea.wizard.template.impl.fullscreenActivity.res.layout.activityFullscreenXml
import com.android.tools.idea.wizard.template.impl.fullscreenActivity.res.values.fullscreenAttrs
import com.android.tools.idea.wizard.template.impl.fullscreenActivity.res.values.fullscreenColors
import com.android.tools.idea.wizard.template.impl.fullscreenActivity.res.values.fullscreenStyles
import com.android.tools.idea.wizard.template.impl.fullscreenActivity.res.values.stringsXml
import com.android.tools.idea.wizard.template.impl.fullscreenActivity.src.app_package.fullscreenActivityJava
import com.android.tools.idea.wizard.template.impl.fullscreenActivity.src.app_package.fullscreenActivityKt

fun RecipeExecutor.fullscreenActivityRecipe(
  moduleData: ModuleTemplateData,
  activityClass: String,
  isLauncher: Boolean,
  layoutName: String,
  packageName: String
) {

  val (projectData, srcOut, resOut, manifestOut) = moduleData
  val apis = moduleData.apis
  val buildApi = apis.buildApi!!
  val useAndroidX = moduleData.projectTemplateData.androidXSupport
  val useMaterial2 = useAndroidX || hasDependency("com.google.android.material:material")
  val ktOrJavaExt = projectData.language.extension
  addAllKotlinDependencies(moduleData)

  addDependency("com.android.support:support-v4:${buildApi}.+")

  val simpleName = activityToLayout(activityClass)
  val superClassFqcn = getMaterialComponentName("android.support.v7.app.AppCompatActivity", useAndroidX)
  mergeXml(androidManifestXml(activityClass, packageName, simpleName, isLauncher, moduleData.isLibrary, moduleData.isNew),
           manifestOut.resolve("AndroidManifest.xml"))

  val finalResOut = moduleData.baseFeature?.dir?.resolve("src/debug/res") ?: resOut
  generateThemeStyles(moduleData.themesData.main, moduleData.isDynamic, useMaterial2, finalResOut, finalResOut)

  mergeXml(fullscreenColors(), finalResOut.resolve("values/colors.xml"))
  mergeXml(fullscreenAttrs(), finalResOut.resolve("values/attrs.xml"))
  mergeXml(fullscreenStyles(moduleData.themesData.main.name), finalResOut.resolve("values/styles.xml"))

  save(activityFullscreenXml(activityClass, packageName), resOut.resolve("layout/${layoutName}.xml"))
  mergeXml(stringsXml(activityClass, moduleData.isNew, simpleName), finalResOut.resolve("values/strings.xml"))

  val actionBarClassFqcn = getMaterialComponentName("android.support.v7.app.ActionBar", useAndroidX)
  val fullscreenActivity = when (projectData.language) {
    Language.Java -> fullscreenActivityJava(
      actionBarClassFqcn, activityClass, projectData.applicationPackage, layoutName, packageName, superClassFqcn)
    Language.Kotlin -> fullscreenActivityKt(activityClass, projectData.applicationPackage, layoutName, packageName, superClassFqcn)
  }
  save(fullscreenActivity, srcOut.resolve("${activityClass}.${ktOrJavaExt}"))

  open(srcOut.resolve("${activityClass}.${ktOrJavaExt}"))
  open(finalResOut.resolve("layout/${layoutName}.xml"))
}
