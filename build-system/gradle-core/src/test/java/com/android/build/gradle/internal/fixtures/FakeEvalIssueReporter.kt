/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.build.gradle.internal.fixtures

import com.android.build.gradle.internal.ide.SyncIssueImpl
import com.android.builder.errors.EvalIssueReporter
import com.android.builder.model.SyncIssue

class FakeEvalIssueReporter: EvalIssueReporter {

    val messages = mutableListOf<String>()

    override fun reportIssue(type: EvalIssueReporter.Type,
            severity: EvalIssueReporter.Severity,
            msg: String,
            data: String?): SyncIssue {
        messages.add(msg)
        return SyncIssueImpl(type, severity, data, msg, listOf())
    }
}