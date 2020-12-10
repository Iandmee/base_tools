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

package com.android.tools.idea.wizard.template.impl.activities.androidTVActivity.src.app_package

import com.android.tools.idea.wizard.template.getMaterialComponentName
import com.android.tools.idea.wizard.template.escapeKotlinIdentifier

fun errorFragmentKt(
  minApiLevel: Int,
  packageName: String,
  useAndroidX: Boolean
): String {
  val getDrawableArgBlock = if (minApiLevel >= 23) "context!!" else "activity!!"
  return """
package ${escapeKotlinIdentifier(packageName)}

import android.os.Bundle
import android.view.View

import ${getMaterialComponentName("android.support.v4.content.ContextCompat", useAndroidX)}

/**
 * This class demonstrates how to extend [${getMaterialComponentName("android.support.v17.leanback.app.ErrorSupportFragment", useAndroidX)}].
 */
class ErrorFragment : ${getMaterialComponentName("android.support.v17.leanback.app.ErrorSupportFragment", useAndroidX)}() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = resources.getString(R.string.app_name)
    }

    internal fun setErrorContent() {
        imageDrawable = ContextCompat.getDrawable($getDrawableArgBlock, androidx.leanback.R.drawable.lb_ic_sad_cloud)
        message = resources.getString(R.string.error_fragment_message)
        setDefaultBackground(TRANSLUCENT)

        buttonText = resources.getString(R.string.dismiss_error)
        buttonClickListener = View.OnClickListener {
            fragmentManager!!.beginTransaction().remove(this@ErrorFragment).commit()
        }
    }

    companion object {
        private val TRANSLUCENT = true
    }
}"""
}
