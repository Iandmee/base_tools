/*
 * Copyright (C) 2018 The Android Open Source Project
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
@file:JvmName("GradleModelConverterUtil")

package com.android.ide.common.gradle.model

import com.android.ide.common.util.PathString
import com.android.ide.common.util.toPathString
import com.android.projectmodel.DynamicResourceValue
import com.android.projectmodel.ExternalLibrary
import com.android.projectmodel.RecursiveResourceFolder
import com.android.projectmodel.ResourceFolder
import com.android.resources.ResourceType
import com.google.common.collect.ImmutableMap

// This file contains utilities for converting Gradle model types (from builder-model) into project model types.

class GradleModelConverter(val project: IdeAndroidProject) {

  /**
   * Converts the given [IdeJavaLibrary] into a [ExternalLibrary]. Returns null if the given library is a module dependency.
   */
  fun convert(library: IdeJavaLibrary): ExternalLibrary = convertLibrary(library)

  /**
   * Converts the given [IdeAndroidLibrary] into a [ExternalLibrary]. Returns null if the given library is a module dependency.
   */
  fun convert(library: IdeAndroidLibrary): ExternalLibrary = convertLibrary(library)
}

fun classFieldsToDynamicResourceValues(classFields: Map<String, IdeClassField>): Map<String, DynamicResourceValue> {
  val result = HashMap<String, DynamicResourceValue>()
  for (field in classFields.values) {
    val resourceType = ResourceType.fromClassName(field.type)
    if (resourceType != null) {
      result[field.name] = DynamicResourceValue(resourceType, field.value)
    }
  }
  return ImmutableMap.copyOf(result)
}

/**
 * Converts a builder-model [IdeJavaLibrary] into a [ExternalLibrary]. Returns null
 * if the input is invalid.
 */
fun convertLibrary(source: IdeJavaLibrary): ExternalLibrary = JavaExternalLibraryWrapper(source)

/**
 * Converts a builder-model [IdeAndroidLibrary] into a [ExternalLibrary]. Returns null
 * if the input is invalid.
 */
fun convertLibrary(source: IdeAndroidLibrary): ExternalLibrary = AndroidExternalLibraryWrapper(source)

private abstract class ExternalLibraryWrapper<T: IdeLibrary>(protected val lib: T) : ExternalLibrary {
  @Suppress("FileComparisons")
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false
    return lib.artifact == (other as? ExternalLibraryWrapper<*>)?.lib?.artifact
  }

  override fun hashCode(): Int {
    return lib.artifact.hashCode()
  }
}

private class JavaExternalLibraryWrapper(source: IdeJavaLibrary) : ExternalLibraryWrapper<IdeJavaLibrary>(source) {
  override val address: String get() = lib.artifactAddress
  override val location: PathString? get() = null
  override val manifestFile: PathString? get() = null
  override val packageName: String? get() = null
  override val classJars: List<PathString> get() = listOf(lib.artifact.toPathString())
  override val dependencyJars: List<PathString> get() = emptyList()
  override val resFolder: ResourceFolder? get() = null
  override val symbolFile: PathString? get() = null
  override val resApkFile: PathString? get() = null
  override val hasResources: Boolean get() = false
}

private class AndroidExternalLibraryWrapper(source: IdeAndroidLibrary) : ExternalLibraryWrapper<IdeAndroidLibrary>(source) {
  override val address: String get() = lib.artifactAddress
  override val location: PathString? get() = lib.artifact.toPathString()
  override val manifestFile: PathString? get() = PathString(lib.manifest)
  override val packageName: String? get() = null
  override val classJars: List<PathString> get() = listOf(PathString(lib.jarFile))
  override val dependencyJars: List<PathString> get() = lib.localJars.map(::PathString)
  override val resFolder: ResourceFolder? get() = RecursiveResourceFolder(PathString(lib.resFolder))
  override val symbolFile: PathString? get() = PathString(lib.symbolFile)
  override val resApkFile: PathString? get() = lib.resStaticLibrary?.let(::PathString)

  // NOTE: The intended implementation is resApkFile != null || resFolder != null, but resFolder is currently always not null.
  override val hasResources: Boolean get() = true
}
