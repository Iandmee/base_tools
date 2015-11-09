/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.sdklib.internal.repository.packages;

import com.android.sdklib.internal.repository.sources.SdkSource;
import com.android.repository.Revision;

/**
 * A mock {@link BuildToolPackage} for testing.
 *
 * By design, this package contains one and only one archive.
 */
public class MockBuildToolPackage extends BuildToolPackage {

    /**
     * Creates a {@link MockBuildToolPackage} with the given revision and hardcoded defaults
     * for everything else.
     * <p/>
     * By design, this creates a package with one and only one archive.
     */
    public MockBuildToolPackage(int revision) {
        this(null /*source*/, revision);
    }

    /**
     * Creates a {@link MockBuildToolPackage} with the given revision and hardcoded defaults
     * for everything else.
     * <p/>
     * By design, this creates a package with one and only one archive.
     */
    public MockBuildToolPackage(SdkSource source, int revision) {
        super(
            source, // source,
            null, // props,
            revision,
            null, // license,
            "desc", // description,
            "url", // descUrl,
            "foo" // archiveOsPath
            );
    }

    /**
     * Creates a {@link MockBuildToolPackage} with the given revision and hardcoded defaults
     * for everything else.
     * <p/>
     * By design, this creates a package with one and only one archive.
     */
    public MockBuildToolPackage(SdkSource source, Revision revision) {
        super(
                source, // source,
                PreciseRevisionPackageTest.createProps(revision), // props,
                revision.getMajor(),
                null, // license,
                "desc", // description,
                "url", // descUrl,
                source == null ? "foo" : null // archiveOsPath, null for remote non-instaled pkgs
                );
    }
}

