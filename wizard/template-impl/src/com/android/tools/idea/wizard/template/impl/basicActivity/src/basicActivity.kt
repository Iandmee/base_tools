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
package com.android.tools.idea.wizard.template.impl.basicActivity.src

import com.android.tools.idea.wizard.template.escapeKotlinIdentifier
import com.android.tools.idea.wizard.template.getMaterialComponentName
import com.android.tools.idea.wizard.template.renderIf

fun basicActivityKt(
  isNewProject: Boolean,
  applicationPackage: String?,
  packageName: String,
  useMaterial2: Boolean,
  useAndroidX: Boolean,
  activityClass: String,
  layoutName: String
): String {
  val menuName = "menuName" // mismatch...

  val newProjectImportBlock = """
    import android.view.Menu
    import android.view.MenuItem
    """.trimIndent() renderIf { isNewProject }

  val applicationPackageImportBlock = """
    import $applicationPackage.R""".trimIndent() renderIf { applicationPackage != null }

  val newProjectBlock2 = """
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
      // Inflate the menu; this adds items to the action bar if it is present.
      menuInflater.inflate(R.menu.$menuName, menu)
      return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
      // Handle action bar item clicks here. The action bar will
      // automatically handle clicks on the Home/Up button, so long
      // as you specify a parent activity in AndroidManifest.xml.
      return when(item.itemId) {
        R.id.action_settings -> true
        else -> super.onOptionsItemSelected(item)
      }
    }
    """.trimIndent() renderIf { isNewProject }

  return """
    package ${escapeKotlinIdentifier(packageName)}

    import android.os.Bundle
    import ${getMaterialComponentName("android.support.design.widget.Snackbar", useMaterial2)}
    import ${getMaterialComponentName("android.support.v7.app.AppCompatActivity", useAndroidX)}

    $newProjectImportBlock

    $applicationPackageImportBlock

    import kotlinx.android.synthetic.main.$layoutName.*

    class $activityClass : AppCompatActivity() {

      override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.$layoutName)
        setSupportActionBar(toolbar)

        fab.setOnClickListener { view ->
          Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
            .setAction("Action", null).show()
        }
      }
      $newProjectBlock2
    }
    """.trimIndent()
}
