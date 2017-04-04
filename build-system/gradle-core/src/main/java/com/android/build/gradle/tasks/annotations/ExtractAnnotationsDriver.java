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

package com.android.build.gradle.tasks.annotations;

import static com.android.SdkConstants.DOT_JAVA;
import static java.io.File.pathSeparator;
import static java.io.File.pathSeparatorChar;

import com.android.annotations.NonNull;
import com.android.tools.lint.LintCoreApplicationEnvironment;
import com.android.tools.lint.LintCoreProjectEnvironment;
import com.google.common.base.Charsets;
import com.google.common.base.Splitter;
import com.google.common.collect.Lists;
import com.google.common.io.Files;
import com.intellij.mock.MockProject;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import com.intellij.psi.PsiJavaFile;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.List;

/**
 * The extract annotations driver is a command line interface to extracting annotations
 * from a source tree. It's similar to the gradle
 * {@link com.android.build.gradle.tasks.ExtractAnnotations} task,
 * but usable from the command line and outside Gradle, for example
 * to extract annotations from the Android framework itself (which is not built with
 * Gradle). It also allows other options only interesting for extracting
 * platform annotations, such as filtering all APIs and constants through an
 * API white-list (such that we for example can pull annotations from the master
 * branch which has the latest metadata, but only expose APIs that are actually in
 * a released platform), as well as translating android.annotation annotations into
 * android.support.annotations.
 */
public class ExtractAnnotationsDriver {
    public static void main(String[] args) {
        new ExtractAnnotationsDriver().run(args);
    }

    private static void usage(PrintStream output) {
        output.println("Usage: " + ExtractAnnotationsDriver.class.getSimpleName() + " <flags>");
        output.println(" --sources <paths>       : Source directories to extract annotations from. ");
        output.println("                           Separate paths with " + pathSeparator + ", and you can use @ ");
        output.println("                           as a filename prefix to have the filenames fed from a file");
        output.println("--classpath <paths>      : Directories and .jar files to resolve symbols from");
        output.println("--output <zip path>      : The .zip file to write the extracted annotations to, if any");
        output.println("--proguard <path>        : The proguard.cfg file to write the keep rules to, if any");
        output.println();
        output.println("Optional flags:");
        output.println("--merge-zips <paths>     : Existing external annotation files to merge in");
        output.println("--quiet                  : Don't print summary information");
        output.println("--rmtypedefs <folder>    : Remove typedef classes found in the given folder");
        output.println("--allow-missing-types    : Don't fail even if some types can't be resolved");
        output.println("--allow-errors           : Don't fail even if there are some compiler errors");
        output.println("--api-filter <api.txt>   : A framework API definition to restrict included APIs to");
        output.println("--hide-filtered          : If filtering out non-APIs, supply this flag to hide listing matches");
        output.println("--skip-class-retention   : Don't extract annotations that have class retention");
        output.println("--typedef-file <path>    : Write a packaging recipe description to the given file");
        System.exit(-1);
    }

    @SuppressWarnings("MethodMayBeStatic")
    public void run(@NonNull String[] args) {
        List<File> classpath = Lists.newArrayList();
        List<File> sources = Lists.newArrayList();
        List<File> mergePaths = Lists.newArrayList();
        List<File> apiFilters = null;
        File rmTypeDefs = null;
        boolean verbose = true;
        boolean allowMissingTypes = false;
        boolean allowErrors = false;
        boolean listFiltered = true;
        boolean skipClassRetention = false;

        File output = null;
        File proguard = null;
        File typedefFile = null;
        if (args.length == 1 && "--help".equals(args[0])) {
            usage(System.out);
        }
        if (args.length < 2) {
            usage(System.err);
        }
        for (int i = 0, n = args.length; i < n; i++) {
            String flag = args[i];

            switch (flag) {
                case "--quiet":
                    verbose = false;
                    continue;
                case "--allow-missing-types":
                    allowMissingTypes = true;
                    continue;
                case "--allow-errors":
                    allowErrors = true;
                    continue;
                case "--hide-filtered":
                    listFiltered = false;
                    continue;
                case "--skip-class-retention":
                    skipClassRetention = true;
                    continue;
            }
            if (i == n - 1) {
                usage(System.err);
            }
            String value = args[i + 1];
            i++;

            switch (flag) {
                case "--sources":
                    sources = getFiles(value);
                    break;
                case "--classpath":
                    classpath = getFiles(value);
                    break;
                case "--merge-zips":
                    mergePaths = getFiles(value);
                    break;

                case "--output":
                    output = new File(value);
                    if (output.exists()) {
                        if (output.isDirectory()) {
                            abort(output + " is a directory");
                        }
                        boolean deleted = output.delete();
                        if (!deleted) {
                            abort("Could not delete previous version of " + output);
                        }
                    } else if (output.getParentFile() != null && !output.getParentFile().exists()) {
                        abort(output.getParentFile() + " does not exist");
                    }
                    break;
                case "--proguard":
                    proguard = new File(value);
                    if (proguard.exists()) {
                        if (proguard.isDirectory()) {
                            abort(proguard + " is a directory");
                        }
                        boolean deleted = proguard.delete();
                        if (!deleted) {
                            abort("Could not delete previous version of " + proguard);
                        }
                    } else if (proguard.getParentFile() != null && !proguard.getParentFile()
                            .exists()) {
                        abort(proguard.getParentFile() + " does not exist");
                    }
                    break;
                case "--typedef-file":
                    typedefFile = new File(value);
                    break;
                case "--api-filter":
                    if (apiFilters == null) {
                        apiFilters = Lists.newArrayList();
                    }
                    for (String path : Splitter.on(",").omitEmptyStrings().split(value)) {
                        File apiFilter = new File(path);
                        if (!apiFilter.isFile()) {
                            String message = apiFilter + " does not exist or is not a file";
                            abort(message);
                        }
                        apiFilters.add(apiFilter);
                    }
                    break;
                case "--rmtypedefs":
                    rmTypeDefs = new File(value);
                    if (!rmTypeDefs.isDirectory()) {
                        abort(rmTypeDefs + " is not a directory");
                    }
                    break;
                default:
                    System.err
                            .println("Unknown flag " + flag + ": Use --help for usage information");
                    break;
            }
        }

        if (sources.isEmpty()) {
            abort("Must specify at least one source path");
        }
        if (classpath.isEmpty()) {
            abort("Must specify classpath pointing to at least android.jar or the framework");
        }
        if (output == null && proguard == null) {
            abort("Must specify output path with --output or a proguard path with --proguard");
        }

        // API definition files
        ApiDatabase database = null;
        if (apiFilters != null && !apiFilters.isEmpty()) {
            try {
                List<String> lines = Lists.newArrayList();
                for (File file : apiFilters) {
                    lines.addAll(Files.readLines(file, Charsets.UTF_8));
                }
                database = new ApiDatabase(lines);
            } catch (IOException e) {
                abort("Could not open API database " + apiFilters + ": " + e.getLocalizedMessage());
            }
        }

        Extractor extractor = new Extractor(database, rmTypeDefs, verbose, !skipClassRetention,
                true);
        extractor.setListIgnored(listFiltered);

        LintCoreApplicationEnvironment appEnv = LintCoreApplicationEnvironment.get();
        Disposable parentDisposable = Disposer.newDisposable();
        LintCoreProjectEnvironment projectEnvironment = LintCoreProjectEnvironment.create(
                parentDisposable, appEnv);


        List<File> joined = Lists.newArrayList(sources);
        joined.addAll(classpath);
        projectEnvironment.registerPaths(joined);

        MockProject project = projectEnvironment.getProject();
        List<PsiJavaFile> units = Extractor.createUnitsInDirectories(project, sources);

        extractor.extractFromProjectSource(units);

        if (mergePaths != null) {
            for (File jar : mergePaths) {
                extractor.mergeExisting(jar);
            }
        }

        extractor.export(output, proguard);

        // Remove typedefs?
        if (typedefFile != null) {
            extractor.writeTypedefFile(typedefFile);
        }

        //noinspection VariableNotUsedInsideIf
        if (rmTypeDefs != null) {
            if (typedefFile != null) {
                Extractor.removeTypedefClasses(rmTypeDefs, typedefFile);
            } else {
                extractor.removeTypedefClasses();
            }
        }

        Disposer.dispose(LintCoreApplicationEnvironment.get().getParentDisposable());
    }

    private static void abort(@NonNull String message) {
        System.err.println(message);
        System.exit(-1);
    }

    private static List<File> getFiles(String value) {
        List<File> files = Lists.newArrayList();
        Splitter splitter = Splitter.on(pathSeparatorChar).omitEmptyStrings().trimResults();
        for (String path : splitter.split(value)) {
            if (path.startsWith("@")) {
                // Special syntax for providing files in a list
                File sourcePath = new File(path.substring(1));
                if (!sourcePath.exists()) {
                    abort(sourcePath + " does not exist");
                }
                try {
                    for (String line : Files.readLines(sourcePath, Charsets.UTF_8)) {
                        line = line.trim();
                        if (!line.isEmpty()) {
                            File file = new File(line);
                            if (!file.exists()) {
                                System.err.println("Warning: Could not find file " + line +
                                        " listed in " + sourcePath);
                            }
                            files.add(file);
                        }
                    }
                    continue;
                } catch (IOException e) {
                    e.printStackTrace();
                    System.exit(-1);
                }
            }
            File file = new File(path);
            if (!file.exists()) {
                abort(file + " does not exist");
            }
            files.add(file);
        }

        return files;
    }

    private static void addJavaSources(List<File> list, File file) {
        if (file.isDirectory()) {
            File[] files = file.listFiles();
            if (files != null) {
                for (File child : files) {
                    addJavaSources(list, child);
                }
            }
        } else {
            if (file.isFile() && file.getName().endsWith(DOT_JAVA)) {
                list.add(file);
            }
        }
    }

    private static List<File> gatherJavaSources(List<File> sourcePath) {
        List<File> sources = Lists.newArrayList();
        for (File file : sourcePath) {
            addJavaSources(sources, file);
        }
        return sources;
    }
}