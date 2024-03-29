/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.jdwptracer;

import com.android.annotations.NonNull;
import java.util.ArrayList;
import java.util.List;

class AssertableLog extends Log {

    private List<String> warnings = new ArrayList<>();

    @Override
    public void warn(@NonNull String message, Throwable t) {
        super.warn(message, t);
        warnings.add(message);
    }

    public List<String> getWarnings() {
        return warnings;
    }
}
