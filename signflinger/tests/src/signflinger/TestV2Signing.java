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

package signflinger;

import com.android.zipflinger.FileSource;
import java.io.File;
import org.junit.Test;

public class TestV2Signing {

    @Test
    public void testV2SignNormalApk() throws Exception {
        File file = Utils.getTestOuputFile("apk-22MiB.apk");
        Utils.createZip(1, 12_000_000, file);
        v2Sign(file);
    }

    @Test
    public void testV2SignBigApk() throws Exception {
        File file = Utils.getTestOuputFile("apk-42MiB.apk");
        Utils.createZip(1, 42_000_000, file);
        v2Sign(file);
    }

    private void v2Sign(File file) throws Exception {
        for (Signer signer : Signers.signers) {
            SignResult result = V2Signer.sign(file, signer);
            Utils.verify(result.file);
        }
    }

    @Test
    public void testBenchmarkAddAndV2sign() throws Exception {
        File file = Utils.getTestOuputFile("apk-42MiB-400files.apk");
        Utils.createZip(400, 120_000, file);

        SignerConfig signerConfig = Signers.getDefaultRSA();
        SignedApkOptions.Builder builder =
                new SignedApkOptions.Builder()
                        .setV2Enabled(true)
                        .setV1Enabled(false)
                        .setPrivateKey(signerConfig.privateKey)
                        .setCertificates(signerConfig.certificates)
                        .setExecutor(Utils.createExecutor());
        SignedApkOptions options = builder.build();

        long start = System.nanoTime();
        SignedApk signedApk = new SignedApk(file, options);
        signedApk.add(new FileSource(Utils.getFile("test1.txt"), "test1", 1));
        signedApk.close();
        long totalTime = (System.nanoTime() - start) / 1000000;
        Utils.verify(file);
        System.out.println("Adding and Signing time=" + totalTime);
    }
}
