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

package com.android.signflinger;

import java.io.File;
import java.nio.file.Files;
import java.util.Arrays;
import org.junit.Rule;
import org.junit.Test;

public class BenchmarkTest {

    @Rule public final Workspace workspace = new Workspace();

    @Test
    public void run() throws Exception {
        File androidManifest = workspace.getDummyAndroidManifest();
        File file = workspace.createZip(21, 1 << 20, "apk-12MiB.apk", androidManifest);
        signAndVerify(file);

        file = workspace.createZip(41, 1 << 20, "apk-42MiB.apk", androidManifest);
        signAndVerify(file);
    }

    private void signAndVerify(File file) throws Exception {
        for (SignerConfig signerConfig : Signers.getAll(workspace)) {
            long times[] = new long[3];
            for (int i = 0; i < times.length; i++) {
                long startTime = System.nanoTime();
                V2Signer.sign(file, signerConfig);
                times[i] = System.nanoTime() - startTime;
            }
            Utils.verifyApk(file);
            Arrays.sort(times);
            long timeMs = times[times.length / 2];
            long fileSizeMiB = Files.size(file.toPath()) / (1 << 20);
            String message =
                    String.format(
                            "V2 signed %d MiB in %3d ms (%s-%s)",
                            fileSizeMiB, timeMs, signerConfig.getAlgo(), signerConfig.getSubType());
            System.out.println(message);
        }
    }
}
