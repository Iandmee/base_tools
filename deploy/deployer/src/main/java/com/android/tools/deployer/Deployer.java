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

import com.android.tools.deploy.proto.Deploy;
import com.android.tools.deployer.model.Apk;
import com.android.tools.deployer.model.ApkEntry;
import com.android.tools.deployer.model.DexClass;
import com.android.tools.deployer.model.FileDiff;
import com.android.tools.deployer.tasks.TaskRunner;
import com.android.tools.deployer.tasks.TaskRunner.Task;
import com.google.common.io.ByteStreams;
import com.google.protobuf.ByteString;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class Deployer {

    public static final String BASE_DIRECTORY = "/data/local/tmp/.studio";
    public static final String APK_DIRECTORY = BASE_DIRECTORY + "/apks";
    public static final String INSTALLER_DIRECTORY = BASE_DIRECTORY + "/bin";

    private final AdbClient adb;
    private final ApkFileDatabase db;
    private final Installer installer;
    private final TaskRunner runner;

    public Deployer(AdbClient adb, ApkFileDatabase db, TaskRunner runner, Installer installer) {
        this.adb = adb;
        this.db = db;
        this.runner = runner;
        this.installer = installer;
    }

    /**
     * Installs the given apks. This method will register the APKs in the database for subsequent
     * swaps
     */
    public void install(List<String> apks) throws IOException {
        try (Trace ignored = Trace.begin("install")) {
            // There could be tasks running from the previous cycle that were
            // left to complete in the background. We wait for them to finish
            // So that we don't accumulate them.
            runner.join();

            // Run installation on the current thread.
            adb.installMultiple(apks, true);

            // Parse the apks
            Task<List<ApkEntry>> entries =
                    runner.submit("parsePaths", new ApkParser()::parsePaths, runner.submit(apks));

            // Update the database
            runner.submit("computeClassChecksums", this::computeClassChecksums, entries);
        }
    }

    public void codeSwap(String packageName, List<String> apks, DebuggerCodeSwapAdapter adapter)
            throws DeployerException {
        Trace.begin("codeSwap");
        swap(packageName, apks, false /* Restart Activity */, adapter);
        Trace.end();
    }

    public void fullSwap(String packageName, List<String> apks) throws DeployerException {
        Trace.begin("fullSwap");
        swap(packageName, apks, true /* Restart Activity */, null);
        Trace.end();
    }

    private void swap(
            String packageName,
            List<String> paths,
            boolean restart,
            DebuggerCodeSwapAdapter adapter)
            throws DeployerException {
        // Get the list of files from the local apks
        List<ApkEntry> newFiles = new ApkParser().parsePaths(paths);

        // Get the list of files from the installed app
        List<ApkEntry> dumps = new ApkDumper(installer).dump(packageName);

        // Calculate the difference between them
        List<FileDiff> diffs = new ApkDiffer().diff(dumps, newFiles);

        // Push the apks to device and get the remote paths
        List<String> apkPaths = pushApks(newFiles);

        // Obtain the process names from the local apks
        Set<String> processNames = extractProcessNames(newFiles);

        // Verify the changes are swappable and get only the dexes that we can change
        List<FileDiff> dexDiffs = verify(diffs, restart);

        // Compare the local vs remote dex files.
        List<DexClass> toSwap = compare(dexDiffs);

        // Update the database with the entire new apk. In the normal case this should
        // be a no-op because the dexes that were modified were extracted at comparison time.
        // However if the compare task doesn't get to execute we still update the database.
        computeClassChecksums(newFiles);

        // Temporary disables all the breakpoints if debuggers are attached.
        disableBreakPoints(adapter);

        // Builds the Request Protocol Buffer.
        Deploy.SwapRequest request =
                buildSwapRequest(packageName, restart, apkPaths, processNames, toSwap, adapter);

        // Send Request to agent
        sendAgentSwapRequest(request);

        // Send Request to debugger
        sendDebuggerSwapRequest(request, adapter);

        // Restores all breakpoints if debuggers are attached.
        enableBreakPoints(adapter);
    }

    private List<FileDiff> verify(List<FileDiff> diffs, boolean restart) throws DeployerException {
        return new SwapVerifier().verify(diffs, restart);
    }

    private boolean computeClassChecksums(List<ApkEntry> newFiles) throws DeployerException {
        try (Trace ignored = Trace.begin("update")) {
            Function<ApkEntry, byte[]> dexProvider = dexProvider();
            for (ApkEntry file : newFiles) {
                if (file.name.endsWith(".dex")) {
                    extractClasses(file, dexProvider, null);
                }
            }
            return true;
        }
    }

    private List<DexClass> compare(List<FileDiff> dexDiffs) throws DeployerException {
        try (Trace ignored = Trace.begin("compare")) {
            List<DexClass> toSwap = new ArrayList<>();
            for (FileDiff diff : dexDiffs) {
                List<DexClass> oldClasses = extractClasses(diff.oldFile, null, null);
                Map<String, Long> checksums = new HashMap<>();
                for (DexClass clz : oldClasses) {
                    checksums.put(clz.name, clz.checksum);
                }
                // Memory optimization to discard not needed code
                Predicate<DexClass> needsCode =
                        (DexClass clz) -> {
                            Long oldChecksum = checksums.get(clz.name);
                            return oldChecksum != null && clz.checksum != oldChecksum;
                        };

                Function<ApkEntry, byte[]> dexProvider = dexProvider();
                List<DexClass> newClasses = extractClasses(diff.newFile, dexProvider, needsCode);
                toSwap.addAll(
                        newClasses
                                .stream()
                                .filter(c -> c.code != null)
                                .collect(Collectors.toList()));
            }
            return toSwap;
        }
    }

    private Function<ApkEntry, byte[]> dexProvider() {
        // TODO Check if opening the file several times matters
        return (ApkEntry dex) -> {
            try (ZipFile file = new ZipFile(dex.apk.path)) {
                ZipEntry entry = file.getEntry(dex.name);
                return ByteStreams.toByteArray(file.getInputStream(entry));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        };
    }

    private Deploy.SwapRequest buildSwapRequest(
            String packageName,
            boolean restart,
            List<String> apkPaths,
            Set<String> processNames,
            List<DexClass> classes,
            DebuggerCodeSwapAdapter adapter) {
        Deploy.SwapRequest.Builder request = Deploy.SwapRequest.newBuilder();
        request.setPackageName(packageName);
        request.setRestartActivity(restart);
        for (DexClass clz : classes) {
            request.addClasses(
                    Deploy.ClassDef.newBuilder()
                            .setName(clz.name)
                            .setDex(ByteString.copyFrom(clz.code)));
        }

        request.addAllApks(apkPaths);
        request.addAllProcessNames(processNames);

        if (adapter != null) {
            for (int pid : adapter.getPids()) {
                request.addSkipProcessIds(pid);
            }
        }

        return request.build();
    }

    private void sendAgentSwapRequest(Deploy.SwapRequest request) throws DeployerException {
        ClassRedefiner redefiner = new InstallerBasedClassRedefiner(installer);
        Deploy.SwapResponse swapResponse = redefiner.redefine(request);
        if (swapResponse.getStatus() != Deploy.SwapResponse.Status.OK) {
            throw new DeployerException(DeployerException.Error.REDEFINER_ERROR, "Swap failed");
        }
    }

    private void sendDebuggerSwapRequest(
            Deploy.SwapRequest request, DebuggerCodeSwapAdapter adapter) {
        if (adapter != null) {
            adapter.setRequest(request);
            adapter.performSwap();
        }
    }

    private Set<String> extractProcessNames(List<ApkEntry> newFiles) {
        Set<String> processNames = new HashSet<>();
        Set<Apk> apks = new HashSet<>();
        for (ApkEntry file : newFiles) {
            apks.add(file.apk);
        }
        for (Apk apk : apks) {
            processNames.addAll(apk.processes);
        }
        return processNames;
    }

    private List<DexClass> extractClasses(
            ApkEntry dex, Function<ApkEntry, byte[]> dexProvider, Predicate<DexClass> needsCode)
            throws DeployerException {
        // Try a cached version
        List<DexClass> classes = db.getClasses(dex);
        if (classes.isEmpty() || needsCode != null) {
            if (dexProvider == null) {
                throw new DeployerException(
                        DeployerException.Error.REMOTE_APK_NOT_FOUND_ON_DB,
                        "Cannot generate classes for unknown dex");
            }
            byte[] code = dexProvider.apply(dex);
            classes = new DexSplitter().split(dex, code, needsCode);
            db.addClasses(classes);
        }
        return classes;
    }

    private List<String> pushApks(List<ApkEntry> fullApks) throws DeployerException {
        try {
            List<String> apkPaths = new ArrayList<>();
            adb.shell(
                    new String[] {"rm", "-r", APK_DIRECTORY, ";", "mkdir", "-p", APK_DIRECTORY},
                    null);
            Set<Apk> apks = new HashSet<>();
            for (ApkEntry file : fullApks) {
                apks.add(file.apk);
            }
            for (Apk apk : apks) {
                String target = APK_DIRECTORY + "/" + Paths.get(apk.path).getFileName();
                adb.push(apk.path, target);
                apkPaths.add(target);
            }
            return apkPaths;
        } catch (IOException e) {
            throw new DeployerException(DeployerException.Error.ERROR_PUSHING_APK, e);
        }
    }

    private void disableBreakPoints(DebuggerCodeSwapAdapter adapter) {
        if (adapter != null) {
            adapter.disableBreakPoints();
        }
    }

    /** Re-enables breakpoint. Should depend on code swap to be completely finished */
    private void enableBreakPoints(DebuggerCodeSwapAdapter adapter) {
        if (adapter != null) {
            adapter.enableBreakPoints();
        }
    }
}
