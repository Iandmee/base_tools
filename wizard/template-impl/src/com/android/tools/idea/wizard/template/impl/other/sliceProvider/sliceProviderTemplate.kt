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

package com.android.tools.idea.wizard.template.impl.other.sliceProvider

import com.android.tools.idea.wizard.template.Category
import com.android.tools.idea.wizard.template.Constraint.CLASS
import com.android.tools.idea.wizard.template.Constraint.NONEMPTY
import com.android.tools.idea.wizard.template.Constraint.UNIQUE
import com.android.tools.idea.wizard.template.Constraint.URI_AUTHORITY
import com.android.tools.idea.wizard.template.FormFactor
import com.android.tools.idea.wizard.template.LanguageWidget
import com.android.tools.idea.wizard.template.ModuleTemplateData
import com.android.tools.idea.wizard.template.TemplateConstraint
import com.android.tools.idea.wizard.template.TemplateData
import com.android.tools.idea.wizard.template.TextFieldWidget
import com.android.tools.idea.wizard.template.WizardUiContext
import com.android.tools.idea.wizard.template.stringParameter
import com.android.tools.idea.wizard.template.template
import java.io.File

val sliceProviderTemplate
  get() = template {
    revision = 1
    name = "Slice Provider"
    minBuildApi = 28
    constraints = listOf(TemplateConstraint.AndroidX)
    description = "Creates a new SliceProvider component and adds it to your Android manifest."

    formFactor = FormFactor.Mobile
    category = Category.Other
    screens = listOf(WizardUiContext.MenuEntry)

    val className = stringParameter {
      name = "Class Name"
      default = "MySliceProvider "
      constraints = listOf(CLASS, UNIQUE, NONEMPTY)
    }

    val authorities = stringParameter {
      name = "URI Authorities"
      default = ""
      help = "A semicolon separated list of one or more URI authorities that identify data under the purview of the SliceProvider. "
      constraints = listOf(NONEMPTY, URI_AUTHORITY)
      suggest = { packageName }
    }

    val hostUrl = stringParameter {
      name = "Host URL"
      default = ""
      help = "An HTTP URL that will expose the SliceProvider. "
      constraints = listOf(NONEMPTY)
      // TODO: It may be better to add an additional field for companyDomain to WizardParameterData, but it may be too much given that
      //  this is the only template that uses the domain
      suggest = { "example.com" }
    }

    val pathPrefix = stringParameter {
      name = "Path Prefix"
      default = "/"
      help = "A partial path in the URL that is matched to the SliceProvider. "
      constraints = listOf(NONEMPTY)
    }

    widgets(
      TextFieldWidget(className),
      TextFieldWidget(authorities),
      TextFieldWidget(hostUrl),
      TextFieldWidget(pathPrefix),
      LanguageWidget()
    )

    thumb {
      // TODO(b/147126989)
      File("no_activity.png")
    }

    recipe = { data: TemplateData ->
      sliceProviderRecipe(data as ModuleTemplateData, className.value, authorities.value, hostUrl.value, pathPrefix.value)
    }
  }
