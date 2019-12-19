/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.tools.idea.wizard.template.impl.activities.googleMapsWearActivity.res.layout

import com.android.tools.idea.wizard.template.getMaterialComponentName

fun activityMapXml(
  activityClass: String,
  packageName: String,
  useAndroidX: Boolean
) = """
<?xml version="1.0" encoding="utf-8"?>

<${getMaterialComponentName("android.support.wear.widget.SwipeDismissFrameLayout", useAndroidX)}
        xmlns:android="http://schemas.android.com/apk/res/android"
        xmlns:tools="http://schemas.android.com/tools"
        xmlns:map="http://schemas.android.com/apk/res-auto"
        android:id="@+id/swipe_dismiss_root_container"
        android:layout_height="match_parent"
        android:layout_width="match_parent"
        tools:context="${packageName}.${activityClass}">

    <FrameLayout
            android:id="@+id/map_container"
            android:layout_width="match_parent"
            android:layout_height="match_parent">

        <fragment
                android:id="@+id/map"
                android:layout_width="match_parent"
                android:layout_height="match_parent"
                android:name="com.google.android.gms.maps.MapFragment"/>

    </FrameLayout>
</${getMaterialComponentName("android.support.wear.widget.SwipeDismissFrameLayout", useAndroidX)}>
"""
