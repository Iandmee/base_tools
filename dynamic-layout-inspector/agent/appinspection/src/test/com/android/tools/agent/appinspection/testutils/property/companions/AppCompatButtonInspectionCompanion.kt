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

package com.android.tools.agent.appinspection.testutils.property.companions

import android.view.inspector.InspectionCompanion
import android.view.inspector.PropertyMapper
import android.view.inspector.PropertyReader
import androidx.appcompat.widget.AppCompatButton
import com.android.tools.agent.appinspection.testutils.property.ATTR_OFFSET
import com.android.tools.agent.appinspection.testutils.property.EnumPropertyMapper
import com.android.tools.agent.appinspection.testutils.property.EnumPropertyReader

class AppCompatButtonInspectionCompanion : InspectionCompanion<AppCompatButton> {

    companion object {
        val OFFSET = ButtonInspectionCompanion.OFFSET + ButtonInspectionCompanion.NUM_PROPERTIES

        fun addResourceNames(packageName: String, resourceNames: MutableMap<Int, String>) {
            resourceNames[ATTR_OFFSET + OFFSET] = "$packageName.attr/backgroundTint"
        }
    }

    internal enum class Property {
        BACKGROUND_TINT
    }

    override fun mapProperties(propertyMapper: PropertyMapper) {
        val mapper = EnumPropertyMapper<Property>(propertyMapper, OFFSET)
        mapper.mapObject(Property.BACKGROUND_TINT)
    }

    override fun readProperties(button: AppCompatButton, propertyReader: PropertyReader) {
        val reader = EnumPropertyReader<Property>(propertyReader, OFFSET)
        reader.readObject(Property.BACKGROUND_TINT, button.supportBackgroundTint)
    }
}
