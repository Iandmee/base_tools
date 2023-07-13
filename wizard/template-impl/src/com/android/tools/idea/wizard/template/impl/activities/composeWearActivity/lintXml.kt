/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.wizard.template.impl.activities.composeWearActivity

fun lintXml() =
  // language=xml
  """
    <?xml version="1.0" encoding="UTF-8"?>
    <lint>
        <!-- Ignore the IconLocation for the Tile preview images -->
        <issue id="IconLocation">
            <ignore path="res/drawable/tile_preview.png" />
            <ignore path="res/drawable-round/tile_preview.png" />
        </issue>
    </lint>
"""
    .trimIndent()
