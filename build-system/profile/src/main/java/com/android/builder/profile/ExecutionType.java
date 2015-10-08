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

package com.android.builder.profile;

/**
 * Defines a type of processing.
 *
 * Use range for similar categories events :
 * 0-1000       initial setup
 * 1000-2000    application related task creation
 * 2000-3000    library related task creation
 * 3000-4000    Tasks related events.
 */
public enum ExecutionType {

    SOME_RANDOM_PROCESSING(1),
    BASE_PLUGIN_PROJECT_CONFIGURE(2),
    BASE_PLUGIN_PROJECT_BASE_EXTENSTION_CREATION(3),
    BASE_PLUGIN_PROJECT_TASKS_CREATION(4),
    BASE_PLUGIN_BUILD_FINISHED(5),
    TASK_MANAGER_CREATE_TASKS(6),
    BASE_PLUGIN_CREATE_ANDROID_TASKS(7),
    VARIANT_MANAGER_CREATE_ANDROID_TASKS(8),
    VARIANT_MANAGER_CREATE_TASKS_FOR_VARIANT(9),
    VARIANT_MANAGER_CREATE_LINT_TASKS(10),
    VARIANT_MANAGER_CREATE_TESTS_TASKS(11),
    VARIANT_MANAGER_CREATE_VARIANTS(12),
    RESOLVE_DEPENDENCIES(12),
    INITIAL_METADATA(100),
    FINAL_METADATA(101),
    GENERAL_CONFIG(102),
    VARIANT_CONFIG(103),

    // ApplicationTaskManager per variant tasks.
    APP_TASK_MANAGER_CREATE_MERGE_MANIFEST_TASK(1000),
    APP_TASK_MANAGER_CREATE_GENERATE_RES_VALUES_TASK(1001),
    APP_TASK_MANAGER_CREATE_CREATE_RENDERSCRIPT_TASK(1002),
    APP_TASK_MANAGER_CREATE_MERGE_RESOURCES_TASK(1003),
    APP_TASK_MANAGER_CREATE_MERGE_ASSETS_TASK(1004),
    APP_TASK_MANAGER_CREATE_BUILD_CONFIG_TASK(1005),
    APP_TASK_MANAGER_CREATE_PROCESS_RES_TASK(1006),
    APP_TASK_MANAGER_CREATE_AIDL_TASK(1007),
    APP_TASK_MANAGER_CREATE_COMPILE_TASK(1008),
    APP_TASK_MANAGER_CREATE_NDK_TASK(1009),
    APP_TASK_MANAGER_CREATE_SPLIT_TASK(1010),
    APP_TASK_MANAGER_CREATE_PACKAGING_TASK(1011),
    @Deprecated APP_TASK_MANAGER_CREATE_PREPROCESS_RESOURCES_TASK(1012),
    @Deprecated APP_TASK_MANAGER_CREATE_BACKPORT_RESOURCES_TASK(1013),
    APP_TASK_MANAGER_CREATE_LINT_TASK(1014),

    // LibraryTaskManager per variant tasks.
    LIB_TASK_MANAGER_CREATE_MERGE_MANIFEST_TASK(2000),
    LIB_TASK_MANAGER_CREATE_GENERATE_RES_VALUES_TASK(2001),
    LIB_TASK_MANAGER_CREATE_CREATE_RENDERSCRIPT_TASK(2002),
    LIB_TASK_MANAGER_CREATE_MERGE_RESOURCES_TASK(2003),
    LIB_TASK_MANAGER_CREATE_MERGE_ASSETS_TASK(2004),
    LIB_TASK_MANAGER_CREATE_BUILD_CONFIG_TASK(2005),
    LIB_TASK_MANAGER_CREATE_PROCESS_RES_TASK(2006),
    LIB_TASK_MANAGER_CREATE_AIDL_TASK(2007),
    LIB_TASK_MANAGER_CREATE_COMPILE_TASK(2008),
    LIB_TASK_MANAGER_CREATE_NDK_TASK(2009),
    LIB_TASK_MANAGER_CREATE_SPLIT_TASK(2010),
    LIB_TASK_MANAGER_CREATE_PACKAGING_TASK(2011),
    LIB_TASK_MANAGER_CREATE_MERGE_PROGUARD_FILE_TASK(2012),
    LIB_TASK_MANAGER_CREATE_POST_COMPILATION_TASK(2013),
    @Deprecated LIB_TASK_MANAGER_CREATE_PROGUARD_TASK(2014),
    @Deprecated LIB_TASK_MANAGER_CREATE_PACKAGE_LOCAL_JAR(2015),
    @Deprecated LIB_TASK_MANAGER_CREATE_BACKPORT_RESOURCES_TASK(2016),
    LIB_TASK_MANAGER_CREATE_LINT_TASK(2017),

    // TASK_EXECUTION
    GENERIC_TASK_EXECUTION(3000),
    TASK_AIDL_COMPILE(3001),
    TASK_DELETE(3002),
    TASK_CHECK_MANIFEST(3003),
    TASK_PREPARE_DEPENDENCIES_TASK(3004),
    TASK_RENDERSCRIPT_COMPILE(3005),
    TASK_GENERATE_BUILD_CONFIG(3006),
    TASK_MERGE_ASSETS(3007),
    TASK_GENERATE_RES_VALUES(3008),
    TASK_MERGE_RESOURCES(3009),
    TASK_MERGE_MANIFESTS(3010),
    TASK_PROCESS_ANDROID_RESOURCES(3011),
    TASK_JAVA_COMPILE(3012),
    TASK_NDK_COMPILE(3013),
    TASK_PRE_DEX(3014),
    TASK_DEX(3015),
    TASK_PACKAGE_SPLIT_RES(3016),
    TASK_PROCESS_RESOURCES(3017),
    TASK_VALIDATE_SIGNING_TASK(3018),
    TASK_PACKAGE_APPLICATION(3019),
    TASK_SPLIT_ZIP_ALIGN(3020),
    TASK_ZIP_ALIGN(3021),
    TASK_COPY(3022),
    TASK_LINT(3023),
    TASK_TRANSFORM(3024);

    int getId() {
        return id;
    }

    private final int id;

    ExecutionType(int id) {
        this.id = id;
    }
}
