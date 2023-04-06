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
package com.android.tools.debuggertests

import com.intellij.util.io.isFile
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.pathString
import kotlin.io.path.reader
import kotlin.io.path.writer
import kotlin.streams.asSequence

private const val MODULE_PATH = "tools/base/debugger-tests"
private const val RESOURCE_PATH = "$MODULE_PATH/resources"
private const val TEST_CLASSES_JAR = "bazel-bin/$MODULE_PATH/test-classes-binary_deploy.jar"
private const val TEST_CLASSES_JAR_PROPERTY = "test-classes-jar"
private const val TEST_CLASSES_DEX_PROPERTY = "test-classes-dex"
private const val TEST_CLASSES_DEX = "bazel-bin/$MODULE_PATH/test-classes-dex.jar"
private const val TOOLS_ADT = "tools/adt/idea"
private const val RES = "res"
private const val GOLDEN = "golden"
private const val SRC = "src"
private const val TESTS = "tests"
private const val BAZEL_PWD = "BUILD_WORKSPACE_DIRECTORY"
private const val USER_DIR = "user.dir"

/** Utilities for fetching resources */
internal object Resources {

  /** Find all tested classes. */
  fun findTestClasses(): List<String> {
    return Files.walk(Paths.get(getRepoResourceDir(), SRC, TESTS))
      .asSequence()
      .filter(Path::isFile)
      .map(Path::toClassName)
      .toList()
  }

  /** Read expected result from golden file */
  fun readGolden(test: String, dir: String): String {
    val path = Paths.get(getRepoResourceDir(), RES, GOLDEN, dir, getGoldenFileName(test))
    return path.reader().use { it.readText() }
  }

  /** Write expected result to golden file */
  fun writeGolden(test: String, actual: String, dir: String) {
    val fileName = getGoldenFileName(test)
    val path = Paths.get(getWorkspaceDir(), RESOURCE_PATH, RES, GOLDEN, dir, fileName)
    path.parent.toFile().mkdirs()
    path.writer().use { it.write(actual) }
  }

  fun getTestClassesJarPath() = getPathFromProperty(TEST_CLASSES_JAR_PROPERTY, TEST_CLASSES_JAR)

  fun getTestClassesDexPath() = getPathFromProperty(TEST_CLASSES_DEX_PROPERTY, TEST_CLASSES_DEX)

  private fun getPathFromProperty(property: String, filename: String): String {
    val jarPath = System.getProperty(property)
    if (jarPath != null) {
      return jarPath
    }
    val path = Paths.get(getWorkspaceDir(), filename)
    if (path.isFile()) {
      return path.pathString
    }
    throw IllegalStateException(
      """
        $filename not found. Please run
          'bazel build //tools/base/debugger-tests:test-deps'
      """
    )
  }
}

private fun getWorkspaceDir(): String =
  System.getenv(BAZEL_PWD) ?: System.getProperty(USER_DIR).removeSuffix(TOOLS_ADT)

// Only works when running locally. Used for writing golden files
private fun getRepoResourceDir(): String {
  val path = Paths.get(System.getProperty(USER_DIR).removeSuffix(TOOLS_ADT))
  return Paths.get(path.pathString, RESOURCE_PATH).pathString
}

private fun getGoldenFileName(test: String) = "${test.replace(".", File.separator)}.txt"

private fun Path.toClassName() =
  pathString
    .substringAfterLast("src${File.separator}")
    .replace(File.separator, ".")
    .removeSuffix(".kt")
