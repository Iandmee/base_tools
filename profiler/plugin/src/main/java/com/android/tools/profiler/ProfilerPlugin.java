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

package com.android.tools.profiler;

import com.android.build.api.transform.Transform;
import com.google.common.base.Charsets;
import com.google.common.io.ByteStreams;
import com.google.common.io.Closeables;
import com.google.common.io.Files;

import org.gradle.api.DefaultTask;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.dsl.RepositoryHandler;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

/**
 * A gradle plugin which, when applied, instruments the target Android app with support code for
 * helping profile it.
 */
public class ProfilerPlugin implements Plugin<Project> {

    private static final String PROPERTY_PROPERTIES_FILE = "android.profiler.properties";
    private static final String PROPERTY_ENABLED = "android.profiler.enabled";
    private static final String PROPERTY_GAPID_ENABLED = "android.profiler.gapid.enabled";
    private static final String PROPERTY_GAPID_TRACER_AAR = "android.profiler.gapid.tracer_aar";
    private static final String PROPERTY_SUPPORT_LIB_ENABLED = "android.profiler.supportLib.enabled";
    private static final String PROPERTY_INSTRUMENTATION_ENABLED = "android.profiler.instrumentation.enabled";

    static class ExtractSupportLibJarTask extends DefaultTask {

        private File outputFile;

        // This empty constructor is needed because Gradle will look at the declared constructors
        // to create objects dynamically. Yeap.
        public ExtractSupportLibJarTask() {
        }

        @OutputFile
        public File getOutputFile() {
            return outputFile;
        }

        public void setOutputFile(File file) {
            this.outputFile = file;
        }

        @TaskAction
        public void extract() throws IOException {
            InputStream jar = ProfilerPlugin.class
                    .getResourceAsStream("/profilers-support-lib.jar");
            if (jar == null) {
                throw new RuntimeException("Couldn't find profiler support library");
            }
            FileOutputStream fileOutputStream = new FileOutputStream(getOutputFile());
            try {
                ByteStreams.copy(jar, fileOutputStream);
            } finally {
                try {
                    jar.close();
                } finally {
                    fileOutputStream.close();
                }
            }
        }
    }

    @Override
    public void apply(final Project project) {
        Properties properties = getProperties(project);
        boolean enabled = getBoolean(properties, PROPERTY_ENABLED);
        if (enabled) {
            addProfilersLib(project, properties);
            applyGapidOptions(project, properties);
            registerTransform(project, properties);
        }
    }
    private void addProfilersLib(Project project, Properties properties) {
        if (getBoolean(properties, PROPERTY_SUPPORT_LIB_ENABLED)) {
            String path = "build/profilers-gen/profilers-support-lib.jar";
            ConfigurableFileCollection files = project.files(path);
            ExtractSupportLibJarTask task = project.getTasks()
                    .create("unpackProfilersLib", ExtractSupportLibJarTask.class);
            task.setOutputFile(project.file(path));
            files.builtBy(task);
            project.getDependencies().add("compile", files);
        }
    }
    private void applyGapidOptions(Project project, Properties properties) {
        if (getBoolean(properties, PROPERTY_GAPID_ENABLED)) {
            String aarFileName = properties.getProperty(PROPERTY_GAPID_TRACER_AAR);
            final File aarFile = (aarFileName == null) ? null : new File(aarFileName);
            if (aarFile != null && aarFile.exists()) {
                RepositoryHandler repositories = project.getRepositories();
                repositories.add(repositories.flatDir(new HashMap<String, Object>() {{
                    put("name", "gfxtracer");
                    put("dirs", Arrays.asList(aarFile.getParentFile().getAbsolutePath()));
                }}));
                final String baseName = Files.getNameWithoutExtension(aarFileName), extension
                        = Files.getFileExtension(aarFileName);
                project.getDependencies().add("compile", new HashMap<String, String>() {{
                    put("name", baseName);
                    put("ext", extension);
                }});
            }
        }
    }

    private void registerTransform(Project project, Properties properties) {
        if (getBoolean(properties, PROPERTY_INSTRUMENTATION_ENABLED)) {
            // TODO: The following line won't work for the experimental plugin. For that we may need to
            // register a rule that will get executed at the right time. Investigate this before
            // shipping the plugin.
            Object android = project.getExtensions().getByName("android");
            try {
                Method method = android.getClass()
                        .getMethod("registerTransform", Transform.class, Object[].class);
                method.invoke(android, new ProfilerTransform(), new Object[]{});
            } catch (NoSuchMethodException e) {
                e.printStackTrace();
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            } catch (InvocationTargetException e) {
                e.printStackTrace();
            }
        }
    }

    private Properties getProperties(Project project) {
        Map<String, ?> projectProperties = project.getProperties();
        Properties defaults = new Properties();
        for (Map.Entry<String, ?> e : projectProperties.entrySet()) {
            // Properties extends HashTable, which does not support null values.
            if (e.getValue() != null) {
                defaults.put(e.getKey(), e.getValue());
            }
        }
        Properties result = new Properties(defaults);

        Object propertiesFile = projectProperties.get(PROPERTY_PROPERTIES_FILE);
        if (propertiesFile != null) {
            Reader reader = null;
            try {
                reader = new InputStreamReader(new FileInputStream(String.valueOf(propertiesFile)),
                        Charsets.UTF_8);
                result.load(reader);
            } catch (IOException e) {
                // Ignored.
                e.printStackTrace();
            } finally {
                if (reader != null) {
                    Closeables.closeQuietly(reader);
                }
            }
        }
        return result;
    }

    private static boolean getBoolean(Properties properties, String name) {
        return Boolean.parseBoolean(properties.getProperty(name, "false"));
    }
}
