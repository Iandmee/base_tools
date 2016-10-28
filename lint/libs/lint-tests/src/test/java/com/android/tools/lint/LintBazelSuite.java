/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.tools.lint;

import com.android.testutils.JarTestSuiteRunner;
import com.android.tools.lint.checks.AnnotationDetectorTest;
import com.android.tools.lint.checks.ApiDetectorTest;
import com.android.tools.lint.checks.CallSuperDetectorTest;
import com.android.tools.lint.checks.JavaScriptInterfaceDetectorTest;
import com.android.tools.lint.checks.ObjectAnimatorDetectorTest;
import com.android.tools.lint.checks.SupportAnnotationDetectorTest;
import com.android.tools.lint.checks.TypoDetectorTest;
import com.android.tools.lint.checks.TypoLookupTest;
import com.android.tools.lint.checks.UnpackedNativeCodeDetectorTest;
import com.android.tools.lint.checks.VersionChecksTest;
import com.android.tools.lint.client.api.CustomRuleTest;
import org.junit.runner.RunWith;

/**
 * Suite used to run lint tests with Bazel. Skips tests that are known to fail under Bazel for now.
 */
@RunWith(JarTestSuiteRunner.class)
@JarTestSuiteRunner.ExcludeClasses({
        AnnotationDetectorTest.class,
        ApiDetectorTest.class,
        VersionChecksTest.class,
        CallSuperDetectorTest.class,
        CustomRuleTest.class,
        JavaScriptInterfaceDetectorTest.class,
        LintBazelSuite.class,
        MainTest.class,
        ObjectAnimatorDetectorTest.class,
        SupportAnnotationDetectorTest.class,
        TypoDetectorTest.class,
        TypoLookupTest.class,
        UnpackedNativeCodeDetectorTest.class,
})
public class LintBazelSuite {}
