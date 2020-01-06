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

package com.android.tools.idea.wizard.template.impl.activities.androidThingsActivity

import com.android.tools.idea.wizard.template.activityToLayout
import com.android.tools.idea.wizard.template.impl.activities.common.commonActivityBody
import com.android.tools.idea.wizard.template.renderIf

fun androidManifestXml(
  activityClass: String,
  isLibrary: Boolean,
  isNewModule: Boolean,
  isThingsLauncher: Boolean,
  packageName: String
): String {
  val intentFilterBlock = renderIf(isThingsLauncher) {"""
                <!-- Make this the first activity that is displayed when the device boots. -->
                <intent-filter>
                    <action android:name="android.intent.action.MAIN" />
                    <category android:name="android.intent.category.HOME" />
                    <category android:name="android.intent.category.DEFAULT" />
                </intent-filter>
  """}

  return """
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <application>

        <uses-library android:name="com.google.android.things"/>

        <activity android:name="${packageName}.${activityClass}" >
            ${commonActivityBody(isThingsLauncher || isNewModule, isLibrary)}
            $intentFilterBlock
        </activity>
    </application>
</manifest>
"""
}
