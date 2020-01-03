/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.wizard.template.impl.other.folders

import com.android.tools.idea.wizard.template.BooleanParameter
import com.android.tools.idea.wizard.template.Category
import com.android.tools.idea.wizard.template.CheckBoxWidget
import com.android.tools.idea.wizard.template.Constraint
import com.android.tools.idea.wizard.template.FormFactor
import com.android.tools.idea.wizard.template.ModuleTemplateData
import com.android.tools.idea.wizard.template.StringParameter
import com.android.tools.idea.wizard.template.TemplateData
import com.android.tools.idea.wizard.template.TextFieldWidget
import com.android.tools.idea.wizard.template.WizardUiContext
import com.android.tools.idea.wizard.template.booleanParameter
import com.android.tools.idea.wizard.template.stringParameter
import com.android.tools.idea.wizard.template.template
import java.io.File

val resourcesFolderTemplate
  get() = template {
    revision = 1
    name = "Java Resources Folder"
    minApi = 16
    minBuildApi = 16
    description = "Creates a source root for Java Resource (NOT Android resource) files."

    category = Category.Folder
    formFactor = FormFactor.Generic
    screens = listOf(WizardUiContext.MenuEntry)

    val remapFolder: BooleanParameter = booleanParameter {
      name = "Change Folder Location"
      default = false
      help = "Change the folder location to another folder within the module"
    }

    val newLocation: StringParameter = stringParameter {
      name = "New Folder Location"
      constraints = listOf(Constraint.NONEMPTY, Constraint.SOURCE_SET_FOLDER, Constraint.UNIQUE)
      default = ""
      suggest = { "src/${sourceProviderName}/resources/" }
      help = "The location for the new folder"
      enabled = { remapFolder.value }
    }

    // This is an invisible parameter to pass data from [WizardTemplateData] to the recipe.
    val sourceProviderName: StringParameter = stringParameter {
      name = "Source Provider Name"
      constraints = listOf()
      default = ""
      visible = { false }
      suggest = { sourceProviderName }
    }

    widgets(
      CheckBoxWidget(remapFolder),
      // TODO(qumeric): make a widget for path input?
      TextFieldWidget(newLocation),
      // TODO(qumeric): provide a better way to pass data than creating a widget with invisible parameter
      TextFieldWidget(sourceProviderName)
    )

    thumb {
      // TODO(b/147126989)
      File("no_activity.png")
    }

    recipe = { data: TemplateData ->
      generateResourcesFolder(data as ModuleTemplateData, remapFolder.value, newLocation.value, { sourceProviderName.suggest()!! })
    }
  }