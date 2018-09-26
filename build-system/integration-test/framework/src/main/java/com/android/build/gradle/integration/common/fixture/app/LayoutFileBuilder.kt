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

package com.android.build.gradle.integration.common.fixture.app

/** Builder for the contents of an XML layout file. */
class LayoutFileBuilder {

    var useAndroidX: Boolean = false
    var withDataBinding: Boolean = false

    private val variables = StringBuilder()
    private val widgets = StringBuilder()

    fun addVariable(name: String, type: String) {
        variables.append("\n")
        variables.append("""
            <variable name="$name" type="$type"/>
            """.trimIndent()
        )
    }

    fun addTextView(id: String, text: String = "", customProperties: List<String> = listOf()) {
        widgets.append("\n")
        widgets.append("<TextView")

        val properties = StringBuilder()
        properties.append("\n")
        properties.append("""
            android:id="@+id/$id"
            android:text="$text"
            """.trimIndent())
        for (customerProperty in customProperties) {
            properties.append("\n$customerProperty")
        }
        properties.append("\n")
        properties.append("""
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            app:layout_constraintBottom_toBottomOf="parent"
            app:layout_constraintLeft_toLeftOf="parent"
            app:layout_constraintRight_toRightOf="parent"
            app:layout_constraintTop_toTopOf="parent" />
            """.trimIndent())

        widgets.append(properties.toString().prependIndent("\t"))
    }

    fun build(): String {
        val contents = StringBuilder()

        val constraintLayoutClass = if (useAndroidX) {
            "androidx.constraintlayout.widget.ConstraintLayout"
        } else {
            "android.support.constraint.ConstraintLayout"
        }

        if (withDataBinding) {
            contents.append(
                """
                <?xml version="1.0" encoding="utf-8"?>
                <layout xmlns:android="http://schemas.android.com/apk/res/android">
                """.trimIndent()
            )
            if (variables.isNotEmpty()) {
                contents.append("\n\n<data>")
                contents.append(variables.toString().prependIndent("\t"))
                contents.append("\n</data>")
            }
            contents.append("\n\n")
            contents.append("""
                <$constraintLayoutClass
                    xmlns:app="http://schemas.android.com/apk/res-auto"
                    android:layout_width="match_parent"
                    android:layout_height="match_parent">
                """.trimIndent()
            )
        } else {
            contents.append(
                """
                <?xml version="1.0" encoding="utf-8"?>
                <$constraintLayoutClass xmlns:android="http://schemas.android.com/apk/res/android"
                    xmlns:app="http://schemas.android.com/apk/res-auto"
                    android:layout_width="match_parent"
                    android:layout_height="match_parent">
                """.trimIndent()
            )
        }

        if (widgets.isNotEmpty()) {
            contents.append("\n")
            contents.append(widgets.toString().prependIndent("\t"))
        }

        contents.append("\n\n</$constraintLayoutClass>")
        if (withDataBinding) {
            contents.append("\n\n</layout>")
        }

        return contents.toString()
    }
}