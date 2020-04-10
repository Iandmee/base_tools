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

package com.android.build.api.apiTest

import com.android.SdkConstants
import com.android.testutils.AbstractBuildGivenBuildCheckTest
import com.android.testutils.TestUtils
import com.google.common.truth.Truth
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import org.junit.rules.TestName
import java.io.File
import java.io.FileReader
import java.util.Properties

/**
 * Base test class for Variant API related tasks. These tasks setup a Gradle project and execute
 * some tasks on those and finally verifies the build behavior or output.
 *
 * @param testType the test type.
 * @param scriptingLanguage the language used to express the build logic.
 */
open class VariantApiBaseTest(
    private val testType: TestType,
    protected val scriptingLanguage: ScriptingLanguage = ScriptingLanguage.Kotlin
) :
    AbstractBuildGivenBuildCheckTest<VariantApiBaseTest.GivenBuilder, BuildResult>() {

    companion object {
        /**
         * If running within a build system, make sure to use the expected version when generating
         * build.gradle.kts files.
         */
        val kotlinVersion: String by lazy {
            System.getenv("KOTLIN_PLUGIN")?.split(':')?.last()
            // fall back, use the version I am running against.
                ?: KotlinVersion.CURRENT.toString()
        }

        /**
         * List of custom repositories where all projects dependencies can be satisfied.
         */
        val mavenRepos: List<String> by lazy {
            System.getenv("CUSTOM_REPO").split(File.pathSeparatorChar)
        }

    }

    /**
     * Type of test.
     */
    enum class TestType {
        /**
         * In [Script] tests, all build logic is expressed in build file scripts.
         */
        Script {
            override fun getDirName(test: VariantApiBaseTest): String {
                return test.scriptingLanguage.name
            }
        },

        /**
         * In [BuildSrc] tests, build customization is done through a buildSrc plugin.
         */
        BuildSrc,

        /**
         * In [Plugin] tests, build customization is done through a binary plugin dependency.
         */
        Plugin;

        open fun getDirName(test: VariantApiBaseTest): String = name
    }

    /**
     * Supported scripting languages for expressing build logic, right now, Kotlin and Groovy.
     *
     * @param buildFileName name of the module build file for the language.
     * @param settingsFileName name of the settings file name for the language.
     */
    enum class ScriptingLanguage(val buildFileName: String, val settingsFileName: String) {

        /**
         * Kotlin scripting languages,
         */
        Kotlin("build.gradle.kts", "settings.gradle.kts") {
            override fun configureBuildFile(repositories: List<String>) =
"""
buildscript {
    ${addRepositories(repositories).prependIndent("\t")}
    dependencies {
        classpath("com.android.tools.build:gradle:${com.android.Version.ANDROID_GRADLE_PLUGIN_VERSION}")
        classpath(kotlin("gradle-plugin", version = "$kotlinVersion"))
    }
}
allprojects {
    ${addRepositories(repositories).prependIndent("\t")}
}

"""

            override fun configureBuildSrcBuildFile(repositories: List<String>) =
"""
plugins {
    kotlin("jvm") version "$kotlinVersion"
}
${addRepositories(repositories)}
"""

            private fun addRepositories(repositories: List<String>) =
"""
repositories {
    ${repositories.joinToString(
                separator = "\")\n\tmaven(\"",
                prefix = "maven(\"",
                postfix = "\")"
                )}
}
"""

        },
        Groovy("build.gradle", "settings.gradle") {
            override fun configureBuildFile(repositories: List<String>) =
"""
buildscript {
    ${addRepositories(repositories).prependIndent("\t")}
    dependencies {
        classpath("com.android.tools.build:gradle:${com.android.Version.ANDROID_GRADLE_PLUGIN_VERSION}")
    }
}
allprojects {
    ${addRepositories(repositories).prependIndent("\t")}
}
${addRepositories(repositories)}
"""

            override fun configureBuildSrcBuildFile(repositories: List<String>) =
"""
${addRepositories(repositories)}
"""


            private fun addRepositories(repositories: List<String>) =
"""
repositories {
    ${repositories.joinToString(
                    separator = "\'}\n\tmaven { url \'",
                    prefix = "maven { url \'",
                    postfix = "\'}"
                )}
}
"""
        };

        /**
         * Configure a module build file for the [scriptingLanguage].
         *
         * By default the AGP plugin will be automatically added, depending on the scripting
         * language, other plugins like the kotlin gradle plugin when using kotlin for instance.
         *
         * @param repositories the list of repositories where project dependencies will be obtained
         * from
         */
        abstract fun configureBuildFile(repositories: List<String>): String

        /**
         * Configure the buildSrc/ build file for the [scriptingLanguage]
         *
         * @param repositories the list of repositories where project dependencies will be obtained
         * from
         */
        abstract fun configureBuildSrcBuildFile(repositories: List<String>): String
    }

    @Rule
    @JvmField val testName= TestName()

    @Rule
    @JvmField val testProjectDir = if (System.getenv("KEEP_TEST_DIR") != null) {
        ApiTestFolder(File(System.getenv("KEEP_TEST_DIR")), testType.getDirName(this))
    } else {
        TemporaryFolder()
    }

    @Rule
    @JvmField val testBuildDir = TemporaryFolder()

    open fun sdkLocation(): String = TestUtils.getSdk().absolutePath

    open class ModuleGivenBuilder(val scriptingLanguage: ScriptingLanguage) {

        /**
         * Manifest file for the module, optional.
         */
        var manifest: String? = null

        /**
         * Build file for the module, must be provided by the time
         * [AbstractBuildGivenBuildCheckTest.when] is called.
         */
        var buildFile: String? = null

        private val sourceFiles = mutableListOf<Pair<String, String>>()

        /**
         * Add a source file to the current module
         *
         * @param path the relative path from the module root folder to store the source file in.
         * @param content the source file content.
         */
        fun addSource(path: String, content: String) {
            sourceFiles.add(Pair(path, content))
        }

        internal open fun writeModule(folder: File) {
            Truth.assertThat(buildFile != null)
            addBuildFile(folder)

            if (manifest != null) {
                File(folder, "src/main").apply {
                    mkdirs()
                    File(
                        this,
                        SdkConstants.ANDROID_MANIFEST_XML
                    ).writeText(manifest!!.trimIndent())
                }
            }

            sourceFiles.forEach {
                File(folder, it.first).apply {
                    parentFile.mkdirs()
                    writeText(it.second)
                }
            }

        }
        internal open fun addBuildFile(folder: File) {
            File(folder, scriptingLanguage.buildFileName).apply {
                writeText(scriptingLanguage.configureBuildFile(mavenRepos) + (buildFile ?: ""))
            }
        }
    }

    open class GivenBuilder(scriptingLanguage: ScriptingLanguage)
        : ModuleGivenBuilder(scriptingLanguage) {

        private val modules = mutableListOf<Pair<String, GivenBuilder>>()

        /**
         * the list of tasks' names to invoke on the project, by default, we only invoke
         * the assembleDebug task.
         */
        internal val tasksToInvoke = mutableListOf<String>()

        private fun modulesPath(): List<String> = modules.filter { it.first != "buildSrc" }.map { it.first }

        /**
         * Add a new module to the current module and open a new block to configure it.
         *
         * @param path the Gradle relative path for the module, must start with ':'
         * @param action the configuration block for the module.
         */
        fun addModule(path: String, action: GivenBuilder.() -> Unit) {
            if (path == "buildSrc") throw RuntimeException("Use addBuildSrc() for buildSrc module")
            object: GivenBuilder(scriptingLanguage) {
                override fun addBuildFile(folder: File) {
                    if (buildFile != null) {
                        File(folder, scriptingLanguage.buildFileName).apply {
                            writeText(buildFile!!)
                        }
                    }
                }
            }.also {
                modules.add(Pair(path, it))
                action.invoke(it)
            }
        }

        /**
         * Add a buildSrc module to the current module.
         *
         * @param action the configuration block for the buildSrc module.
         */
        fun addBuildSrc(action: GivenBuilder.() -> Unit) {
            object: GivenBuilder(scriptingLanguage) {
                override fun addBuildFile(folder: File) {
                    File(folder, scriptingLanguage.buildFileName).apply {
                        writeText(scriptingLanguage.configureBuildSrcBuildFile(mavenRepos).trimIndent() + "\n" + (buildFile ?: ""))
                    }
                }
            }.also {
                modules.add(Pair("buildSrc", it))
                action.invoke(it)
            }
        }

        fun writeModule(folder: File, sdkLocation: String) {
            val settingsFile = File(folder, scriptingLanguage.settingsFileName)
            val localProperties = File(folder, "local.properties")

            val includeList = modulesPath().joinToString(
                separator = "\")\ninclude(\"",
                prefix = "include(\"",
                postfix = "\")\n"
            )

            settingsFile.writeText(
                """
            $includeList
            rootProject.name = "${javaClass.simpleName}"
            """.trimIndent())

            localProperties.writeText("sdk.dir=$sdkLocation")

            super.writeModule(folder)

            modules.forEach {
                File(folder, it.first.replace(':', File.separatorChar)).apply {
                    mkdirs()
                    it.second.writeModule(this)
                }
            }
        }

        override fun addBuildFile(folder: File) {
            File(folder, scriptingLanguage.buildFileName).apply {
                writeText(scriptingLanguage.configureBuildFile(mavenRepos) + (buildFile ?: ""))
            }
        }
    }

    open class DocumentationBuilder {
        var index: String? = null
    }

    var docs=  DocumentationBuilder()

    fun withDocs(docs: DocumentationBuilder.()-> Unit) {
        docs.invoke(this.docs)
    }

    override fun defaultWhen(given: GivenBuilder): BuildResult? {

        val projectDir = File(testProjectDir.root, testName.methodName)
        projectDir.deleteRecursively()
        projectDir.mkdirs()
        given.writeModule(projectDir, sdkLocation())
        docs.index?.apply {
            File(projectDir, "readme.md").writeText(this)
        }

        if (given.tasksToInvoke.isEmpty()) {
            given.tasksToInvoke.add("assembleDebug")
        }
        val gradleRunner = GradleRunner.create()
            .withProjectDir(projectDir)
            .withPluginClasspath()
            .withArguments("-Dandroid.enableJvmResourceCompiler=true", *given.tasksToInvoke.toTypedArray())
            .forwardOutput()

        // if running within Intellij, we must set the Gradle Distribution when invoking the
        // GradleRunner, so fetch it from our workspace and use the same URL we use to build
        // tools/base projects.
        if (System.getenv("SRC")!=null) {
            val srcDir = System.getenv("SRC")
            FileReader(File("$srcDir/tools/gradle/wrapper/gradle-wrapper.properties")).apply {
                val gradleWrapperProperties = Properties()
                    .also { properties -> properties.load(this) }
                val relativePathDistribution = gradleWrapperProperties["distributionUrl"]
                gradleRunner.withGradleDistribution(
                    File("$srcDir/tools/gradle/wrapper/$relativePathDistribution").toURI())
            }
        }
        return gradleRunner.build()
    }

    override fun instantiateGiven(): GivenBuilder = GivenBuilder(scriptingLanguage)
}