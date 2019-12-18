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
import com.android.tools.idea.wizard.template.impl.activities.androidTVActivity.androidManifestXml
import com.android.tools.idea.wizard.template.impl.activities.androidTVActivity.res.layout.activityDetailsXml
import com.android.tools.idea.wizard.template.impl.activities.androidTVActivity.res.layout.activityMainXml
import com.android.tools.idea.wizard.template.impl.activities.androidTVActivity.res.values.colorsXml
import com.android.tools.idea.wizard.template.impl.activities.androidTVActivity.res.values.stringsXml
import com.android.tools.idea.wizard.template.impl.activities.androidTVActivity.src.app_package.browseErrorActivityJava
import com.android.tools.idea.wizard.template.impl.activities.androidTVActivity.src.app_package.browseErrorActivityKt
import com.android.tools.idea.wizard.template.impl.activities.androidTVActivity.src.app_package.cardPresenterJava
import com.android.tools.idea.wizard.template.impl.activities.androidTVActivity.src.app_package.cardPresenterKt
import com.android.tools.idea.wizard.template.impl.activities.androidTVActivity.src.app_package.detailsActivityJava
import com.android.tools.idea.wizard.template.impl.activities.androidTVActivity.src.app_package.detailsActivityKt
import com.android.tools.idea.wizard.template.impl.activities.androidTVActivity.src.app_package.detailsDescriptionPresenterJava
import com.android.tools.idea.wizard.template.impl.activities.androidTVActivity.src.app_package.detailsDescriptionPresenterKt
import com.android.tools.idea.wizard.template.impl.activities.androidTVActivity.src.app_package.errorFragmentJava
import com.android.tools.idea.wizard.template.impl.activities.androidTVActivity.src.app_package.errorFragmentKt
import com.android.tools.idea.wizard.template.impl.activities.androidTVActivity.src.app_package.mainActivityJava
import com.android.tools.idea.wizard.template.impl.activities.androidTVActivity.src.app_package.mainActivityKt
import com.android.tools.idea.wizard.template.impl.activities.androidTVActivity.src.app_package.mainFragmentJava
import com.android.tools.idea.wizard.template.impl.activities.androidTVActivity.src.app_package.mainFragmentKt
import com.android.tools.idea.wizard.template.impl.activities.androidTVActivity.src.app_package.movieJava
import com.android.tools.idea.wizard.template.impl.activities.androidTVActivity.src.app_package.movieKt
import com.android.tools.idea.wizard.template.impl.activities.androidTVActivity.src.app_package.movieListJava
import com.android.tools.idea.wizard.template.impl.activities.androidTVActivity.src.app_package.movieListKt
import com.android.tools.idea.wizard.template.impl.activities.androidTVActivity.src.app_package.playbackActivityJava
import com.android.tools.idea.wizard.template.impl.activities.androidTVActivity.src.app_package.playbackActivityKt
import com.android.tools.idea.wizard.template.impl.activities.androidTVActivity.src.app_package.playbackVideoFragmentJava
import com.android.tools.idea.wizard.template.impl.activities.androidTVActivity.src.app_package.playbackVideoFragmentKt
import com.android.tools.idea.wizard.template.impl.activities.androidTVActivity.src.app_package.videoDetailsFragmentJava
import com.android.tools.idea.wizard.template.impl.activities.androidTVActivity.src.app_package.videoDetailsFragmentKt
import com.android.tools.idea.wizard.template.impl.activities.common.addAllKotlinDependencies
import java.io.File

fun RecipeExecutor.androidTVActivityRecipe(
  moduleData: ModuleTemplateData,
  activityClass: String,
  activityTitle: String,
  layoutName: String,
  mainFragmentClass: String,
  detailsActivityClass: String,
  detailsLayoutName: String,
  detailsFragmentClass: String,
  packageName: String
) {
  val (projectData, srcOut, resOut, manifestOut) = moduleData
  val buildApi = moduleData.apis.buildApi
  val useAndroidX = moduleData.projectTemplateData.androidXSupport
  val ktOrJavaExt = projectData.language.extension
  addAllKotlinDependencies(moduleData)

  addDependency("com.android.support:appcompat-v7:${buildApi}.+")
  addDependency("com.android.support:leanback-v17:+")
  addDependency("com.github.bumptech.glide:glide:3.8.0")

  mergeXml(androidManifestXml(activityClass, detailsActivityClass, moduleData.isLibrary, moduleData.isNew, packageName),
           manifestOut.resolve("AndroidManifest.xml"))
  mergeXml(stringsXml(activityClass, activityTitle, moduleData.isNew), resOut.resolve("values/strings.xml"))
  mergeXml(colorsXml(), resOut.resolve("values/colors.xml"))
  copy(File("app_icon_your_company.png"), resOut.resolve("drawable/app_icon_your_company.png"))
  copy(File("default_background.xml"), resOut.resolve("drawable/default_background.xml"))
  copy(File("movie.png"), resOut.resolve("drawable/movie.png"))
  save(activityMainXml(activityClass, mainFragmentClass, packageName), resOut.resolve("layout/${layoutName}.xml"))
  save(activityDetailsXml(detailsActivityClass, detailsFragmentClass, packageName), resOut.resolve("layout/${detailsLayoutName}.xml"))

  val mainActivity = when (projectData.language) {
    Language.Java -> mainActivityJava(activityClass, layoutName, mainFragmentClass, packageName)
    Language.Kotlin -> mainActivityKt(activityClass, layoutName, mainFragmentClass, packageName)
  }
  save(mainActivity, srcOut.resolve("${activityClass}.${ktOrJavaExt}"))

  val mainFragment = when (projectData.language) {
    Language.Java -> mainFragmentJava(detailsActivityClass, mainFragmentClass, moduleData.apis.minApiLevel, packageName, useAndroidX)
    Language.Kotlin -> mainFragmentKt(detailsActivityClass, mainFragmentClass, moduleData.apis.minApiLevel, packageName, useAndroidX)
  }
  save(mainFragment, srcOut.resolve("${mainFragmentClass}.${ktOrJavaExt}"))

  val detailsActivity = when (projectData.language) {
    Language.Java -> detailsActivityJava(detailsActivityClass, detailsLayoutName, packageName)
    Language.Kotlin -> detailsActivityKt(detailsActivityClass, detailsLayoutName, packageName)
  }
  save(detailsActivity, srcOut.resolve("${detailsActivityClass}.${ktOrJavaExt}"))

  val videoDetailsFragment = when (projectData.language) {
    Language.Java -> videoDetailsFragmentJava(
      activityClass, detailsActivityClass, detailsFragmentClass, moduleData.apis.minApiLevel, packageName, useAndroidX)
    Language.Kotlin -> videoDetailsFragmentKt(
      activityClass, detailsActivityClass, detailsFragmentClass, moduleData.apis.minApiLevel, packageName, useAndroidX)
  }
  save(videoDetailsFragment, srcOut.resolve("${detailsFragmentClass}.${ktOrJavaExt}"))

  val movie = when (projectData.language) {
    Language.Java -> movieJava(packageName)
    Language.Kotlin -> movieKt(packageName)
  }
  save(movie, srcOut.resolve("Movie.${ktOrJavaExt}"))

  val movieList = when (projectData.language) {
    Language.Java -> movieListJava(packageName)
    Language.Kotlin -> movieListKt(packageName)
  }
  save(movieList, srcOut.resolve("MovieList.${ktOrJavaExt}"))

  val cardPresenter = when (projectData.language) {
    Language.Java -> cardPresenterJava(packageName, useAndroidX)
    Language.Kotlin -> cardPresenterKt(packageName, useAndroidX)
  }
  save(cardPresenter, srcOut.resolve("CardPresenter.${ktOrJavaExt}"))

  val detailsDescriptionPresenter = when (projectData.language) {
    Language.Java -> detailsDescriptionPresenterJava(packageName, useAndroidX)
    Language.Kotlin -> detailsDescriptionPresenterKt(packageName, useAndroidX)
  }
  save(detailsDescriptionPresenter, srcOut.resolve("DetailsDescriptionPresenter.${ktOrJavaExt}"))

  val playbackActivity = when (projectData.language) {
    Language.Java -> playbackActivityJava(packageName, useAndroidX)
    Language.Kotlin -> playbackActivityKt(packageName, useAndroidX)
  }
  save(playbackActivity, srcOut.resolve("PlaybackActivity.${ktOrJavaExt}"))

  val playbackVideoFragment = when (projectData.language) {
    Language.Java -> playbackVideoFragmentJava(moduleData.apis.minApiLevel, packageName, useAndroidX)
    Language.Kotlin -> playbackVideoFragmentKt(moduleData.apis.minApiLevel, packageName, useAndroidX)
  }
  save(playbackVideoFragment, srcOut.resolve("PlaybackVideoFragment.${ktOrJavaExt}"))

  val browseErrorActivity = when (projectData.language) {
    Language.Java -> browseErrorActivityJava(layoutName, packageName)
    Language.Kotlin -> browseErrorActivityKt(layoutName, packageName)
  }
  save(browseErrorActivity, srcOut.resolve("BrowseErrorActivity.${ktOrJavaExt}"))

  val errorFragment = when (projectData.language) {
    Language.Java -> errorFragmentJava(moduleData.apis.minApiLevel, packageName, useAndroidX)
    Language.Kotlin -> errorFragmentKt(moduleData.apis.minApiLevel, packageName, useAndroidX)
  }
  save(errorFragment, srcOut.resolve("ErrorFragment.${ktOrJavaExt}"))

  open(srcOut.resolve("${activityClass}.${ktOrJavaExt}"))
  open(resOut.resolve("layout/${layoutName}.xml"))
}
