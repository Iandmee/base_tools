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

package com.android.ddmlib;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Output receiver for "pm install package.apk" command line.
 *
 * <p>Use a combination of {@link #isSuccessfullyCompleted()} and {@link #getErrorMessage()} to
 * decide if the installation was successful and what was the error.
 */
public final class InstallReceiver extends MultiLineReceiver {

    private static final String SUCCESS_OUTPUT = "Success"; //$NON-NLS-1$
    private static final Pattern FAILURE_PATTERN =
            Pattern.compile("Failure\\s+\\[(.*)\\]"); //$NON-NLS-1$

    private String mErrorMessage = null;
    /**
     * Track whether the installation was actually successful, regardless of if we get an output
     * from the command or not.
     */
    private boolean mSuccessfullyCompleted = false;

    public InstallReceiver() {}

    @Override
    public void processNewLines(String[] lines) {
        for (String line : lines) {
            if (!line.isEmpty()) {
                if (line.startsWith(SUCCESS_OUTPUT)) {
                    mSuccessfullyCompleted = true;
                    mErrorMessage = null;
                } else {
                    Matcher m = FAILURE_PATTERN.matcher(line);
                    if (m.matches()) {
                        mErrorMessage = m.group(1);
                    } else {
                        mErrorMessage = "Unknown failure (" + line + ")";
                    }
                }
            }
        }
    }

    @Override
    public boolean isCancelled() {
        return false;
    }

    /**
     * Returns the error message from the installation. Returns null if it was successful or if a
     * timeout occurred.
     */
    public String getErrorMessage() {
        return mErrorMessage;
    }

    /**
     * Returns true if the installation was fully successful. If {@link #getErrorMessage()} returns
     * null and {@link #isSuccessfullyCompleted()} returns false, a timeout on device side was most
     * likely encountered.
     */
    public boolean isSuccessfullyCompleted() {
        return mSuccessfullyCompleted;
    }
}
