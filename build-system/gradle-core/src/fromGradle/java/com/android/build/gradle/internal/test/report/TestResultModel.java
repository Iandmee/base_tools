/*
 * Copyright 2011 the original author or authors.
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

package com.android.build.gradle.internal.test.report;

import org.gradle.api.tasks.testing.TestResult;

public abstract class TestResultModel {
    public static final DurationFormatter DURATION_FORMATTER = new DurationFormatter();

    public abstract TestResult.ResultType getResultType();

    public abstract long getDuration();

    public abstract String getTitle();

    public String getFormattedDuration() {
        return DURATION_FORMATTER.format(getDuration());
    }

    public String getStatusClass() {
        switch (getResultType()) {
            case SUCCESS:
                return "success";
            case FAILURE:
                return "failures";
            case SKIPPED:
                return "skipped";
            default:
                throw new IllegalStateException();
        }
    }

    public String getFormattedResultType() {
        switch (getResultType()) {
            case SUCCESS:
                return "passed";
            case FAILURE:
                return "failed";
            case SKIPPED:
                return "ignored";
            default:
                throw new IllegalStateException();
        }
    }
}
