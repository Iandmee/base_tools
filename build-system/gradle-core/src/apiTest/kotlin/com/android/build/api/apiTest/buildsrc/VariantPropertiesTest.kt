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
package com.android.build.api.apiTest.buildsrc

import com.android.build.api.apiTest.VariantApiBaseTest
import com.google.common.truth.Truth
import org.junit.Test
import kotlin.test.assertNotNull

/**
 * Tests related to accessing [com.android.build.api.variant.VariantProperties]
 */
class VariantPropertiesTest: VariantApiBaseTest(
    TestType.BuildSrc
) {
    @Test
    fun setVersionsFromTask() {
        given {
            tasksToInvoke.add("verifierForRelease")
            addBuildSrc {
                testingElements.addManifestVerifierTask(this)
                addSource(
                    "src/main/kotlin/VersionCodeTask.kt",
                    // language=kotlin
                    """
                    import org.gradle.api.DefaultTask
                    import org.gradle.api.file.RegularFileProperty
                    import org.gradle.api.tasks.OutputFile
                    import org.gradle.api.tasks.TaskAction

                    abstract class VersionCodeTask : DefaultTask() {

                        @get:OutputFile
                        abstract val outputFile: RegularFileProperty

                        @TaskAction
                        fun action() {
                            outputFile.get().asFile.writeText("1234")
                        }
                    }
                    """.trimIndent()
                )
                addSource(
                    "src/main/kotlin/VersionNameTask.kt",
                    // language=kotlin
                    """
                    import org.gradle.api.DefaultTask
                    import org.gradle.api.file.RegularFileProperty
                    import org.gradle.api.tasks.OutputFile
                    import org.gradle.api.tasks.TaskAction

                    abstract class VersionNameTask : DefaultTask() {

                        @get:OutputFile
                        abstract val outputFile: RegularFileProperty

                        @TaskAction
                        fun action() {
                            outputFile.get().asFile.writeText("versionName from task")
                        }
                    }
                    """.trimIndent()
                )
                addSource(
                    "src/main/kotlin/CustomPlugin.kt",
                    // language=kotlin
                    """
                    import com.android.build.api.artifact.Artifacts
                    import com.android.build.api.artifact.ArtifactType
                    import com.android.build.api.dsl.ApplicationExtension
                    import com.android.build.api.variant.VariantOutputConfiguration.OutputType
                    import com.android.build.gradle.AppPlugin
                    import org.gradle.api.Plugin
                    import org.gradle.api.Project

                    class CustomPlugin: Plugin<Project> {
                        override fun apply(project: Project) {
                            project.plugins.withType(AppPlugin::class.java) {
                                // NOTE: BaseAppModuleExtension is internal. This will be replaced by a public
                                // interface
                                val extension = project.extensions.getByName("android") as ApplicationExtension<*, *, *, *, *>
                                extension.configure(project)
                            }
                        }
                    }

                    fun ApplicationExtension<*, *, *, *, *>.configure(project: Project) {
                        // Note: Everything in there is incubating.

                        // onVariantProperties registers an action that configures variant properties during
                        // variant computation (which happens during afterEvaluate)
                        onVariantProperties {
                            // applies to all variants. This excludes test components (unit test and androidTest)
                        }

                        // use filter to apply onVariantProperties to a subset of the variants
                        onVariantProperties.withBuildType("release") {
                            // Because app module can have multiple output when using mutli-APK, versionCode/Name
                            // are only available on the variant output.
                            // Here gather the output when we are in single mode (ie no multi-apk)
                            val mainOutput = this.outputs.single { it.outputType == OutputType.SINGLE }

                            // create version Code generating task
                            val versionCodeTask = project.tasks.register("computeVersionCodeFor${'$'}{name}", VersionCodeTask::class.java) {
                                it.outputFile.set(project.layout.buildDirectory.file("versionCode.txt"))
                            }

                            // wire version code from the task output
                            // map will create a lazy Provider that
                            // 1. runs just before the consumer(s), ensuring that the producer (VersionCodeTask) has run
                            //    and therefore the file is created.
                            // 2. contains task dependency information so that the consumer(s) run after the producer.
                            mainOutput.versionCode.set(versionCodeTask.map { it.outputFile.get().asFile.readText().toInt() })

                            // same for version Name
                            val versionNameTask = project.tasks.register("computeVersionNameFor${'$'}{name}", VersionNameTask::class.java) {
                                it.outputFile.set(project.layout.buildDirectory.file("versionName.txt"))
                            }
                            mainOutput.versionName.set(versionNameTask.map { it.outputFile.get().asFile.readText() })

                            // finally add the verifier task that will check that the merged manifest
                            // does contain the version code and version name from the tasks added 
                            // above.
                            project.tasks.register("verifierFor${'$'}{name}", VerifyManifestTask::class.java) {
                                it.apkFolder.set(artifacts.get(ArtifactType.APK))
                                it.builtArtifactsLoader.set(artifacts.getBuiltArtifactsLoader())
                            }
                        }
                    }
                    """.trimIndent()
                )
                buildFile =
                    """
                dependencies {
                    implementation("com.android.tools.build:gradle:$agpVersion")
                }
                """.trimIndent()
            }
            addModule(":app") {
                buildFile = """
                plugins {
                        id("com.android.application")
                        kotlin("android")
                        kotlin("android.extensions")
                }

                apply<CustomPlugin>()

                android { ${testingElements.addCommonAndroidBuildLogic()}
                }
                """.trimIndent()
                testingElements.addManifest(this)
            }
        }
        check {
            assertNotNull(this)
            Truth.assertThat(output).contains("BUILD SUCCESSFUL")
        }
    }
}