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

package com.android.build.gradle.internal.tasks

import com.android.build.api.component.impl.ComponentPropertiesImpl
import com.android.build.gradle.internal.AndroidDependenciesRenderer
import com.google.common.collect.ImmutableList
import java.io.IOException
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.internal.logging.text.StyledTextOutputFactory

open class DependencyReportTask : DefaultTask() {

    private val renderer = AndroidDependenciesRenderer()

    @get:Internal
    lateinit var components: ImmutableList<ComponentPropertiesImpl>

    @TaskAction
    @Throws(IOException::class)
    fun generate() {
        renderer.setOutput(services.get(StyledTextOutputFactory::class.java).create(javaClass))
        val sortedComponents = components.sortedWith(compareBy { it.name })

        for (component in sortedComponents) {
            renderer.startComponent(component)
            renderer.render(component)
        }
    }

}
