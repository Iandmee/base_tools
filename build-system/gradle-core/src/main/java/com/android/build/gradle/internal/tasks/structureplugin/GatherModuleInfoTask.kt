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

package com.android.build.gradle.internal.tasks.structureplugin

import com.android.build.gradle.BasePlugin
import com.android.sdklib.AndroidTargetHash
import org.gradle.api.Action
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ModuleDependency
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskAction

open class GatherModuleInfoTask : DefaultTask() {
    private lateinit var sourceProjectName : String
    lateinit var outputProvider : Provider<RegularFile>
        private set
    private var moduleDataHolder = ModuleInfo()

    @TaskAction
    fun action() {
        AndroidCollector().collectInto(moduleDataHolder, this)
        DependencyCollector().collectInto(moduleDataHolder, this)

        moduleDataHolder.path = sourceProjectName
        moduleDataHolder.saveAsJsonTo(outputProvider.get().asFile)
    }

    class ConfigAction(private val project: Project) : Action<GatherModuleInfoTask> {
        override fun execute(task: GatherModuleInfoTask) {
            task.sourceProjectName = project.name
            task.outputProvider = project.layout.buildDirectory.file("local-module-info.json")
        }
    }
}

private interface DataCollector {
    fun collectInto(dataHolder: ModuleInfo, task: GatherModuleInfoTask)
}

private class AndroidCollector : DataCollector {
    override fun collectInto(dataHolder: ModuleInfo, task: GatherModuleInfoTask) {
        if (!task.project.isAndroidProject()) return
        dataHolder.type = ModuleType.ANDROID
        collectBuildConfig(dataHolder, task)
    }

    fun collectBuildConfig(dataHolder: ModuleInfo, task: GatherModuleInfoTask) {
        task.project.plugins.withType(BasePlugin::class.java).firstOrNull()?.let {
            it.extension.defaultConfig.minSdkVersion?.apiLevel?.let {
                dataHolder.androidBuildConfig.minSdkVersion = it }
            it.extension.defaultConfig.targetSdkVersion?.apiLevel?.let {
                dataHolder.androidBuildConfig.targetSdkVersion = it }

            // extension.compileSdkVersion returns "android-27", we want just "27".
            dataHolder.androidBuildConfig.compileSdkVersion =
                    AndroidTargetHash.getPlatformVersion(it.extension.compileSdkVersion)!!.apiLevel
        }
    }
}

private class DependencyCollector : DataCollector {
    val relevantConfigurations = setOf(
        "api", "implementation", "classpath",
        "compile", "compileOnly",
        "runtime", "runtimeOnly",
        "testImplementation", "testCompile",
        "androidTestImplementation", "androidTestCompile",
        "annotationProcessor", "kapt",
        "package", "provided", "wearApp"
    )

    override fun collectInto(dataHolder: ModuleInfo, task: GatherModuleInfoTask) {
        val projectDependencies = task.project.configurations.asIterable()
            .filter { relevantConfigurations.contains(it.name) }
            .flatMap { gatherDependencies(it) }.asIterable()
            .cleanDependencies(task.project)

        dataHolder.dependencies.addAll(projectDependencies)
    }

    private fun gatherDependencies(config: Configuration): List<PoetDependenciesInfo> {
        val deps = mutableListOf<PoetDependenciesInfo>()

        for (dependency in config.allDependencies) when (dependency) {
                is ProjectDependency -> PoetDependenciesInfo(
                    DependencyType.MODULE,
                    config.name,
                    dependency.dependencyProject.path
                )
                is ModuleDependency -> PoetDependenciesInfo(
                    DependencyType.EXTERNAL_LIBRARY,
                    config.name,
                    "${dependency.group}:${dependency.name}:${dependency.version}"
                )
                else -> null
            }?.let { deps.add(it) }
        return deps
    }

    private fun Iterable<PoetDependenciesInfo>.cleanDependencies(project: Project): Iterable<PoetDependenciesInfo> {
        var filteredDependencies = this

        // Java modules that have the "kotlin" plugin applied creates repeated dependencies for
        // kotlin jdk, so here we remove them to not pollute the resulting file.
        if (project.plugins.hasPlugin("java-library") && project.plugins.hasPlugin("kotlin")) {
            // Keep only the compile dependency if it's org.jetbrains.kotlin:kotlin-stdlib-jdk.
            filteredDependencies = filteredDependencies.filter {
                !it.dependency.startsWith("org.jetbrains.kotlin:kotlin-stdlib-jdk") ||
                        it.scope == "compile"
            }
        }

        return filteredDependencies
    }

}

private fun Project.isAndroidProject(): Boolean {
    // "com.android.application", "com.android.library", "com.android.test"
    return plugins.withType(BasePlugin::class.java).count() > 0
}