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

package com.android.build.gradle.internal.cxx.configure

import com.android.build.api.component.impl.ComponentBuilderImpl
import com.android.build.api.variant.impl.LibraryVariantImpl
import com.android.build.api.variant.impl.VariantImpl
import com.android.build.gradle.LibraryExtension
import com.android.build.gradle.internal.SdkComponentsBuildService
import com.android.build.gradle.internal.core.Abi
import com.android.build.gradle.internal.cxx.configure.CxxGradleTaskModel.*
import com.android.build.gradle.internal.cxx.gradle.generator.CxxConfigurationModel
import com.android.build.gradle.internal.cxx.gradle.generator.CxxConfigurationParameters
import com.android.build.gradle.internal.cxx.gradle.generator.tryCreateConfigurationParameters
import com.android.build.gradle.internal.cxx.logging.IssueReporterLoggingEnvironment
import com.android.build.gradle.internal.cxx.logging.PassThroughDeduplicatingLoggingEnvironment
import com.android.build.gradle.internal.cxx.model.CxxAbiModel
import com.android.build.gradle.internal.cxx.model.createCxxAbiModel
import com.android.build.gradle.internal.cxx.model.createCxxModuleModel
import com.android.build.gradle.internal.cxx.model.createCxxVariantModel
import com.android.build.gradle.internal.cxx.settings.rewriteCxxAbiModelWithCMakeSettings
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactScope.ALL
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType.JNI
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH
import com.android.build.gradle.internal.tasks.PrefabModuleTaskData
import com.android.build.gradle.internal.tasks.PrefabPackageTask
import com.android.build.gradle.internal.tasks.factory.TaskFactory
import com.android.build.gradle.internal.tasks.factory.dependsOn
import com.android.build.gradle.internal.variant.ComponentInfo
import com.android.build.gradle.tasks.createCxxConfigureTask
import com.android.build.gradle.tasks.createReferringCxxBuildTask
import com.android.build.gradle.tasks.createVariantCxxCleanTask
import com.android.build.gradle.tasks.createWorkingCxxBuildTask
import com.android.builder.errors.IssueReporter
import com.android.utils.appendCapitalized
import org.gradle.api.Task
import org.gradle.api.tasks.TaskProvider

/**
 * Construct gradle tasks for C/C++ configuration and build.
 */
fun <VariantBuilderT : ComponentBuilderImpl, VariantT : VariantImpl> createCxxTasks(
        sdkComponents: SdkComponentsBuildService,
        issueReporter: IssueReporter,
        taskFactory: TaskFactory,
        variants: List<ComponentInfo<VariantBuilderT, VariantT>>) {
    if (variants.isEmpty()) return
    IssueReporterLoggingEnvironment(issueReporter).use {
        PassThroughDeduplicatingLoggingEnvironment().use {
            val configurationParameters =
                    variants.mapNotNull { tryCreateConfigurationParameters(it.variant) }
            if (configurationParameters.isEmpty()) return
            val abis = createInitialCxxModel(sdkComponents, configurationParameters)
            val taskModel = createCxxTaskDependencyModel(abis)

            val global = variants.first().variant.globalScope

            val anchors = mutableMapOf<String, TaskProvider<Task>>()
            fun anchor(name: String) = anchors.computeIfAbsent(name) { taskFactory.register(name) }

            val variantMap = variants.map { it.variant.name to it.variant }.toMap()
            for((name, task) in taskModel.tasks) {
                when(task) {
                    is Configure -> {
                        taskFactory.register(createCxxConfigureTask(
                                global,
                                task.representative.toConfigurationModel(),
                                name))
                    }
                    is Build -> {
                        taskFactory.register(createWorkingCxxBuildTask(
                                global,
                                task.representative.toConfigurationModel(),
                                name))
                    }
                    is VariantBuild -> {
                        val variant = variantMap.getValue(task.variantName)
                        val configuration = task.representatives.toConfigurationModel()
                        val buildTask = taskFactory.register(createReferringCxxBuildTask(
                                task.representatives.toConfigurationModel(),
                                variant,
                                name))
                        variant.taskContainer.cxxConfigurationModel = configuration
                        variant.taskContainer.externalNativeBuildTask = buildTask
                        variant.taskContainer.compileTask.dependsOn(buildTask)
                        buildTask.dependsOn(variant.variantDependencies.getArtifactFileCollection(
                                RUNTIME_CLASSPATH,
                                ALL,
                                JNI))
                        val cleanTask =
                                taskFactory.register(createVariantCxxCleanTask(configuration,
                                        variant))
                        taskFactory.named("clean").dependsOn(cleanTask)
                    }
                    is Anchor -> anchor(name)
                }
            }

            for((dependant, dependee) in taskModel.edges) {
                taskFactory.named(dependant).dependsOn(taskFactory.named(dependee))
            }

            // Set up prefab publishing tasks if they are indicated.
            for(variant in variants) {
                val libraryVariant = variant.variant
                if (libraryVariant !is LibraryVariantImpl) continue
                createPrefabTasks(taskFactory, libraryVariant)
            }
        }
    }
}

fun createPrefabTasks(taskFactory: TaskFactory, libraryVariant: LibraryVariantImpl) {
    if (!libraryVariant.buildFeatures.prefabPublishing) return
    val global = libraryVariant.globalScope
    val extension = global.extension as LibraryExtension
    val project = global.project
    val modules = extension.prefab.map { options ->
        val headers = options.headers?.let { headers ->
            project.layout
                    .projectDirectory
                    .dir(headers)
                    .asFile
        }
        PrefabModuleTaskData(options.name, headers, options.libraryName)
    }
    if (modules.isNotEmpty()) {
        val packageTask= taskFactory.register(
                PrefabPackageTask.CreationAction(
                        modules,
                        global.sdkComponents.get(),
                        libraryVariant.taskContainer.cxxConfigurationModel!!,
                        libraryVariant))
        packageTask
                .get()
                .dependsOn(libraryVariant.taskContainer.externalNativeBuildTask)
    }
}

fun createCxxTaskDependencyModel(abis: List<CxxAbiModel>) : CxxTaskDependencyModel {
    val tasks = mutableMapOf<String, CxxGradleTaskModel>()
    val edges = mutableListOf<Pair<String, String>>()
    val builds = mutableMapOf<String, MutableList<CxxAbiModel>>()
    abis
            .filter { abi -> abi.isActiveAbi }
            .forEach { abi ->
                val variantName = "".appendCapitalized(abi.variant.variantName)
                val taskSuffix = "Cxx$variantName[${abi.abi.tag}]"
                val configureTaskName = "configure$taskSuffix"
                val configureTask = Configure(abi)
                tasks[configureTaskName] = configureTask

                val buildTaskName = "build$taskSuffix"
                val buildTask = Build(abi)
                tasks[buildTaskName] = buildTask

                val generateJsonTaskName = "generateJsonModel$variantName"
                tasks.computeIfAbsent(generateJsonTaskName) { Anchor(abi.variant.variantName) }

                val externalNativeBuildTaskName = "externalNativeBuild$variantName"
                val buildAbis = builds.computeIfAbsent(externalNativeBuildTaskName) { mutableListOf() }
                buildAbis += abi

                edges += buildTaskName to configureTaskName
                edges += generateJsonTaskName to configureTaskName
                edges += externalNativeBuildTaskName to buildTaskName
                edges += externalNativeBuildTaskName to generateJsonTaskName
            }

    builds.forEach { (taskName, abis) ->
        tasks[taskName] = VariantBuild(abis.first().variant.variantName, abis.distinct())
    }

    return CxxTaskDependencyModel(
            tasks = tasks,
            edges = edges.distinct()
    )
}

/**
 * Create the [CxxAbiModel]s for a given build.
 */
fun createInitialCxxModel(
        sdkComponents: SdkComponentsBuildService,
        configurationParameters: List<CxxConfigurationParameters>) : List<CxxAbiModel> {
    return configurationParameters.flatMap { parameters ->
        val module = createCxxModuleModel(sdkComponents, parameters)
        val variant = createCxxVariantModel(parameters, module)
        Abi.getDefaultValues().map { abi ->
            createCxxAbiModel(sdkComponents, parameters, variant, abi)
                    .rewriteCxxAbiModelWithCMakeSettings()
        }
    }
}

fun CxxAbiModel.toConfigurationModel() = listOf(this).toConfigurationModel()

private fun List<CxxAbiModel>.toConfigurationModel() =
        CxxConfigurationModel(
                variant = first().variant,
                activeAbis = filter { it.isActiveAbi },
                unusedAbis = filter { !it.isActiveAbi },
        )
