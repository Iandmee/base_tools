/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.tools.lint.checks;

import com.android.annotations.Nullable;
import com.android.testutils.TestUtils;
import com.android.tools.lint.checks.infrastructure.LintDetectorTest;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Issue;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public abstract class AbstractCheckTest extends LintDetectorTest {
    @Override
    protected boolean allowAndroidBuildEnvironment() {
        return false;
    }

    @Override
    protected List<Issue> getIssues() {
        List<Issue> issues = new ArrayList<>();
        Class<? extends Detector> detectorClass = getDetectorInstance().getClass();
        // Get the list of issues from the registry and filter out others, to make sure
        // issues are properly registered
        List<Issue> candidates = new BuiltinIssueRegistry().getIssues();
        for (Issue issue : candidates) {
            if (issue.getImplementation().getDetectorClass() == detectorClass) {
                issues.add(issue);
            }
        }

        return issues;
    }

    @Override
    protected InputStream getTestResource(String relativePath, boolean expectExists) {
        String path = "data" + File.separator + relativePath;
        InputStream stream = AbstractCheckTest.class.getResourceAsStream(path);
        if (stream == null) {
            File root = TestUtils.getWorkspaceRoot();
            String pkg = AbstractCheckTest.class.getName();
            pkg = pkg.substring(0, pkg.lastIndexOf('.'));
            File f = new File(root,
                "tools/base/lint/libs/lint-tests/src/test/java/".replace('/', File.separatorChar)
                            + pkg.replace('.', File.separatorChar)
                            + File.separatorChar + path);
            if (f.exists()) {
                try {
                    return new BufferedInputStream(new FileInputStream(f));
                } catch (FileNotFoundException e) {
                    stream = null;
                    if (expectExists) {
                        fail("Could not find file " + relativePath);
                    }
                }
            }
        }
        if (!expectExists && stream == null) {
            return null;
        }
        return stream;
    }

    @Override
    protected TestLintClient createClient() {
        return new ToolsBaseTestLintClient();
    }

    /**
     * Overrides TestLintClient to use the checked-in SDK that is available in the tools/base
     * repo. The "real" TestLintClient is a public utility for writing lint tests, so it cannot
     * make assumptions specific to tools/base.
     */
    protected class ToolsBaseTestLintClient extends TestLintClient {
        @Nullable
        @Override
        public File getSdkHome() {
            return TestUtils.getSdk();
        }
    }
}
