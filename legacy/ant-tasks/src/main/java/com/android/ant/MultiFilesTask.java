/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.android.ant;

import com.android.annotations.NonNull;

import org.apache.tools.ant.Project;
import org.apache.tools.ant.types.FileSet;
import org.apache.tools.ant.types.Path;
import org.apache.tools.ant.types.PatternSet.NameEntry;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

class MultiFilesTask extends BuildTypedTask {

    enum DisplayType {
        FOUND, COMPILING, REMOVE_OUTPUT, REMOVE_DEP
    }

    interface SourceProcessor {
        @NonNull Set<String> getSourceFileExtensions();
        void process(@NonNull String filePath, @NonNull String sourceFolder,
                @NonNull List<String> sourceFolders, @NonNull Project taskProject);
        void displayMessage(@NonNull DisplayType type, int count);
        void removedOutput(@NonNull File file);
    }

    protected void processFiles(SourceProcessor processor, List<Path> paths, String genFolder) {

        Project taskProject = getProject();

        Set<String> extensions = processor.getSourceFileExtensions();

        // build a list of all the source folders
        ArrayList<String> sourceFolders = new ArrayList<String>();
        for (Path p : paths) {
            String[] values = p.list();
            if (values != null) {
                sourceFolders.addAll(Arrays.asList(values));
            }
        }

        ArrayList<String> includePatterns = new ArrayList<String>(extensions.size());
        for (String extension : extensions) {
            includePatterns.add("**/*." + extension);
        }

        // gather all the source files from all the source folders.
        Map<String, String> sourceFiles = getFilesByNameEntryFilter(sourceFolders,
                includePatterns.toArray(new String[includePatterns.size()]));
        if (!sourceFiles.isEmpty()) {
            processor.displayMessage(DisplayType.FOUND, sourceFiles.size());
        }

        // go look for all dependency files in the gen folder. This will have all dependency
        // files but we can filter them based on the first pre-req file.
        Iterator<?> depFiles = getFilesByNameEntryFilter(genFolder, "**/*.d");

        // parse all the dep files and keep the ones that are of the proper type and check if
        // they require compilation again.
        Map<String, String> toCompile = new HashMap<String, String>();
        ArrayList<File> toRemove = new ArrayList<File>();
        ArrayList<String> depsToRemove = new ArrayList<String>();
        while (depFiles.hasNext()) {
            String depFile = depFiles.next().toString();
            DependencyGraph graph = new DependencyGraph(depFile, null /*watchPaths*/);

            // get the source file. it's the first item in the pre-reqs
            File sourceFile = graph.getFirstPrereq();
            String sourceFilePath = sourceFile.getAbsolutePath();

            // The gen folder may contain other dependency files not generated by this particular
            // processor.
            // We only care if the first pre-rep is of the right extension.
            String fileExtension = sourceFilePath.substring(sourceFilePath.lastIndexOf('.') + 1);
            if (extensions.contains(fileExtension.toLowerCase(Locale.US))) {
                // remove from the list of sourceFiles to mark as "processed" (but not compiled
                // yet, that'll be done by adding it to toCompile)
                String sourceFolder = sourceFiles.get(sourceFilePath);
                if (sourceFolder == null) {
                    // looks like the source file does not exist anymore!
                    // we'll have to remove the output!
                    Set<File> outputFiles = graph.getTargets();
                    toRemove.addAll(outputFiles);

                    // also need to remove the dep file.
                    depsToRemove.add(depFile);
                } else {
                    // Source file is present. remove it from the list as being processed.
                    sourceFiles.remove(sourceFilePath);

                    // check if it needs to be recompiled.
                    if (hasBuildTypeChanged() ||
                            graph.dependenciesHaveChanged(false /*printStatus*/)) {
                        toCompile.put(sourceFilePath, sourceFolder);
                    }
                }
            }
        }

        // add to the list of files to compile, whatever is left in sourceFiles. Those are
        // new files that have never been compiled.
        toCompile.putAll(sourceFiles);

        processor.displayMessage(DisplayType.COMPILING, toCompile.size());
        if (!toCompile.isEmpty()) {
            for (Entry<String, String> toCompilePath : toCompile.entrySet()) {
                processor.process(toCompilePath.getKey(), toCompilePath.getValue(),
                        sourceFolders, taskProject);
            }
        }

        if (!toRemove.isEmpty()) {
            processor.displayMessage(DisplayType.REMOVE_OUTPUT, toRemove.size());

            for (File toRemoveFile : toRemove) {
                processor.removedOutput(toRemoveFile);
                if (!toRemoveFile.delete()) {
                    System.err.println("Failed to remove " + toRemoveFile.getAbsolutePath());
                }
            }
        }

        // remove the dependency files that are obsolete
        if (!depsToRemove.isEmpty()) {
            processor.displayMessage(DisplayType.REMOVE_DEP, toRemove.size());

            for (String path : depsToRemove) {
                if (!new File(path).delete()) {
                    System.err.println("Failed to remove " + path);
                }
            }
        }
    }

    /**
     * Returns a list of files found in given folders, all matching a given filter.
     * The result is a map of (file, folder).
     * @param folders the folders to search
     * @param filters the filters for the files. Typically a glob.
     * @return a map of (file, folder)
     */
    private Map<String, String> getFilesByNameEntryFilter(List<String> folders, String[] filters) {
        Map<String, String> sourceFiles = new HashMap<String, String>();

        for (String folder : folders) {
            Iterator<?> iterator = getFilesByNameEntryFilter(folder, filters);

            while (iterator.hasNext()) {
                sourceFiles.put(iterator.next().toString(), folder);
            }
        }

        return sourceFiles;
    }

    /**
     * Returns a list of files found in a given folder, matching a given filter.
     * @param folder the folder to search
     * @param filters the filter for the files. Typically a glob.
     * @return an iterator.
     */
    private Iterator<?> getFilesByNameEntryFilter(String folder, String... filters) {
        Project taskProject = getProject();

        // create a fileset to find all the files in the folder
        FileSet fs = new FileSet();
        fs.setProject(taskProject);
        fs.setDir(new File(folder));
        for (String filter : filters) {
            NameEntry include = fs.createInclude();
            include.setName(filter);
        }

        // loop through the results of the file set
        return fs.iterator();
    }
}
