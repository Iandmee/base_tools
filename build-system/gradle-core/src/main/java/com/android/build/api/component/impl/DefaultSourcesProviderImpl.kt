/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.build.api.component.impl

import com.android.build.api.variant.impl.DirectoryEntries
import com.android.build.api.variant.impl.DirectoryEntry
import com.android.build.api.variant.impl.FileBasedDirectoryEntryImpl
import com.android.build.api.variant.impl.TaskProviderBasedDirectoryEntryImpl
import com.android.build.gradle.api.AndroidSourceSet
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.builder.compiling.BuildConfigType
import java.util.Collections

/**
 * Computes the default sources for all [com.android.build.api.variant.impl.SourceType]s.
 */
class DefaultSourcesProviderImpl(val component: ComponentImpl): DefaultSourcesProvider {

    override val java: List<DirectoryEntry>
        get() = component.defaultJavaSources()
    override val res: List<DirectoryEntries>
        get() = component.defaultResSources()

    /**
     * Computes the default java sources: source sets and generated sources.
     * For access to the final list of java sources, use [com.android.build.api.variant.Sources]
     *
     * Every entry is a ConfigurableFileTree instance to enable incremental java compilation.
     */
    private fun ComponentImpl.defaultJavaSources(): List<DirectoryEntry> {
        // Build the list of source folders.
        val sourceSets = mutableListOf<DirectoryEntry>()

        // First the actual source folders.
        val providers = variantSources.sortedSourceProviders
        for (provider in providers) {
            val sourceSet = provider as AndroidSourceSet
            for (srcDir in sourceSet.java.srcDirs) {
                sourceSets.add(
                    FileBasedDirectoryEntryImpl(
                        name = sourceSet.name,
                        directory = srcDir,
                        filter = (provider as AndroidSourceSet).java.filter,
                    )
                )
            }
        }

        // for the other, there's no duplicate so no issue.
        if (buildConfigEnabled && getBuildConfigType() == BuildConfigType.JAVA_SOURCE) {
            sourceSets.add(
                TaskProviderBasedDirectoryEntryImpl(
                    "generated_build_config",
                    artifacts.get(InternalArtifactType.GENERATED_BUILD_CONFIG_JAVA),
                )
            )
        }
        if (taskContainer.aidlCompileTask != null) {
            sourceSets.add(
                TaskProviderBasedDirectoryEntryImpl(
                    "generated_aidl",
                    artifacts.get(InternalArtifactType.AIDL_SOURCE_OUTPUT_DIR),
                )
            )
        }
        if (buildFeatures.dataBinding || buildFeatures.viewBinding) {
            // DATA_BINDING_TRIGGER artifact is created for data binding only (not view binding)
            if (buildFeatures.dataBinding) {
                // Under some conditions (e.g., for a unit test variant where
                // includeAndroidResources == false or testedVariantType != AAR, see
                // TaskManager.createUnitTestVariantTasks), the artifact may not have been created,
                // so we need to check its presence first (using internal AGP API instead of Gradle
                // API---see https://android.googlesource.com/platform/tools/base/+/ca24108e58e6e0dc56ce6c6f639cdbd0fa3b812f).
                if (!artifacts.getArtifactContainer(InternalArtifactType.DATA_BINDING_TRIGGER)
                        .needInitialProducer().get()
                ) {
                    sourceSets.add(
                        TaskProviderBasedDirectoryEntryImpl(
                            name = "databinding_generated",
                            directoryProvider = artifacts.get(InternalArtifactType.DATA_BINDING_TRIGGER),
                        )
                    )
                }
            }
            addDataBindingSources(sourceSets)
        }
        addRenderscriptSources(sourceSets)
        if (buildFeatures.mlModelBinding) {
            sourceSets.add(
                TaskProviderBasedDirectoryEntryImpl(
                    name = "mlModel_generated",
                    directoryProvider = artifacts.get(InternalArtifactType.ML_SOURCE_OUT),
                )
            )
        }
        return sourceSets
    }

    private fun ComponentImpl.defaultResSources(): List<DirectoryEntries> {
        val sourceDirectories = mutableListOf<DirectoryEntries>()

        sourceDirectories.addAll(variantSources.resSourceList)

        val generatedFolders = mutableListOf<DirectoryEntry>()
        if (buildFeatures.renderScript) {
            generatedFolders.add(
                TaskProviderBasedDirectoryEntryImpl(
                    name = "renderscript_generated_res",
                    directoryProvider = artifacts.get(InternalArtifactType.RENDERSCRIPT_GENERATED_RES),
                )
            )
        }

        if (buildFeatures.resValues) {
            generatedFolders.add(
                TaskProviderBasedDirectoryEntryImpl(
                    name = "generated_res",
                    directoryProvider = artifacts.get(InternalArtifactType.GENERATED_RES),
                )
            )
        }

        sourceDirectories.add(DirectoryEntries("generated", generatedFolders))

        return Collections.unmodifiableList(sourceDirectories)
    }
}