/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_PERMISSION
import com.android.SdkConstants.TAG_ACTIVITY
import com.android.SdkConstants.TAG_ACTIVITY_ALIAS
import com.android.SdkConstants.TAG_APPLICATION
import com.android.SdkConstants.TAG_PROVIDER
import com.android.SdkConstants.TAG_RECEIVER
import com.android.SdkConstants.TAG_SERVICE
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import org.w3c.dom.Element

/**
 * looks for obvious errors in the guarding of components with a permission via the android:permission attribute
 */
class PermissionErrorDetector : Detector(), XmlScanner {
    override fun getApplicableElements(): Collection<String> = listOf(
        TAG_APPLICATION,
        TAG_ACTIVITY,
        TAG_ACTIVITY_ALIAS,
        TAG_RECEIVER,
        TAG_SERVICE,
        TAG_PROVIDER,
    )

    override fun visitElement(context: XmlContext, element: Element) {
        element.getAttributeNodeNS(ANDROID_URI, ATTR_PERMISSION)?.let { attr ->
            if (KNOWN_PERMISSION_ERROR_VALUES.any { it.equals(attr.value, ignoreCase = true) }) {
                context.report(
                    KNOWN_PERMISSION_ERROR,
                    element,
                    context.getValueLocation(attr),
                    "${attr.value} is not a valid permission value"
                )
            }
        }
    }

    companion object {
        @JvmField
        val KNOWN_PERMISSION_ERROR: Issue = Issue.create(
            id = "KnownPermissionError",
            briefDescription = "Value specified for permission is a known error",
            explanation = """
                This check looks for values specified in component permissions that are known errors, such as \
                `android:permission="true"`.

                 Please double check the permission value you have supplied.  The value is expected to be a \
                 permission string from the system, another app, or your own, NOT a boolean.
                """,
            category = Category.SECURITY,
            priority = 5,
            severity = Severity.ERROR,
            implementation = Implementation(
                PermissionErrorDetector::class.java,
                Scope.MANIFEST_SCOPE
            )
        )

        val KNOWN_PERMISSION_ERROR_VALUES = listOf("true", "false") // TODO: additional obvious values?
    }
}