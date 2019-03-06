/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.tools.deployer;

import com.google.common.collect.ImmutableMap;

/**
 * Represents a failed deployment. When installing, apply changes or apply code changes failed, this
 * will be raised containing the information needed by the UI (IDE or command line) to surface the
 * error and the possible actions to the user.
 */
public class DeployerException extends Exception {

    // TODO(b/117673388): Add "Learn More" hyperlink/call to action when we finally have the webpage up.
    public enum ResolutionAction {
        // No possible resolution action exists.
        NONE,
        // Install and re-run the application.
        RUN_APP,
        // Apply changes to the application.
        APPLY_CHANGES,
        // Retry the previously attempted action.
        RETRY,
    }

    /**
     * The ordinal of this enum is used as the return code for the command line runners, the first
     * value NO_ERROR is not used as is zero and represents no error on the command line.
     */
    public enum Error {
        NO_ERROR("", "", "", ResolutionAction.NONE), // Should not be used

        CANNOT_SWAP_BEFORE_API_26(
                "Apply Changes is only supported on API 26 or newer",
                "",
                "",
                ResolutionAction.NONE),

        // Specific errors that can occur before the swap process.

        DUMP_UNKNOWN_PACKAGE(
                "Package not found on device.",
                "The package '%s' was not found on the device. Is the app installed?",
                "Install and run app",
                ResolutionAction.RUN_APP),

        DUMP_UNKNOWN_PROCESS(
                "No running app process found.", "", "Run app", ResolutionAction.RUN_APP),

        REMOTE_APK_NOT_FOUND_IN_DB(
                "Android Studio was unable to recognize the APK(s) currently installed on the device.",
                "",
                "Reinstall and restart app",
                ResolutionAction.RUN_APP),

        DIFFERENT_NUMBER_OF_APKS(
                "A different number of APKs were found on the device than on the host.",
                "",
                "Reinstall and restart app",
                ResolutionAction.RUN_APP),

        DIFFERENT_APK_NAMES(
                "The naming scheme of APKs on the device differ from the APKs on the host.",
                "",
                "Reinstall and restart app",
                ResolutionAction.RUN_APP),

        // Errors pertaining to un-swappable changes.

        CANNOT_SWAP_NEW_CLASS(
                "Adding classes requires an app restart.",
                "Found new class: %s",
                "Reinstall and restart app",
                ResolutionAction.RUN_APP),

        CANNOT_SWAP_STATIC_LIB(
                "Modifications to shared libraries require an app restart.",
                "File '%s' was modified.",
                "Reinstall and restart app",
                ResolutionAction.RUN_APP),

        CANNOT_SWAP_MANIFEST(
                "Modifications to AndroidManifest.xml require an app restart.",
                "Manifest '%s' was modified.",
                "Reinstall and restart app",
                ResolutionAction.RUN_APP),

        CANNOT_SWAP_RESOURCE(
                "Modifying resources requires an activity restart.",
                "Resource '%s' was modified.",
                "Apply changes and restart activity",
                ResolutionAction.APPLY_CHANGES),

        // Errors that are reported to us by jvmti.

        CANNOT_ADD_METHOD(
                "Adding a new method requires an app restart.",
                "",
                "Reinstall and restart app",
                ResolutionAction.RUN_APP),

        CANNOT_MODIFY_FIELDS(
                "Adding or removing a field requires an app restart.",
                "",
                "Reinstall and restart app",
                ResolutionAction.RUN_APP),

        CANNOT_CHANGE_INHERITANCE(
                "Changes to class inheritance require an app restart.",
                "",
                "Reinstall and restart app",
                ResolutionAction.RUN_APP),

        CANNOT_DELETE_METHOD(
                "Removing a method requires an app restart.",
                "",
                "Reinstall and restart app",
                ResolutionAction.RUN_APP),

        CANNOT_CHANGE_CLASS_MODIFIERS(
                "Changing class modifiers requires an app restart.",
                "",
                "Reinstall and restart app",
                ResolutionAction.RUN_APP),

        CANNOT_CHANGE_METHOD_MODIFIERS(
                "Changing method modifiers requires an app restart.",
                "",
                "Reinstall and restart app",
                ResolutionAction.RUN_APP),

        VERIFICATION_ERROR(
                "New code fails verification",
                "",
                "Reinstall and restart app",
                ResolutionAction.RUN_APP),

        UNKNOWN_JVMTI_ERROR(
                "Unknown JVMTI error code",
                "",
                "Reinstall and restart app",
                ResolutionAction.RUN_APP),

        // Catch-all errors for when an arbitrary failure may occur.

        DUMP_FAILED(
                "We were unable to deploy your changes.", "%s", "Retry", ResolutionAction.RETRY),

        PREINSTALL_FAILED(
                "The application could not be installed.", "%s", "Retry", ResolutionAction.RETRY),

        INSTALL_FAILED(
                "The application could not be installed: %s",
                "%s", "Retry", ResolutionAction.RETRY),

        SWAP_FAILED(
                "We were unable to deploy your changes.", "%s", "Retry", ResolutionAction.RETRY),

        PARSE_FAILED(
                "We were unable to deploy your changes.", "%s", "Retry", ResolutionAction.RETRY),

        INTERRUPTED("Deployment was interrupted.", "%s", "Retry", ResolutionAction.RETRY),

        OPERATION_NOT_SUPPORTED("Operation not supported.", "%s", "", ResolutionAction.NONE);

        private final String message;
        private final String details;

        private final String callToAction;
        private final ResolutionAction action;

        Error(String message, String details, String callToAction, ResolutionAction action) {
            this.message = message;
            this.details = details;
            this.callToAction = callToAction;
            this.action = action;
        }

        public String getCallToAction() {
            return callToAction;
        }

        public ResolutionAction getResolution() {
            return action;
        }
    }

    private Error error;
    private String code;
    private String details;

    private static String[] NO_ARGS = {};

    private DeployerException(Error error) {
        this(error, "", NO_ARGS, NO_ARGS);
    }

    private DeployerException(Error error, String[] messageArgs, String... detailArgs) {
        this(error, "", messageArgs, detailArgs);
    }

    private DeployerException(
            Error error, String code, String[] messageArgs, String... detailArgs) {
        super(String.format(error.message, (Object[]) messageArgs));
        this.error = error;
        this.code = error.name() + (code.isEmpty() ? "" : ".") + code;
        this.details = String.format(error.details, (Object[]) detailArgs);
    }

    public Error getError() {
        return error;
    }

    public String getId() {
        return code;
    }

    public String getDetails() {
        return details;
    }

    public static DeployerException unknownPackage(String packageName) {
        return new DeployerException(Error.DUMP_UNKNOWN_PACKAGE, NO_ARGS, packageName);
    }

    // TODO: Make this package-aware.
    public static DeployerException unknownProcess() {
        return new DeployerException(Error.DUMP_UNKNOWN_PROCESS);
    }

    public static DeployerException remoteApkNotFound() {
        return new DeployerException(Error.REMOTE_APK_NOT_FOUND_IN_DB);
    }

    public static DeployerException apkCountMismatch() {
        return new DeployerException(Error.DIFFERENT_NUMBER_OF_APKS);
    }

    public static DeployerException apkNameMismatch() {
        return new DeployerException(Error.DIFFERENT_APK_NAMES);
    }

    public static DeployerException addedNewClass(String className) {
        return new DeployerException(Error.CANNOT_SWAP_NEW_CLASS, NO_ARGS, className);
    }

    public static DeployerException changedSharedObject(String filePath) {
        return new DeployerException(Error.CANNOT_SWAP_STATIC_LIB, NO_ARGS, filePath);
    }

    public static DeployerException changedManifest(String filePath) {
        return new DeployerException(Error.CANNOT_SWAP_MANIFEST, NO_ARGS, filePath);
    }

    public static DeployerException changedResources(String filePath) {
        return new DeployerException(Error.CANNOT_SWAP_RESOURCE, NO_ARGS, filePath);
    }

    private static final ImmutableMap<String, Error> ERROR_CODE_TO_ERROR =
            ImmutableMap.<String, Error>builder()
                    .put(
                            "JVMTI_ERROR_UNSUPPORTED_REDEFINITION_METHOD_ADDED",
                            Error.CANNOT_ADD_METHOD)
                    .put(
                            "JVMTI_ERROR_UNSUPPORTED_REDEFINITION_SCHEMA_CHANGED",
                            Error.CANNOT_MODIFY_FIELDS)
                    .put(
                            "JVMTI_ERROR_UNSUPPORTED_REDEFINITION_HIERARCHY_CHANGED",
                            Error.CANNOT_CHANGE_INHERITANCE)
                    .put(
                            "JVMTI_ERROR_UNSUPPORTED_REDEFINITION_METHOD_DELETED",
                            Error.CANNOT_DELETE_METHOD)
                    .put(
                            "JVMTI_ERROR_UNSUPPORTED_REDEFINITION_CLASS_MODIFIERS_CHANGED",
                            Error.CANNOT_CHANGE_CLASS_MODIFIERS)
                    .put(
                            "JVMTI_ERROR_UNSUPPORTED_REDEFINITION_METHOD_MODIFIERS_CHANGED",
                            Error.CANNOT_CHANGE_METHOD_MODIFIERS)
                    .put("JVMTI_ERROR_FAILS_VERIFICATION", Error.VERIFICATION_ERROR)
                    .build();

    public static DeployerException jvmtiError(String jvmtiErrorCode) {
        return new DeployerException(
                ERROR_CODE_TO_ERROR.getOrDefault(jvmtiErrorCode, Error.UNKNOWN_JVMTI_ERROR));
    }

    public static DeployerException dumpFailed(String reason) {
        return new DeployerException(Error.DUMP_FAILED, NO_ARGS, reason);
    }

    public static DeployerException parseFailed(String reason) {
        return new DeployerException(Error.PARSE_FAILED, NO_ARGS, reason);
    }

    public static DeployerException preinstallFailed(String reason) {
        return new DeployerException(Error.PREINSTALL_FAILED, NO_ARGS, reason);
    }

    public static DeployerException installFailed(String code, String reason) {
        return new DeployerException(Error.INSTALL_FAILED, new String[] {code}, reason);
    }

    public static DeployerException swapFailed(String reason) {
        return new DeployerException(Error.SWAP_FAILED, NO_ARGS, reason);
    }

    public static DeployerException interrupted(String reason) {
        return new DeployerException(Error.INTERRUPTED, NO_ARGS, reason);
    }

    public static DeployerException operationNotSupported(String reason) {
        return new DeployerException(Error.OPERATION_NOT_SUPPORTED, NO_ARGS, reason);
    }

    public static DeployerException apiNotSupported() {
        return new DeployerException(Error.CANNOT_SWAP_BEFORE_API_26, NO_ARGS, NO_ARGS);
    }
}
