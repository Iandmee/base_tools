/*
 * Copyright (C) 2012 The Android Open Source Project
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
package com.android.build.gradle.internal.dsl

import com.android.build.gradle.internal.api.dsl.DslScope
import com.android.builder.core.AbstractProductFlavor
import com.android.builder.core.BuilderConstants
import com.android.builder.core.DefaultApiVersion
import com.android.builder.internal.ClassFieldImpl
import com.android.builder.model.ApiVersion
import com.android.builder.model.ProductFlavor
import com.google.common.base.Strings
import org.gradle.api.Action

/** Base DSL object used to configure product flavors.  */
abstract class BaseFlavor(name: String, private val dslScope: DslScope) :
    AbstractProductFlavor(
        name,
        dslScope.objectFactory.newInstance(
            VectorDrawablesOptions::class.java
        )
    ), CoreProductFlavor {

    /** Encapsulates per-variant configurations for the NDK, such as ABI filters.  */
    val ndk: NdkOptions = dslScope.objectFactory.newInstance(NdkOptions::class.java)

    override val ndkConfig: CoreNdkOptions
        get() {
            return ndk
        }

    /**
     * Encapsulates per-variant CMake and ndk-build configurations for your external native build.
     *
     * To learn more, see
     * [Add C and C++ Code to Your Project](http://developer.android.com/studio/projects/add-native-code.html#).
     */

    val externalNativeBuild: ExternalNativeBuildOptions =
        dslScope.objectFactory.newInstance(
            ExternalNativeBuildOptions::class.java,
            dslScope.objectFactory
        )

    override val externalNativeBuildOptions: CoreExternalNativeBuildOptions
        get() {
            return this.externalNativeBuild
        }

    fun setMinSdkVersion(minSdkVersion: Int) {
        setMinSdkVersion(DefaultApiVersion(minSdkVersion))
    }

    /**
     * Sets minimum SDK version.
     *
     * See [uses-sdk element documentation](http://developer.android.com/guide/topics/manifest/uses-sdk-element.html).
     */
    open fun minSdkVersion(minSdkVersion: Int) {
        setMinSdkVersion(minSdkVersion)
    }

    open fun setMinSdkVersion(minSdkVersion: String?) {
        setMinSdkVersion(getApiVersion(minSdkVersion))
    }

    /**
     * Sets minimum SDK version.
     *
     * See [uses-sdk element documentation](http://developer.android.com/guide/topics/manifest/uses-sdk-element.html).
     */
    open fun minSdkVersion(minSdkVersion: String?) {
        setMinSdkVersion(minSdkVersion)
    }

    fun setTargetSdkVersion(targetSdkVersion: Int): ProductFlavor {
        setTargetSdkVersion(DefaultApiVersion(targetSdkVersion))
        return this
    }

    /**
     * Sets the target SDK version to the given value.
     *
     * See [
 * uses-sdk element documentation](http://developer.android.com/guide/topics/manifest/uses-sdk-element.html).
     */
    open fun targetSdkVersion(targetSdkVersion: Int) {
        setTargetSdkVersion(targetSdkVersion)
    }

    fun setTargetSdkVersion(targetSdkVersion: String?) {
        setTargetSdkVersion(getApiVersion(targetSdkVersion))
    }

    /**
     * Sets the target SDK version to the given value.
     *
     * See [
 * uses-sdk element documentation](http://developer.android.com/guide/topics/manifest/uses-sdk-element.html).
     */
    open fun targetSdkVersion(targetSdkVersion: String?) {
        setTargetSdkVersion(targetSdkVersion)
    }

    /**
     * Sets the maximum SDK version to the given value.
     *
     * See [
 * uses-sdk element documentation](http://developer.android.com/guide/topics/manifest/uses-sdk-element.html).
     */
    open fun maxSdkVersion(maxSdkVersion: Int) {
        setMaxSdkVersion(maxSdkVersion)
    }

    /**
     * Adds a custom argument to the test instrumentation runner, e.g:
     *
     * `testInstrumentationRunnerArgument "size", "medium"`
     *
     * Test runner arguments can also be specified from the command line
     *
     * ```
     * ./gradlew connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.size=medium
     * ./gradlew connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.foo=bar
     * ```
     */
    fun testInstrumentationRunnerArgument(key: String, value: String) {
        testInstrumentationRunnerArguments[key] = value
    }

    /**
     * Adds custom arguments to the test instrumentation runner, e.g:
     *
     * `testInstrumentationRunnerArguments(size: "medium", foo: "bar")`

     * Test runner arguments can also be specified from the command line:
     *
     * ```
     * ./gradlew connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.size=medium
     * ./gradlew connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.foo=bar
     * ```
     */
    open fun testInstrumentationRunnerArguments(args: Map<String, String>) {
        testInstrumentationRunnerArguments.putAll(args)
    }

    /** Signing config used by this product flavor.  */
    override var signingConfig: SigningConfig?
        get() = super.signingConfig as SigningConfig?
        set(value) { super.setSigningConfig(value) }

    // -- DSL Methods. TODO remove once the instantiator does what I expect it to do.
    /**
     * Adds a new field to the generated BuildConfig class.
     *
     * The field is generated as: `<type> <name> = <value>;`
     *
     * This means each of these must have valid Java content. If the type is a String, then the
     * value should include quotes.
     *
     * @param type the type of the field
     * @param name the name of the field
     * @param value the value of the field
     */
    fun buildConfigField(
        type: String,
        name: String,
        value: String
    ) {
        val alreadyPresent = buildConfigFields[name]
        if (alreadyPresent != null) {
            val flavorName = getName()
            if (BuilderConstants.MAIN == flavorName) {
                dslScope.logger
                    .info(
                        "DefaultConfig: buildConfigField '{}' value is being replaced: {} -> {}",
                        name,
                        alreadyPresent.value,
                        value
                    )
            } else {
                dslScope.logger
                    .info(
                        "ProductFlavor({}): buildConfigField '{}' " +
                                "value is being replaced: {} -> {}",
                        flavorName,
                        name,
                        alreadyPresent.value,
                        value
                    )
            }
        }
        addBuildConfigField(ClassFieldImpl(type, name, value))
    }

    /**
     * Adds a new generated resource.
     *
     * This is equivalent to specifying a resource in res/values.
     *
     * See [Resource
 * Types](http://developer.android.com/guide/topics/resources/available-resources.html).
     *
     * @param type the type of the resource
     * @param name the name of the resource
     * @param value the value of the resource
     */
    fun resValue(type: String, name: String, value: String) {
        val alreadyPresent = resValues[name]
        if (alreadyPresent != null) {
            val flavorName = getName()
            if (BuilderConstants.MAIN == flavorName) {
                dslScope.logger
                    .info(
                        "DefaultConfig: resValue '{}' value is being replaced: {} -> {}",
                        name,
                        alreadyPresent.value,
                        value
                    )
            } else {
                dslScope.logger
                    .info(
                        "ProductFlavor({}): resValue '{}' value is being replaced: {} -> {}",
                        flavorName,
                        name,
                        alreadyPresent.value,
                        value
                    )
            }
        }
        addResValue(ClassFieldImpl(type, name, value))
    }

    /**
     * Specifies a ProGuard configuration file that the plugin should use.
     *
     * There are two ProGuard rules files that ship with the Android plugin and are used by
     * default:
     *
     *  * proguard-android.txt
     *  * proguard-android-optimize.txt
     *
     * `proguard-android-optimize.txt` is identical to `proguard-android.txt
    ` * , exccept with optimizations enabled. You can use `
     * getDefaultProguardFile(String filename)` to return the full path of each file.
     */
    fun proguardFile(proguardFile: Any) {
        proguardFiles.add(dslScope.file(proguardFile))
    }

    /**
     * Specifies ProGuard configuration files that the plugin should use.
     *
     * There are two ProGuard rules files that ship with the Android plugin and are used by
     * default:
     *
     *  * proguard-android.txt
     *  * proguard-android-optimize.txt
     *
     * `proguard-android-optimize.txt` is identical to `proguard-android.txt
    ` * , exccept with optimizations enabled. You can use `
     * getDefaultProguardFile(String filename)` to return the full path of each file.
     */
    fun proguardFiles(vararg files: Any) {
        for (file in files) {
            proguardFile(file)
        }
    }

    /**
     * Sets the ProGuard configuration files.
     *
     * There are two ProGuard rules files that ship with the Android plugin and are used by
     * default:
     *
     *  * proguard-android.txt
     *  * proguard-android-optimize.txt
     *
     * `proguard-android-optimize.txt` is identical to `proguard-android.txt
    ` * , exccept with optimizations enabled. You can use `
     * getDefaultProguardFile(String filename)` to return the full path of the files.
     */
    fun setProguardFiles(proguardFileIterable: Iterable<Any>) {
        proguardFiles.clear()
        for (file in proguardFileIterable) {
            proguardFile(file)
        }
    }

    /**
     * Adds a proguard rule file to be used when processing test code.
     *
     * Test code needs to be processed to apply the same obfuscation as was done to main code.
     */
    fun testProguardFile(proguardFile: Any) {
        testProguardFiles.add(dslScope.file(proguardFile))
    }

    /**
     * Adds proguard rule files to be used when processing test code.
     *
     * Test code needs to be processed to apply the same obfuscation as was done to main code.
     */
    fun testProguardFiles(vararg proguardFiles: Any) {
        for (proguardFile in proguardFiles) {
            testProguardFile(proguardFile)
        }
    }

    /**
     * Specifies proguard rule files to be used when processing test code.
     *
     * Test code needs to be processed to apply the same obfuscation as was done to main code.
     */
    fun setTestProguardFiles(files: Iterable<Any>) {
        testProguardFiles.clear()
        for (proguardFile in files) {
            testProguardFile(proguardFile)
        }
    }

    /**
     * Adds a proguard rule file to be included in the published AAR.
     *
     * This proguard rule file will then be used by any application project that consume the AAR
     * (if proguard is enabled).
     *
     * This allows AAR to specify shrinking or obfuscation exclude rules.
     *
     * This is only valid for Library project. This is ignored in Application project.
     */
    fun consumerProguardFile(proguardFile: Any) {
        consumerProguardFiles.add(dslScope.file(proguardFile))
    }

    /**
     * Adds proguard rule files to be included in the published AAR.
     *
     * This proguard rule file will then be used by any application project that consume the AAR
     * (if proguard is enabled).
     *
     * This allows AAR to specify shrinking or obfuscation exclude rules.
     *
     * This is only valid for Library project. This is ignored in Application project.
     */
    fun consumerProguardFiles(vararg proguardFiles: Any) {
        for (proguardFile in proguardFiles) {
            consumerProguardFile(proguardFile)
        }
    }

    /**
     * Specifies a proguard rule file to be included in the published AAR.
     *
     * This proguard rule file will then be used by any application project that consume the AAR
     * (if proguard is enabled).
     *
     * This allows AAR to specify shrinking or obfuscation exclude rules.
     *
     * This is only valid for Library project. This is ignored in Application project.
     */
    fun setConsumerProguardFiles(proguardFileIterable: Iterable<Any>) {
        consumerProguardFiles.clear()
        for (proguardFile in proguardFileIterable) {
            consumerProguardFile(proguardFile)
        }
    }

    /** Encapsulates per-variant configurations for the NDK, such as ABI filters.  */
    fun ndk(action: Action<NdkOptions>) {
        action.execute(ndk)
    }

    /**
     * Encapsulates per-variant CMake and ndk-build configurations for your external native build.
     *
     * To learn more, see
     * [Add C and C++ Code to Your Project](http://developer.android.com/studio/projects/add-native-code.html#).
     */
    fun externalNativeBuild(action: Action<ExternalNativeBuildOptions>) {
        action.execute(externalNativeBuild)
    }

    fun externalNativeBuild(action: ExternalNativeBuildOptions.() -> Unit) {
        action.invoke(externalNativeBuild)
    }

    /**
     * Specifies a list of
     * [alternative resources](https://d.android.com/guide/topics/resources/providing-resources.html#AlternativeResources)
     * to keep.
     *
     * For example, if you are using a library that includes language resources (such as
     * AppCompat or Google Play Services), then your APK includes all translated language strings
     * for the messages in those libraries whether the rest of your app is translated to the same
     * languages or not. If you'd like to keep only the languages that your app officially supports,
     * you can specify those languages using the `resConfigs` property, as shown in the
     * sample below. Any resources for languages not specified are removed.
     *
     * ````
     * android {
     *     defaultConfig {
     *         ...
     *         // Keeps language resources for only the locales specified below.
     *         resConfigs "en", "fr"
     *     }
     * }
     * ````
     *
     * You can also use this property to filter resources for screen densities. For example,
     * specifying `hdpi` removes all other screen density resources (such as `mdpi`,
     * `xhdpi`, etc) from the final APK.
     *
     * **Note:** `auto` is no longer supported because it created a number of
     * issues with multi-module projects. Instead, you should specify a list of locales that your
     * app supports, as shown in the sample above. Android plugin 3.1.0 and higher ignore the `
     * auto` argument, and Gradle packages all string resources your app and its dependencies
     * provide.
     *
     * To learn more, see
     * [Remove unused alternative resources](https://d.android.com/studio/build/shrink-code.html#unused-alt-resources).
     */
    fun resConfig(config: String) {
        addResourceConfiguration(config)
    }

    /**
     * Specifies a list of
     * [alternative resources](https://d.android.com/guide/topics/resources/providing-resources.html#AlternativeResources)
     * to keep.
     *
     * For example, if you are using a library that includes language resources (such as
     * AppCompat or Google Play Services), then your APK includes all translated language strings
     * for the messages in those libraries whether the rest of your app is translated to the same
     * languages or not. If you'd like to keep only the languages that your app officially supports,
     * you can specify those languages using the `resConfigs` property, as shown in the
     * sample below. Any resources for languages not specified are removed.
     *
     * ````
     * android {
     *     defaultConfig {
     *         ...
     *         // Keeps language resources for only the locales specified below.
     *         resConfigs "en", "fr"
     *     }
     * }
     * ````
     *
     * You can also use this property to filter resources for screen densities. For example,
     * specifying `hdpi` removes all other screen density resources (such as `mdpi`,
     * `xhdpi`, etc) from the final APK.
     *
     * **Note:** `auto` is no longer supported because it created a number of
     * issues with multi-module projects. Instead, you should specify a list of locales that your
     * app supports, as shown in the sample above. Android plugin 3.1.0 and higher ignore the `
     * auto` argument, and Gradle packages all string resources your app and its dependencies
     * provide.
     *
     * To learn more, see
     * [Remove unused alternative resources](https://d.android.com/studio/build/shrink-code.html#unused-alt-resources).
     */
    fun resConfigs(vararg config: String) {
        addResourceConfigurations(*config)
    }

    /**
     * Specifies a list of
     * [alternative resources](https://d.android.com/guide/topics/resources/providing-resources.html#AlternativeResources)
     * to keep.
     *
     * For example, if you are using a library that includes language resources (such as
     * AppCompat or Google Play Services), then your APK includes all translated language strings
     * for the messages in those libraries whether the rest of your app is translated to the same
     * languages or not. If you'd like to keep only the languages that your app officially supports,
     * you can specify those languages using the `resConfigs` property, as shown in the
     * sample below. Any resources for languages not specified are removed.
     *
     * ````
     * android {
     *     defaultConfig {
     *         ...
     *         // Keeps language resources for only the locales specified below.
     *         resConfigs "en", "fr"
     *     }
     * }
     * ````
     *
     * You can also use this property to filter resources for screen densities. For example,
     * specifying `hdpi` removes all other screen density resources (such as `mdpi`,
     * `xhdpi`, etc) from the final APK.
     *
     * **Note:** `auto` is no longer supported because it created a number of
     * issues with multi-module projects. Instead, you should specify a list of locales that your
     * app supports, as shown in the sample above. Android plugin 3.1.0 and higher ignore the `
     * auto` argument, and Gradle packages all string resources your app and its dependencies
     * provide.
     *
     * To learn more, see
     * [Remove unused alternative resources](https://d.android.com/studio/build/shrink-code.html#unused-alt-resources).
     */
    fun resConfigs(config: Collection<String>) {
        addResourceConfigurations(config)
    }

    /** Options for configuration Java compilation.  */
    override val javaCompileOptions: JavaCompileOptions =
        dslScope.objectFactory.newInstance(
            JavaCompileOptions::class.java,
            dslScope.objectFactory,
            dslScope.deprecationReporter
        )

    fun javaCompileOptions(action: Action<JavaCompileOptions>) {
        action.execute(javaCompileOptions)
    }

    /** Options for configuring the shader compiler.  */
    override val shaders: ShaderOptions =
        dslScope.objectFactory.newInstance(ShaderOptions::class.java)

    /** Configure the shader compiler options for this product flavor.  */
    fun shaders(action: Action<ShaderOptions>) {
        action.execute(shaders)
    }

    /**
     * Deprecated equivalent of `vectorDrawablesOptions.generatedDensities`.
     */
    @Deprecated("Replace with vectorDrawablesOptions.generatedDensities")
    var generatedDensities: Set<String>?
        get() = vectorDrawables.generatedDensities
        set(densities) {
            vectorDrawables.setGeneratedDensities(densities)
        }

    fun setGeneratedDensities(generatedDensities: Iterable<String>) {
        vectorDrawables.setGeneratedDensities(generatedDensities)
    }

    /** Configures [VectorDrawablesOptions].  */
    fun vectorDrawables(action: Action<VectorDrawablesOptions>) {
        action.execute(vectorDrawables)
    }

    /** Options to configure the build-time support for `vector` drawables.  */
    override val vectorDrawables: VectorDrawablesOptions
        get() = super.vectorDrawables as VectorDrawablesOptions

    /**
     * Sets whether to enable unbundling mode for embedded wear app.
     *
     * If true, this enables the app to transition from an embedded wear app to one distributed
     * by the play store directly.
     */
    open fun wearAppUnbundled(wearAppUnbundled: Boolean?) {
        this.wearAppUnbundled = wearAppUnbundled
    }

    private fun getApiVersion(value: String?): ApiVersion? {
        return if (!Strings.isNullOrEmpty(value)) {
            if (Character.isDigit(value!![0])) {
                try {
                    val apiLevel = Integer.valueOf(value)
                    DefaultApiVersion(apiLevel)
                } catch (e: NumberFormatException) {
                    throw RuntimeException("'$value' is not a valid API level. ", e)
                }
            } else DefaultApiVersion(value)
        } else null
    }
}
