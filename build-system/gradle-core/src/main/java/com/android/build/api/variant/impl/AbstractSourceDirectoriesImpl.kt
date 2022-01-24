/*
 * Copyright (C) 2021 The Android Open Source Project
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

import com.android.build.api.variant.AbstractSourceDirectories
import com.android.build.api.variant.SourceDirectories
import org.gradle.api.Task
import org.gradle.api.file.Directory
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.util.PatternFilterable
import org.gradle.api.tasks.util.PatternSet
import java.io.File

abstract class AbstractSourceDirectoriesImpl(
    private val _name: String,
    private val projectDirectory: Directory,
    private val variantDslFilters: PatternFilterable?
): AbstractSourceDirectories {

    /**
     * Filters to use for the variant source folders only.
     * This will be initialized from the variant DSL source folder filters if it exists or empty
     * if it does not.
     */
    val filter = PatternSet().also {
        if (variantDslFilters != null) {
            it.setIncludes(variantDslFilters.includes)
            it.setExcludes(variantDslFilters.excludes)
        }
    }

    override fun <T : Task> add(taskProvider: TaskProvider<T>, wiredWith: (T) -> Provider<Directory>) {12
        val mappedValue: Provider<Directory> = taskProvider.flatMap {
            wiredWith(it)
        }
        addSource(
            TaskProviderBasedDirectoryEntryImpl(
                "$name-${taskProvider.name}",
                mappedValue,
                isUserAdded = true,
            )
        )
    }

    override fun getName(): String = _name

    override fun addSrcDir(srcDir: String) {
        val directory = projectDirectory.dir(srcDir)
        if (!directory.asFile.exists() || !directory.asFile.isDirectory) {
            throw IllegalArgumentException("$srcDir does not point to a directory")
        }
        addSource(
            FileBasedDirectoryEntryImpl(
                name = "variant",
                directory = directory.asFile,
                filter = filter,
                isUserAdded = true
            )
        )
    }

    internal abstract fun addSource(directoryEntry: DirectoryEntry)

    internal abstract fun variantSourcesForModel(filter: (DirectoryEntry) -> Boolean ): List<File>
}
