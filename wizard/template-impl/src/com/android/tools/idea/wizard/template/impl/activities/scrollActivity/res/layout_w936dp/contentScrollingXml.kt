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

package com.android.tools.idea.wizard.template.impl.activities.scrollActivity.res.layout_w936dp

import com.android.tools.idea.wizard.template.getMaterialComponentName

fun contentScrollingXml(
  activityClass: String,
  layoutName: String,
  packageName: String,
  useAndroidX: Boolean
) =
  """<?xml version="1.0" encoding="utf-8"?>
<${getMaterialComponentName("android.support.constraint.ConstraintLayout", useAndroidX)} xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    xmlns:tools="http://schemas.android.com/tools"
    app:layout_behavior="@string/appbar_scrolling_view_behavior"
    tools:showIn="@layout/${layoutName}"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    tools:context="${packageName}.${activityClass}">

    <${getMaterialComponentName("android.support.v4.widget.NestedScrollView", useAndroidX)}
        android:layout_width="840dp"
        android:layout_height="match_parent"
        app:layout_constraintEnd_toEndOf="parent"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintTop_toTopOf="parent" >

        <TextView
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_marginTop="48dp"
            android:text="@string/large_text" />
    </${getMaterialComponentName("android.support.v4.widget.NestedScrollView", useAndroidX)}>
</${getMaterialComponentName("android.support.constraint.ConstraintLayout", useAndroidX)}>
"""
