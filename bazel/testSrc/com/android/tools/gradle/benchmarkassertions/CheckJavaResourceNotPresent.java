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

package com.android.tools.gradle.benchmarkassertions;

import static com.android.testutils.truth.ZipFileSubject.assertThat;

import java.nio.file.Path;

@SuppressWarnings("unused")
public class CheckJavaResourceNotPresent implements BenchmarkProjectAssertion {

    private final String apk;
    private final String name;
    private final String apkFromStudio;

    public CheckJavaResourceNotPresent(String apk, String apkFromStudio, String name) {
        this.apk = apk;
        this.apkFromStudio = apkFromStudio;
        this.name = name;
    }

    @Override
    public void checkProject(Path projectRoot, boolean fromStudio) throws Exception {
        assertThat(
                projectRoot.resolve(fromStudio ? this.apkFromStudio : this.apk),
                it -> {
                    it.doesNotContain(name);
                });
    }
}
