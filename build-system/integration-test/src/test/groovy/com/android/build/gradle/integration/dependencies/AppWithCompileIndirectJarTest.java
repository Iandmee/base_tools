/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.build.gradle.integration.dependencies;

import static com.android.build.gradle.integration.common.utils.TestFileUtils.appendToFile;
import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat;

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.utils.ModelHelper;
import com.android.builder.model.AndroidArtifact;
import com.android.builder.model.AndroidLibrary;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.Dependencies;
import com.android.builder.model.JavaLibrary;
import com.android.builder.model.Variant;
import com.google.common.collect.Iterables;
import com.google.common.truth.Truth;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

import java.io.IOException;
import java.util.Collection;
import java.util.Map;

/**
 * test for compile jar in app through an aar dependency
 */
public class AppWithCompileIndirectJarTest {

    @ClassRule
    public static GradleTestProject project = GradleTestProject.builder()
            .fromTestProject("projectWithModules")
            .create();
    public static Map<String, AndroidProject> models;

    @BeforeClass
    public static void setUp() throws IOException {
        appendToFile(project.getBuildFile(),
"\nsubprojects {\n" +
"    apply from: \"$rootDir/../commonLocalRepo.gradle\"\n" +
"}\n");

        appendToFile(project.getSubproject("app").getBuildFile(),
"\ndependencies {\n" +
"    compile project(':library')\n" +
"}\n");

        appendToFile(project.getSubproject("library").getBuildFile(),
"\ndependencies {\n" +
"    compile 'com.google.guava:guava:18.0'\n" +
"}\n");

        models = project.getAllModels();
    }

    @AfterClass
    public static void cleanUp() {
        project = null;
        models = null;
    }

    @Test
    public void checkTheModel() {
        Variant appDebug = ModelHelper.getVariant(models.get(":app").getVariants(), "debug");
        Truth.assertThat(appDebug).isNotNull();

        Dependencies deps = appDebug.getMainArtifact().getDependencies();

        Collection<AndroidLibrary> libs = deps.getLibraries();
        assertThat(libs).named("app androidlibrary deps count").hasSize(1);
        AndroidLibrary androidLibrary = Iterables.getOnlyElement(libs);
        assertThat(androidLibrary.getProject()).named("app androidlib deps path").isEqualTo(":library");

        assertThat(deps.getProjects()).named("app module dependency count").isEmpty();

        Collection<JavaLibrary> javaLibraries = deps.getJavaLibraries();
        assertThat(javaLibraries).named("app java dependency count").hasSize(1);
        JavaLibrary javaLib = Iterables.getOnlyElement(javaLibraries);
        assertThat(javaLib.getResolvedCoordinates())
                .named("app java lib resolved coordinates")
                .isEqualTo("com.google.guava", "guava", "18.0");

        // ---

        Variant libDebug = ModelHelper.getVariant(models.get(":library").getVariants(), "debug");
        Truth.assertThat(libDebug).isNotNull();

        deps = libDebug.getMainArtifact().getDependencies();

        assertThat(deps.getLibraries()).named("lib androidlibrary deps count").isEmpty();

        javaLibraries = deps.getJavaLibraries();
        assertThat(javaLibraries).named("lib java dependency count").hasSize(1);
        javaLib = Iterables.getOnlyElement(javaLibraries);
        assertThat(javaLib.getResolvedCoordinates())
                .named("lib java lib resolved coordinates")
                .isEqualTo("com.google.guava", "guava", "18.0");
    }
}
