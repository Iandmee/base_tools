/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.tools.deployer;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Arrays;

public class ShellRunner {
    private String[] command;
    private boolean readOutput;

    ShellRunner(String[] command, boolean readOuput) {
        this.command = command;
        this.readOutput = readOuput;
    }

    void run(LineProcessor lineProcessor) throws DeployerException {
        try {
            ProcessBuilder processBuilder = new ProcessBuilder();
            if (!readOutput) {
                processBuilder = processBuilder.inheritIO();
            }
            Process process = processBuilder.command(command).start();
            int status = process.waitFor();
            BufferedReader reader =
                    new BufferedReader(new InputStreamReader(process.getInputStream()));

            String line;
            while ((line = reader.readLine()) != null) {
                lineProcessor.processLine(line);
            }

            if (status != 0) {
                throw new DeployerException(
                        "Command failed:" + String.join(" ", command) + ", status:" + status);
            }
        } catch (InterruptedException | IOException e) {
            throw new DeployerException(
                    "Unable to run cmd: '" + Arrays.toString(command) + "'.", e);
        }
    }

    interface LineProcessor {
        void processLine(String line);
    }
}
