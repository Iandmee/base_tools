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

import java.nio.file.Paths
import kotlin.io.path.pathString

private const val JDWP_OPTIONS = "-agentlib:jdwp=transport=dt_socket,server=y,suspend=y"
private const val CLASSPATH = "-classpath"
private const val MAIN = "MainKt"
private val JAVA = Paths.get(System.getProperty("java.home"), "bin", "java").pathString

/** An [Engine] that launches and attaches to a java process. */
internal class JvmEngine : AttachingEngine("jvm") {

  override fun buildCommandLine(mainClass: String): List<String> {
    return listOf(JAVA, JDWP_OPTIONS, CLASSPATH, Resources.TEST_CLASSES_JAR, MAIN, mainClass)
  }
}
