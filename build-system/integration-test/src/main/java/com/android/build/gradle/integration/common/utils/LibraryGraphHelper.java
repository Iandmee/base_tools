/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.build.gradle.integration.common.utils;

import com.android.annotations.NonNull;
import com.android.build.gradle.integration.common.fixture.GetAndroidModelAction.ModelContainer;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.level2.GraphItem;
import com.android.builder.model.level2.Library;
import com.android.builder.model.level2.LibraryGraph;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Helper for library graph
 */
public class LibraryGraphHelper {

    public enum Property {
        ADDRESS,
        GRADLE_PATH,
        COORDINATES,
        VARIANT,
    }

    public enum Type {
        JAVA(Library.LIBRARY_JAVA),
        ANDROID(Library.LIBRARY_ANDROID),
        MODULE(Library.LIBRARY_MODULE);

        private final int value;
        Type(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }
    }

    public enum Filter {
        PROVIDED, NOT_PROVIDED,
        SKIPPED, NOT_SKIPPED
    }

    @NonNull
    private final ModelContainer<AndroidProject> container;

    public LibraryGraphHelper(@NonNull ModelContainer<AndroidProject> container) {
        this.container = container;
    }

    public Items on(LibraryGraph libraryGraph) {
        return new Items(libraryGraph, libraryGraph.getDependencies());
    }

    public class Items {
        private final LibraryGraph libraryGraph;
        private final List<GraphItem> items;

        private Items(LibraryGraph libraryGraph, List<GraphItem> items) {
            this.libraryGraph = libraryGraph;
            this.items = items;
        }

        public List<GraphItem> asList() {
            return items;
        }

        public GraphItem asSingleGraphItem() {
            return Iterables.getOnlyElement(items);
        }

        public Items getTransitiveFromSingleItem() {
            return new Items(libraryGraph, asSingleGraphItem().getDependencies());
        }

        public List<Library> asLibraries() {
            Map<String, Library> map = container.getGlobalLibraryMap().getLibraries();
            return items.stream()
                    .map(item -> map.get(item.getArtifactAddress()))
                    .collect(Collectors.toList());
        }

        public Library asSingleLibrary() {
            Map<String, Library> map = container.getGlobalLibraryMap().getLibraries();
            return map.get(asSingleGraphItem().getArtifactAddress());
        }

        public Items withTransitive() {
            // flatten the graph
            Set<GraphItem> flatDependencies = new LinkedHashSet<>();
            computeFlatList(items, flatDependencies);

            return new Items(libraryGraph, Lists.reverse(new ArrayList<>(flatDependencies)));
        }

        public Items withType(Type type) {
            Map<String, Library> map = container.getGlobalLibraryMap().getLibraries();
            return new Items(libraryGraph, items.stream()
                    .filter(item -> map.get(item.getArtifactAddress()).getType() == type.getValue())
                    .collect(Collectors.toList()));
        }

        public Items filter(Filter type) {
            if (libraryGraph == null) {
                throw new RuntimeException("Can't filter on LibraryGraphHelper with no LibraryGraph");
            }
            return new Items(libraryGraph, items.stream()
                    .filter(item -> {
                        switch (type) {
                            case PROVIDED:
                                return libraryGraph.getProvidedLibraries().contains(item.getArtifactAddress());
                            case NOT_PROVIDED:
                                return !libraryGraph.getProvidedLibraries().contains(item.getArtifactAddress());
                            case SKIPPED:
                                return libraryGraph.getSkippedLibraries().contains(item.getArtifactAddress());
                            case NOT_SKIPPED:
                                return !libraryGraph.getSkippedLibraries().contains(item.getArtifactAddress());
                            default:
                                throw new RuntimeException("Unsupported Filter Type");
                        }
                    })
                    .collect(Collectors.toList()));
        }

        public List<String> mapTo(Property property) {
            Map<String, Library> map = container.getGlobalLibraryMap().getLibraries();
            return items.stream()
                    .map(item -> {
                        switch (property) {
                            case ADDRESS:
                                return item.getArtifactAddress();
                            case GRADLE_PATH:
                                return map.get(item.getArtifactAddress()).getProjectPath();
                            case COORDINATES:
                                return item.getArtifactAddress();
                            case VARIANT:
                                String variant = map.get(item.getArtifactAddress()).getVariant();
                                return variant != null ? variant : null;
                            default:
                                throw new RuntimeException("Unknown LibraryGraphHelper.Property");
                        }
                    })
                    .collect(Collectors.toList());
        }
    }


    /**
     * Resolves a given list of libraries, finds out if they depend on other libraries, and
     * returns a flat list of all the direct and indirect dependencies in the reverse order
     *
     * @param items     the dependency nodes to flatten.
     * @param outFlatDependencies where to store all the dependencies
     */
    private static void computeFlatList(
            @NonNull List<GraphItem> items,
            @NonNull Set<GraphItem> outFlatDependencies) {
        // loop in the inverse order to resolve dependencies on the libraries, so that if a library
        // is required by two higher level libraries it can be inserted in the correct place
        // (behind both higher level libraries).
        // For instance:
        //        A
        //       / \
        //      B   C
        //       \ /
        //        D
        //
        // Must give: A B C D
        // So that both B and C override D (and B overrides C)
        for (int i = items.size() - 1; i >= 0;  i--) {
            GraphItem item = items.get(i);

            // flatten the dependencies for those libraries
            // never pass the tested local jars as this is guaranteed to be null beyond the
            // direct dependencies.
            computeFlatList(item.getDependencies(), outFlatDependencies);

            // and add the current one (if needed) in back, the list will get reversed and it
            // will get moved to the front (higher priority)
            if (!outFlatDependencies.contains(item)) {
                outFlatDependencies.add(item);
            }
        }
    }
}
