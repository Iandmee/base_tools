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

package com.android.tools.idea.wizard.template.impl.activities.androidThingsActivity.src.app_package

import com.android.tools.idea.wizard.template.getMaterialComponentName
import com.android.tools.idea.wizard.template.renderIf


fun simpleActivityJava(
  activityClass: String,
  generateLayout: Boolean,
  layoutName: String,
  packageName: String,
  useAndroidX: Boolean
): String {
  val layoutBlock = renderIf(generateLayout) {"setContentView(R.layout.${layoutName});"}
  return """
package ${packageName};

import ${getMaterialComponentName("android.support.v7.app.AppCompatActivity", useAndroidX)};
import android.os.Bundle;

/**
 * Skeleton of an Android Things activity.
 *
 * Android Things peripheral APIs are accessible through the PeripheralManager
 * For example, the snippet below will open a GPIO pin and set it to HIGH:
 *
 * PeripheralManager manager = PeripheralManager.getInstance();
 * try {
 *     Gpio gpio = manager.openGpio("BCM6");
 *     gpio.setDirection(Gpio.DIRECTION_OUT_INITIALLY_LOW);
 *     gpio.setValue(true);
 * } catch (IOException e) {
 *     Log.e(TAG, "Unable to access GPIO");
 * }
 *
 * You can find additional examples on GitHub: https://github.com/androidthings
 */
public class ${activityClass} extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        $layoutBlock
    }
}
"""
}
