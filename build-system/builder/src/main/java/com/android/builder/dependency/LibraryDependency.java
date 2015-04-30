/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.builder.dependency;

import com.android.annotations.NonNull;
import com.android.builder.model.AndroidLibrary;

import java.util.Collection;
import java.util.List;

/**
 * Represents a dependency on a Library Project.
 */
public interface LibraryDependency extends AndroidLibrary, ManifestDependency, SymbolFileProvider {

    /**
     * Returns the direct dependency of this dependency. The order is important
     */
    @NonNull
    List<LibraryDependency> getDependencies();

    /**
     * Returns the collection of local Jar files that are included in the dependency.
     * @return a list of JarDependency. May be empty but not null.
     */
    @NonNull
    Collection<JarDependency> getLocalDependencies();

    /**
     * Returns whether the library is considered optional, meaning that it could not
     * be present in the final APK.
     *
     * If the library is optional, then it'll get skipped from resource merging inside other
     * libraries.
     */
    @Override
    boolean isOptional();
}
