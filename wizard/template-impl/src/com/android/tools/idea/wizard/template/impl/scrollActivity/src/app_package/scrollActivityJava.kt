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

package com.android.tools.idea.wizard.template.impl.scrollActivity.src.app_package

import com.android.tools.idea.wizard.template.getMaterialComponentName
import com.android.tools.idea.wizard.template.renderIf

fun scrollActivityJava(
  activityClass: String,
  applicationPackage: String?,
  isNewModule: Boolean,
  layoutName: String,
  menuName: String,
  packageName: String,
  useAndroidX: Boolean,
  useMaterial2: Boolean): String {
  val newModuleImportBlock = renderIf(isNewModule) {"""
import android.view.Menu;
import android.view.MenuItem; 
  """.trimIndent()}
  val newModuleBlock = renderIf(isNewModule) {"""
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.${menuName}, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }
        return super.onOptionsItemSelected(item);
    } 
  """.trimIndent()}
  val applicationPackageBlock = renderIf(applicationPackage != null) {"import ${applicationPackage}.R;"}
  return """package ${packageName};

import android.os.Bundle;
import ${getMaterialComponentName("android.support.design.widget.CollapsingToolbarLayout", useMaterial2)};
import ${getMaterialComponentName("android.support.design.widget.FloatingActionButton", useMaterial2)};
import ${getMaterialComponentName("android.support.design.widget.Snackbar", useMaterial2)};
import ${getMaterialComponentName("android.support.v7.app.AppCompatActivity", useAndroidX)};
import ${getMaterialComponentName("android.support.v7.widget.Toolbar", useAndroidX)};
import android.view.View;
$newModuleImportBlock
$applicationPackageBlock

public class ${activityClass} extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.${layoutName});
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        CollapsingToolbarLayout toolBarLayout = (CollapsingToolbarLayout) findViewById(R.id.toolbar_layout);
        toolBarLayout.setTitle(getTitle());

        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();
            }
        });
    }
$newModuleBlock
}
"""
}
