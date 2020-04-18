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

package com.android.build.gradle.integration.library

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp
import com.android.build.gradle.integration.common.fixture.app.TestSourceFile
import com.android.build.gradle.integration.common.utils.getBuildType
import com.android.build.gradle.integration.common.utils.getProductFlavor
import com.android.builder.model.AndroidProject
import com.android.builder.model.ClassField
import com.google.common.base.Charsets
import com.google.common.collect.Maps
import com.google.common.io.Files
import org.junit.Assert
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.BeforeClass
import org.junit.ClassRule
import org.junit.Test
import java.io.File
import java.io.IOException

/**
 * Test for BuildConfig field declared in build type, flavors, and variant and how they override
 * each other.
 *
 * Forked from [com.android.build.gradle.integration.application.BuildConfigTest].
 */
class LibraryBuildConfigTest {

    companion object {
        @JvmStatic
        @get:ClassRule
        val project: GradleTestProject =
            GradleTestProject.builder().fromTestApp(HelloWorldApp.noBuildFile().also {
                it.addFile(
                    TestSourceFile(
                        "build.gradle",
                        """
                            apply plugin: 'com.android.library'

                            android {
                              compileSdkVersion ${GradleTestProject.DEFAULT_COMPILE_SDK_VERSION}

                              defaultConfig {
                                buildConfigField "int", "VALUE_DEFAULT", "1"
                                buildConfigField "int", "VALUE_DEBUG",   "1"
                                buildConfigField "int", "VALUE_FLAVOR",  "1"
                                buildConfigField "int", "VALUE_VARIANT", "1"
                              }

                              buildTypes {
                                debug {
                                  buildConfigField "int", "VALUE_DEBUG",   "100"
                                  buildConfigField "int", "VALUE_VARIANT", "100"
                                }
                              }

                              flavorDimensions 'foo'
                              productFlavors {
                                flavor1 {
                                  buildConfigField "int", "VALUE_DEBUG",   "10"
                                  buildConfigField "int", "VALUE_FLAVOR",  "10"
                                  buildConfigField "int", "VALUE_VARIANT", "10"
                                }
                                flavor2 {
                                  buildConfigField "int", "VALUE_DEBUG",   "20"
                                  buildConfigField "int", "VALUE_FLAVOR",  "20"
                                  buildConfigField "int", "VALUE_VARIANT", "20"
                                }
                              }

                              libraryVariants.all { variant ->
                                if (variant.buildType.name == "debug") {
                                  variant.buildConfigField "int", "VALUE_VARIANT", "1000"
                                }
                              }
                            }
                            """.trimIndent()
                    )
                )
            }).create()

        private lateinit var model: AndroidProject

        @JvmStatic
        @BeforeClass
        fun setUp() {
            model = project.executeAndReturnModel(
                "clean",
                "generateFlavor1DebugBuildConfig",
                "generateFlavor1ReleaseBuildConfig",
                "generateFlavor2DebugBuildConfig",
                "generateFlavor2ReleaseBuildConfig"
            ).onlyModel
        }
    }


    @Test
    @Throws(IOException::class)
    fun testFlavor1Debug() {
        val expected = """
            /**
             * Automatically generated file. DO NOT MODIFY
             */
            package com.example.helloworld;

            public final class BuildConfig {
              public static final boolean DEBUG = Boolean.parseBoolean("true");
              public static final String LIBRARY_PACKAGE_NAME = "com.example.helloworld";
              public static final String BUILD_TYPE = "debug";
              public static final String FLAVOR = "flavor1";
              // Fields from the variant
              public static final int VALUE_VARIANT = 1000;
              // Fields from build type: debug
              public static final int VALUE_DEBUG = 100;
              // Fields from product flavor: flavor1
              public static final int VALUE_FLAVOR = 10;
              // Fields from default config.
              public static final int VALUE_DEFAULT = 1;
            }

            """.trimIndent()
        doCheckBuildConfig(expected, "flavor1/debug")
    }

    @Test
    fun modelDefaultConfig() {
        val map = Maps.newHashMap<String, String>()
        map["VALUE_DEFAULT"] = "1"
        map["VALUE_FLAVOR"] = "1"
        map["VALUE_DEBUG"] = "1"
        map["VALUE_VARIANT"] = "1"
        checkMaps(map, model.defaultConfig.productFlavor.buildConfigFields, "defaultConfig")
    }

    @Test
    fun modelFlavor1() {
        val map = Maps.newHashMap<String, String>()
        map["VALUE_FLAVOR"] = "10"
        map["VALUE_DEBUG"] = "10"
        map["VALUE_VARIANT"] = "10"
        checkFlavor(model, "flavor1", map)
    }

    @Test
    @Throws(IOException::class)
    fun buildFlavor2Debug() {
        val expected = ("""
            /**
             * Automatically generated file. DO NOT MODIFY
             */
            package com.example.helloworld;

            public final class BuildConfig {
              public static final boolean DEBUG = Boolean.parseBoolean("true");
              public static final String LIBRARY_PACKAGE_NAME = "com.example.helloworld";
              public static final String BUILD_TYPE = "debug";
              public static final String FLAVOR = "flavor2";
              // Fields from the variant
              public static final int VALUE_VARIANT = 1000;
              // Fields from build type: debug
              public static final int VALUE_DEBUG = 100;
              // Fields from product flavor: flavor2
              public static final int VALUE_FLAVOR = 20;
              // Fields from default config.
              public static final int VALUE_DEFAULT = 1;
            }

            """).trimIndent()
        doCheckBuildConfig(expected, "flavor2/debug")
    }

    @Test
    fun modelFlavor2() {
        val map = Maps.newHashMap<String, String>()
        map["VALUE_FLAVOR"] = "20"
        map["VALUE_DEBUG"] = "20"
        map["VALUE_VARIANT"] = "20"
        checkFlavor(model, "flavor2", map)
    }

    @Test
    fun buildFlavor1Release() {
        val expected = """
            /**
             * Automatically generated file. DO NOT MODIFY
             */
            package com.example.helloworld;

            public final class BuildConfig {
              public static final boolean DEBUG = false;
              public static final String LIBRARY_PACKAGE_NAME = "com.example.helloworld";
              public static final String BUILD_TYPE = "release";
              public static final String FLAVOR = "flavor1";
              // Fields from product flavor: flavor1
              public static final int VALUE_DEBUG = 10;
              public static final int VALUE_FLAVOR = 10;
              public static final int VALUE_VARIANT = 10;
              // Fields from default config.
              public static final int VALUE_DEFAULT = 1;
            }

            """.trimIndent()
        doCheckBuildConfig(expected, "flavor1/release")
    }

    @Test
    fun modelDebug() {
        val map = Maps.newHashMap<String, String>()
        map["VALUE_DEBUG"] = "100"
        map["VALUE_VARIANT"] = "100"
        checkBuildType(model, "debug", map)
    }

    @Test
    @Throws(IOException::class)
    fun buildFlavor2Release() {
        val expected = """
            /**
             * Automatically generated file. DO NOT MODIFY
             */
            package com.example.helloworld;

            public final class BuildConfig {
              public static final boolean DEBUG = false;
              public static final String LIBRARY_PACKAGE_NAME = "com.example.helloworld";
              public static final String BUILD_TYPE = "release";
              public static final String FLAVOR = "flavor2";
              // Fields from product flavor: flavor2
              public static final int VALUE_DEBUG = 20;
              public static final int VALUE_FLAVOR = 20;
              public static final int VALUE_VARIANT = 20;
              // Fields from default config.
              public static final int VALUE_DEFAULT = 1;
            }

            """.trimIndent()
        doCheckBuildConfig(expected, "flavor2/release")
    }

    @Test
    fun modelRelease() {
        checkBuildType(model, "release", emptyMap())
    }

    private fun doCheckBuildConfig(expected: String, variantDir: String) {
        val outputFile = File(
            project.testDir,
            "build/generated/source/buildConfig/"
                    + variantDir
                    + "/com/example/helloworld/BuildConfig.java"
        )
        Assert.assertTrue("Missing file: $outputFile", outputFile.isFile)
        assertEquals(
            expected,
            Files.asByteSource(outputFile).asCharSource(Charsets.UTF_8).read()
        )
    }

    private fun checkFlavor(
        androidProject: AndroidProject,
        flavorName: String,
        valueMap: Map<String, String>?
    ) {
        val productFlavor = androidProject.getProductFlavor(flavorName).productFlavor
        assertNotNull("$flavorName variant null-check", productFlavor)

        checkMaps(valueMap, productFlavor.buildConfigFields, flavorName)
    }

    private fun checkBuildType(
        androidProject: AndroidProject,
        buildTypeName: String,
        valueMap: Map<String, String>?
    ) {
        val buildType = androidProject.getBuildType(buildTypeName).buildType
        assertNotNull("$buildTypeName flavor null-check", buildType)
        checkMaps(valueMap, buildType.buildConfigFields, buildTypeName)
    }

    private fun checkMaps(
        valueMap: Map<String, String>?,
        value: Map<String, ClassField>?,
        name: String
    ) {
        assertNotNull(value)

        // check the map against the expected one.
        assertEquals(valueMap!!.keys, value!!.keys)
        for (key in valueMap.keys) {
            val field = value[key]
            assertNotNull("$name: expected field $key", field)
            assertEquals(
                "$name: check Value of $key", valueMap[key], field!!.value
            )
        }
    }
}
