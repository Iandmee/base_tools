/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.build.gradle.tasks

import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.internal.tasks.NonIncrementalTask
import com.android.build.gradle.internal.tasks.TaskInputHelper
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.builder.compiling.ResValueGenerator
import com.android.builder.model.ClassField
import com.android.utils.FileUtils
import com.google.common.collect.Lists
import java.io.File
import java.util.function.Supplier
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskProvider

@CacheableTask
open class GenerateResValues : NonIncrementalTask() {

    // ----- PUBLIC TASK API -----

    @get:OutputDirectory
    lateinit var resOutputDir: File

    // ----- PRIVATE TASK API -----

    @get:Internal //handled by getItemValues()
    var items: List<Any>
        get() = itemsSupplier.get()
        set(value) {
            itemsSupplier = Supplier { value }
        }

    private lateinit var itemsSupplier: Supplier<List<Any>>

    @Input
    fun getItemValues(): List<String> {
        val resolvedItems = items
        val list = Lists.newArrayListWithCapacity<String>(resolvedItems.size * 3)

        for (item in resolvedItems) {
            if (item is String) {
                list.add(item)
            } else if (item is ClassField) {
                list.add(item.type)
                list.add(item.name)
                list.add(item.value)
            }
        }

        return list
    }

    override fun doTaskAction() {
        val folder = resOutputDir
        val resolvedItems = items

        // Always clean up the directory before use.
        FileUtils.cleanOutputDir(folder)

        if (resolvedItems.isNotEmpty()) {
            val generator = ResValueGenerator(folder)
            generator.addItems(resolvedItems)
            generator.generate()
        }
    }

    class CreationAction(scope: VariantScope) :
        VariantTaskCreationAction<GenerateResValues>(scope) {

        override val name = scope.getTaskName("generate", "ResValues")
        override val type = GenerateResValues::class.java

        override fun handleProvider(
            taskProvider: TaskProvider<out GenerateResValues>
        ) {
            super.handleProvider(taskProvider)
            variantScope.taskContainer.generateResValuesTask = taskProvider
        }

        override fun configure(task: GenerateResValues) {
            super.configure(task)

            task.itemsSupplier = TaskInputHelper.memoize { variantScope.variantConfiguration.resValues }

            task.resOutputDir = variantScope.generatedResOutputDir
        }
    }
}
