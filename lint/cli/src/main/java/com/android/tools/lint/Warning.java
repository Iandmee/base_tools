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

package com.android.tools.lint;

import com.android.annotations.NonNull;
import com.android.tools.lint.client.api.LintClient;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Project;
import com.android.tools.lint.detector.api.Severity;

import java.io.File;

/**
 * A {@link Warning} represents a specific warning that a {@link LintClient}
 * has been told about. The context stores these as they are reported into a
 * list of warnings such that it can sort them all before presenting them all at
 * the end.
 */
public class Warning implements Comparable<Warning> {
    public final Issue issue;
    public final String message;
    public final Severity severity;
    public final Object data;
    public final Project project;
    public Location location;
    public File file;
    public String path;
    public int line = -1;
    public int offset = -1;
    public String errorLine;
    public String fileContents;

    public Warning(Issue issue, String message, Severity severity, Project project, Object data) {
        this.issue = issue;
        this.message = message;
        this.severity = severity;
        this.project = project;
        this.data = data;
    }

    // ---- Implements Comparable<Warning> ----
    @SuppressWarnings({"VariableNotUsedInsideIf", "ConstantConditions"})
    @Override
    public int compareTo(@NonNull Warning other) {
        // Sort by category, then by priority, then by id,
        // then by file, then by line
        int categoryDelta = issue.getCategory().compareTo(other.issue.getCategory());
        if (categoryDelta != 0) {
            return categoryDelta;
        }
        // DECREASING priority order
        int priorityDelta = other.issue.getPriority() - issue.getPriority();
        if (priorityDelta != 0) {
            return priorityDelta;
        }
        String id1 = issue.getId();
        String id2 = other.issue.getId();
        assert id1 != null;
        assert id2 != null;
        int idDelta = id1.compareTo(id2);
        if (idDelta != 0) {
            return idDelta;
        }
        if (file != null) {
            if (other.file != null) {
                int fileDelta = file.getName().compareTo(
                        other.file.getName());
                if (fileDelta != 0) {
                    return fileDelta;
                }
            } else {
                return -1;
            }
        } else if (other.file != null) {
            return 1;
        }
        if (line != other.line) {
            return line - other.line;
        }

        return message.compareTo(other.message);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        Warning warning = (Warning) o;

        if (line != warning.line) {
            return false;
        }
        if (file != null ? !file.equals(warning.file) : warning.file != null) {
            return false;
        }
        if (!issue.getCategory().equals(warning.issue.getCategory())) {
            return false;
        }
        if (issue.getPriority() != warning.issue.getPriority()) {
            return false;
        }
        if (!issue.getId().equals(warning.issue.getId())) {
            return false;
        }
        if (!message.equals(warning.message)) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = message.hashCode();
        result = 31 * result + (file != null ? file.hashCode() : 0);
        return result;
    }
}
