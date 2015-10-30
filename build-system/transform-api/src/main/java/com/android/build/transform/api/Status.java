/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.build.transform.api;

import com.google.common.annotations.Beta;

/**
 * The file changed status for incremental execution.
 * <p/>
 * <strong>This API is non final and is subject to change. We are looking for feedback, and will
 * attempt to stabilize it in the 1.6 time frame.</strong>
 */
@Beta
public enum Status {
    /**
     * The file was not changed since the last build.
     */
    NOTCHANGED,
    /**
     * The file was added since the last build.
     */
    ADDED,
    /**
     * The file was modified since the last build.
     */
    CHANGED,
    /**
     * The file was removed since the last build.
     */
    REMOVED
}
