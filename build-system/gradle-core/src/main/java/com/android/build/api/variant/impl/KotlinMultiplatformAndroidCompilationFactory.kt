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

package com.android.build.api.variant.impl

import com.android.build.api.variant.KotlinMultiplatformAndroidCompilation
import com.android.build.gradle.internal.CompileOptions
import com.android.build.gradle.internal.dsl.KotlinMultiplatformAndroidExtensionImpl
import com.android.build.gradle.internal.plugins.KotlinMultiplatformAndroidPlugin.Companion.androidExtensionOnKotlinExtensionName
import com.android.utils.appendCapitalized
import org.gradle.api.NamedDomainObjectFactory
import org.gradle.api.Project
import org.gradle.api.artifacts.Dependency
import org.jetbrains.kotlin.gradle.ExternalKotlinTargetApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinTargetHierarchy
import org.jetbrains.kotlin.gradle.plugin.mpp.external.ExternalKotlinCompilationDescriptor.*
import org.jetbrains.kotlin.gradle.plugin.mpp.external.createCompilation
import org.jetbrains.kotlin.gradle.plugin.mpp.targetHierarchy.SourceSetTreeClassifier

@OptIn(ExternalKotlinTargetApi::class)
internal class KotlinMultiplatformAndroidCompilationFactory(
    private val project: Project,
    private val target: KotlinMultiplatformAndroidTargetImpl,
    private val kotlinExtension: KotlinMultiplatformExtension,
    private val androidExtension: KotlinMultiplatformAndroidExtensionImpl
): NamedDomainObjectFactory<KotlinMultiplatformAndroidCompilation> {

    @Suppress("INVISIBLE_MEMBER")
    override fun create(name: String): KotlinMultiplatformAndroidCompilationImpl {
        if (KmpPredefinedAndroidCompilation.MAIN.compilationName != name &&
            androidExtension.androidTestOnJvmConfiguration?.compilationName != name &&
            androidExtension.androidTestOnDeviceConfiguration?.compilationName != name) {
            throw IllegalAccessException(
                "Kotlin multiplatform android plugin doesn't support creating arbitrary " +
                        "compilations. Only three types of compilations are supported:\n" +
                        "  * main compilation (named \"${KmpPredefinedAndroidCompilation.MAIN.compilationName}\"),\n" +
                        "  * test on jvm compilation (use `kotlin.$androidExtensionOnKotlinExtensionName.withAndroidTestOnJvm()` to enable),\n" +
                        "  * test on device compilation (use `kotlin.$androidExtensionOnKotlinExtensionName.withAndroidTestOnDevice()` to enable)."
            )
        }

        val isTestComponent = androidExtension.androidTestOnJvmConfiguration?.compilationName == name ||
                androidExtension.androidTestOnDeviceConfiguration?.compilationName == name

        return target.createCompilation<KotlinMultiplatformAndroidCompilationImpl> {
            compilationName = name
            defaultSourceSet = kotlinExtension.sourceSets.getByName(
                target.targetName.appendCapitalized(name)
            )
            compilationFactory =
                CompilationFactory(
                    ::KotlinMultiplatformAndroidCompilationImpl
                )
            compileTaskName = "compile".appendCapitalized(
                target.targetName.appendCapitalized(name)
            )

            if (isTestComponent) {
                compilationAssociator = CompilationAssociator { auxiliary, main ->
                    // When associating a test compilation with a main compilation, we add a
                    // dependency from the configurations of the test components on the main project
                    // later. But we still need to add implementation and compileOnly dependencies
                    // from the main compilation to the test compilation to be consistent with the
                    // behaviour of the other kotlin targets.
                    if (main.compilationName == KmpPredefinedAndroidCompilation.MAIN.compilationName) {
                        auxiliary.compileDependencyConfigurationName.addAllDependenciesFromOtherConfigurations(
                            project,
                            main.implementationConfigurationName,
                            main.compileOnlyConfigurationName
                        )
                    } else {
                        // TODO(b/295485387): kotlin will provide an external API of this in 1.9.20
                        val defaultAssociator = org.jetbrains.kotlin.gradle.plugin.mpp.compilationImpl.DefaultKotlinCompilationAssociator
                        defaultAssociator.associate(
                            target,
                            auxiliary,
                            main
                        )
                    }
                }
            }
            sourceSetTreeClassifier = getSourceSetTreeClassifierFromConfiguration(name)
        }.also {
            it.compilerOptions.options.jvmTarget.set(
                JvmTarget.fromTarget(CompileOptions.DEFAULT_JAVA_VERSION.toString())
            )
        }
    }

    private fun getSourceSetTreeClassifierFromConfiguration(name: String): SourceSetTreeClassifier {
        return when {
            androidExtension.androidTestOnJvmConfiguration?.compilationName == name ->
                androidExtension.androidTestOnJvmConfiguration?.sourceSetTree?.let {
                    SourceSetTreeClassifier.Name(it)
                } ?: SourceSetTreeClassifier.Value(KotlinTargetHierarchy.SourceSetTree.test)
            androidExtension.androidTestOnDeviceConfiguration?.compilationName == name ->
                androidExtension.androidTestOnDeviceConfiguration?.sourceSetTree?.let {
                    SourceSetTreeClassifier.Name(it)
                } ?: SourceSetTreeClassifier.None
            else -> SourceSetTreeClassifier.Default
        }
    }

    private fun String.addAllDependenciesFromOtherConfigurations(
        project: Project,
        vararg configurationNames: String
    ) {
        project.configurations.named(this).configure { receiverConfiguration ->
            receiverConfiguration.dependencies.addAllLater(
                project.objects.listProperty(Dependency::class.java).apply {
                    set(
                        project.provider {
                            configurationNames
                                .map { project.configurations.getByName(it) }
                                .flatMap { it.allDependencies }
                        }
                    )
                }
            )
        }
    }
}
