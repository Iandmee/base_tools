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

package com.android.tools.idea.wizard.template.impl.activities.androidTVActivity

import com.android.tools.idea.wizard.template.activityToLayout
import com.android.tools.idea.wizard.template.renderIf

fun androidManifestXml(
  activityClass: String,
  detailsActivity: String,
  isLibrary: Boolean,
  isNewModule: Boolean,
  packageName: String,
  themeName: String,
): String {
  val labelBlock = if (isNewModule) "android:label=\"@string/app_name\""
  else "android:label=\"@string/title_${activityToLayout(activityClass)}\""
  val launcher = !isLibrary
  val intentFilterBlock = renderIf(launcher) {"""
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LEANBACK_LAUNCHER" />
            </intent-filter>
  """
  }
  return """
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools">

    <uses-permission
        android:name="android.permission.INTERNET" />

    <uses-feature
        android:name="android.hardware.touchscreen"
        android:required="false" />

    <uses-feature android:name="android.software.leanback"
        android:required="true" />

    <application android:theme="$themeName">

        <activity android:name="${packageName}.${activityClass}"
            android:exported="$launcher"
            android:icon="@drawable/app_icon_your_company"
            android:logo="@drawable/app_icon_your_company"
            android:banner="@drawable/app_icon_your_company"
            android:screenOrientation="landscape"
            $labelBlock>
            $intentFilterBlock
        </activity>

        <activity android:name="${packageName}.${detailsActivity}" android:exported="false" />
        <activity android:name="PlaybackActivity" android:exported="false" />
        <activity android:name="BrowseErrorActivity" android:exported="false" />

    </application>

</manifest>
"""
}
