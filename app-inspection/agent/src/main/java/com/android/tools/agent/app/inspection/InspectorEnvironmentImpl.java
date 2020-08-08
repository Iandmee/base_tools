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

package com.android.tools.agent.app.inspection;

import androidx.annotation.NonNull;
import androidx.inspection.ArtToolInterface;
import androidx.inspection.InspectorEnvironment;
import androidx.inspection.InspectorExecutors;
import java.util.Arrays;
import java.util.List;

class InspectorEnvironmentImpl implements InspectorEnvironment {
    private final long mAppInspectionServicePtr;
    private final String inspectorId;
    private final InspectorExecutors mExecutors;

    InspectorEnvironmentImpl(
            long mAppInspectionServicePtr,
            @NonNull String inspectorId,
            @NonNull InspectorExecutors executors) {
        this.mAppInspectionServicePtr = mAppInspectionServicePtr;
        this.inspectorId = inspectorId;
        mExecutors = executors;
    }

    @Override
    public <T> List<T> findInstances(Class<T> clazz) {
        return Arrays.asList(nativeFindInstances(mAppInspectionServicePtr, clazz));
    }

    @Override
    public void registerEntryHook(
            Class<?> originClass, String originMethod, ArtToolInterface.EntryHook entryHook) {
        AppInspectionService.addEntryHook(inspectorId, originClass, originMethod, entryHook);
    }

    @Override
    public <T> void registerExitHook(
            Class<?> originClass, String originMethod, ArtToolInterface.ExitHook<T> exitHook) {
        AppInspectionService.addExitHook(inspectorId, originClass, originMethod, exitHook);
    }

    @NonNull
    @Override
    public InspectorExecutors executors() {
        return mExecutors;
    }

    private static native <T> T[] nativeFindInstances(long servicePtr, Class<T> clazz);
}

