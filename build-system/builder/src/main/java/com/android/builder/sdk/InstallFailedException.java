/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.builder.sdk;

import com.android.annotations.NonNull;
import com.android.repository.api.RemotePackage;
import com.google.common.collect.ImmutableList;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Exception thrown when an SDK component installation fails for another reason than a licence not
 * being accepted.
 *
 * @see LicenceNotAcceptedException
 */
public final class InstallFailedException extends Exception {
    private final ImmutableList<RemotePackage> failedPackages;

    public InstallFailedException(
            @NonNull Path sdkLocation, @NonNull List<RemotePackage> failedPackages) {
        super(getMessage(failedPackages, sdkLocation));
        this.failedPackages = ImmutableList.copyOf(failedPackages);
    }

    @NonNull
    public ImmutableList<RemotePackage> getAffectedPackages() {
        return failedPackages;
    }

    @NonNull
    private static String getMessage(
            @NonNull List<RemotePackage> affectedPackages, @NonNull Path sdkLocation) {
        StringBuilder message =
                new StringBuilder("Failed to install the following SDK components:\n");
        for (RemotePackage affectedPackage : affectedPackages) {
            message.append("    ")
                    .append(affectedPackage.getPath())
                    .append(' ')
                    .append(affectedPackage.getDisplayName())
                    .append('\n');
        }

        // Use NIO to check permissions, which seems to work across platform better.
        if (!Files.isWritable(sdkLocation)) {
            message.append("The SDK directory is not writable (")
                    .append(sdkLocation.toString())
                    .append(")\n");
        } else {
            message.append(
                    "Install the missing components using the SDK manager in Android Studio.\n");
        }
        return message.toString();
    }
}
