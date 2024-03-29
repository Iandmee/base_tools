/*
 * Copyright (C) 2011 The Android Open Source Project
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

import com.android.tools.lint.detector.api.Detector

class WrongImportDetectorTest : AbstractCheckTest() {
  override fun getDetector(): Detector {
    return WrongImportDetector()
  }

  fun testJava() {
    //noinspection all // Sample code
    lint()
      .files(
        java(
            """
                package test.pkg;

                import android.app.Activity;
                import android.os.Bundle;
                import android.R;
                import android.widget.*;

                public class BadImport {
                }
                """
          )
          .indented()
      )
      .run()
      .expect(
        """
            src/test/pkg/BadImport.java:5: Warning: Don't include android.R here; use a fully qualified name for each usage instead [SuspiciousImport]
            import android.R;
            ~~~~~~~~~~~~~~~~~
            0 errors, 1 warnings
            """
      )
  }

  fun testKotlin() {
    lint()
      .files(
        kotlin(
            """
                import android.R // ERROR
                import android.R as AndroidR // OK
                """
          )
          .indented()
      )
      .run()
      .expect(
        """
            src/test.kt:1: Warning: Don't include android.R here; use a fully qualified name for each usage instead [SuspiciousImport]
            import android.R // ERROR
            ~~~~~~~~~~~~~~~~~~~~~~~~~
            0 errors, 1 warnings
            """
      )
  }
}
