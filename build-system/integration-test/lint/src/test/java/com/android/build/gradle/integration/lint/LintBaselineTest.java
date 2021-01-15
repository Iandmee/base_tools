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

package com.android.build.gradle.integration.lint;

import static com.android.testutils.truth.PathSubject.assertThat;
import static com.google.common.truth.Truth.assertThat;

import com.android.build.gradle.integration.common.fixture.GradleBuildResult;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.truth.ScannerSubject;
import java.io.File;
import kotlin.io.FilesKt;
import kotlin.text.Charsets;
import org.junit.Rule;
import org.junit.Test;

/**
 * Test for generating baselines for all variants, making sure we don't accidentally merge resources
 * files in different resource qualifiers; https://issuetracker.google.com/131073349
 */
public class LintBaselineTest {

    @Rule public final GradleTestProject project =
            GradleTestProject.builder()
                    .fromTestProject("lintBaseline")
                    .withConfigurationCacheMaxProblems(23)
                    .create();

    @Test
    public void checkMerging() throws Exception {
        GradleBuildResult result = project.executor().expectFailure().run(":app:lint");
        ScannerSubject.assertThat(result.getStderr()).contains("Created baseline file");

        File baselineFile =
                new File(project.getSubproject("app").getProjectDir(), "lint-baseline.xml");
        assertThat(baselineFile).exists();
        String baseline = FilesKt.readText(baselineFile, Charsets.UTF_8);
        assertThat(baseline)
                .contains(
                        ""
                                + "    <issue\n"
                                + "        id=\"UselessLeaf\"\n"
                                + "        message=\"This `LinearLayout` view is useless (no children, no `background`, no `id`, no `style`)\"\n"
                                + "        errorLine1=\"    &lt;LinearLayout android:layout_width=&quot;match_parent&quot; android:layout_height=&quot;match_parent&quot;>&lt;/LinearLayout>\"\n"
                                + "        errorLine2=\"     ~~~~~~~~~~~~\">\n"
                                + "        <location\n"
                                + "            file=\"src/main/res/layout-land/my_layout.xml\"\n"
                                + "            line=\"7\"\n"
                                + "            column=\"6\"/>\n"
                                + "    </issue>\n"
                                + "\n"
                                + "    <issue\n"
                                + "        id=\"UselessLeaf\"\n"
                                + "        message=\"This `LinearLayout` view is useless (no children, no `background`, no `id`, no `style`)\"\n"
                                + "        errorLine1=\"    &lt;LinearLayout android:layout_width=&quot;match_parent&quot; android:layout_height=&quot;match_parent&quot;>&lt;/LinearLayout>\"\n"
                                + "        errorLine2=\"     ~~~~~~~~~~~~\">\n"
                                + "        <location\n"
                                + "            file=\"src/main/res/layout/my_layout.xml\"\n"
                                + "            line=\"7\"\n"
                                + "            column=\"6\"/>\n"
                                + "    </issue>\n"
                                + "\n");
        // Check the written baseline means that a subsequent lint invocation passes.
        project.executor().run("clean", ":app:lint");
    }
}
