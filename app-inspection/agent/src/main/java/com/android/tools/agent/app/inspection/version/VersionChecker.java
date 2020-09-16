/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.tools.agent.app.inspection.version;

/**
 * Checks the minVersion provided by Android Studio against the version file embedded in the app's
 * APK META-INF.
 */
public class VersionChecker {

    private final VersionFileReader reader = new VersionFileReader();

    /**
     * Compares the version of the library against the provided min_version string.
     *
     * <p>The version of the library is found inside a version file in the APK's META-INF directory.
     *
     * @param targetInfo which contains the minVersion and the versionFile being targeted.
     * @return a VersionChecker.Result object containing the result of the check and any errors.
     */
    public VersionCheckerResult checkVersion(VersionTargetInfo targetInfo) {
        String versionFile = targetInfo.versionFileName;
        String minVersionString = targetInfo.minVersion;
        VersionFileReader.Result readResult = reader.readVersionFile(versionFile);
        switch (readResult.status) {
            case NOT_FOUND:
                return new VersionCheckerResult(
                        VersionCheckerResult.Status.NOT_FOUND,
                        "Failed to find version file " + versionFile,
                        versionFile,
                        null);
            case READ_ERROR:
                return new VersionCheckerResult(
                        VersionCheckerResult.Status.INCOMPATIBLE,
                        "Failed to read version file " + versionFile,
                        versionFile,
                        null);
        }
        Version version = Version.parseOrNull(readResult.versionString);
        if (version == null) {
            return new VersionCheckerResult(
                    VersionCheckerResult.Status.INCOMPATIBLE,
                    "Failed to parse version string "
                            + readResult.versionString
                            + " which is in "
                            + versionFile,
                    versionFile,
                    readResult.versionString);
        }
        Version minVersion = Version.parseOrNull(minVersionString);
        if (minVersion == null) {
            return new VersionCheckerResult(
                    VersionCheckerResult.Status.ERROR,
                    "Failed to parse provided min version " + minVersionString,
                    versionFile,
                    readResult.versionString);
        }
        if (version.compareTo(minVersion) >= 0) {
            return new VersionCheckerResult(
                    VersionCheckerResult.Status.COMPATIBLE,
                    null,
                    versionFile,
                    readResult.versionString);
        } else {
            return new VersionCheckerResult(
                    VersionCheckerResult.Status.INCOMPATIBLE,
                    "Library version "
                            + readResult.versionString
                            + " does not satisfy the inspector's min version requirement "
                            + minVersionString,
                    versionFile,
                    readResult.versionString);
        }
    }

}
