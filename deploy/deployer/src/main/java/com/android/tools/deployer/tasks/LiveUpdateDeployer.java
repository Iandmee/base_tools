/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.tools.deployer.tasks;

import com.android.tools.deploy.proto.Deploy;
import com.android.tools.deployer.AdbClient;
import com.android.tools.deployer.Installer;
import com.android.tools.idea.protobuf.ByteString;
import com.android.utils.ILogger;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The purpose of this class is to provide a facet to updating Live Literals on the device.
 *
 * <p>Structurally very similar ot {@Deployer}, the main operation of this class is to invoke the
 * Java installer to execute a command on the on-device installer.
 *
 * <p>The fundamental difference, however, is that {@Deployer} operates right after a build is
 * finished and APK steadily available. Here, we need to operate without the APK and concentrate our
 * effort on respond time instead.
 */
public class LiveUpdateDeployer {

    private final ILogger logger;

    public LiveUpdateDeployer(ILogger logger) {
        this.logger = logger;
    }

    /** Inputs for updating Live Literal. */
    public static class UpdateLiveLiteralParam {
        final String key;
        final String type;
        final String value;
        final int offset;
        final String helper;

        public UpdateLiveLiteralParam(
                String key, int offset, String helper, String type, String value) {
            this.key = key;
            this.offset = offset;
            this.helper = helper;
            this.type = type;
            this.value = value;
        }
    }

    /** Inputs for Live Edit updates. */
    public static class UpdateLiveEditsParam {
        public final boolean isComposable;
        public final List<Integer> groupIds;
        public final Map<String, byte[]> classes;
        public final Map<String, byte[]> supportClasses;
        final boolean debugModeEnabled;

        /**
         * @param classes Map from classname to binary
         * @param supportClasses Map from classname to binary
         */
        public UpdateLiveEditsParam(
                Map<String, byte[]> classes,
                Map<String, byte[]> supportClasses,
                List<Integer> groupIds,
                boolean isComposable,
                boolean debugModeEnabled) {
            this.classes = classes;
            this.supportClasses = supportClasses;
            this.groupIds = groupIds;
            this.isComposable = isComposable;
            this.debugModeEnabled = debugModeEnabled;
        }
    }

    public static final class UpdateLiveEditResult {
        public final List<UpdateLiveEditError> errors;
        public final Deploy.AgentLiveEditResponse.RecomposeType recomposeType;

        public UpdateLiveEditResult() {
            this(new ArrayList<>(), Deploy.AgentLiveEditResponse.RecomposeType.NONE);
        }

        public UpdateLiveEditResult(List<UpdateLiveEditError> errors) {
            this(errors, Deploy.AgentLiveEditResponse.RecomposeType.NONE);
        }

        public UpdateLiveEditResult(
                List<UpdateLiveEditError> errors, Deploy.AgentLiveEditResponse.RecomposeType type) {
            this.errors = errors;
            recomposeType = type;
        }
    }

    /**
     * Everything is an error at the moment. While they are hard error that might cause the update
     * to be aborted. These should not be presented to the user with any sense of urgency due to the
     * agreed "best effort" nature of LL updates.
     */
    public static class UpdateLiveEditError {
        private static final String APP_RESTART_STR = "\nPlease Re-Run your application.";
        private static final String ADDED_CLASS_STR =
                "Unsupported addition of new class '%s' in file '%s'.";
        private static final String ADDED_METHOD_STR =
                "Unsupported addition of new method '%s.%s' in file '%s', line %d.";
        private static final String REMOVED_METHOD_STR =
                "Unsupported deletion of method '%s.%s' in file '%s'.";
        private static final String ADDED_FIELD_STR =
                "Unsupported addition of field '%s' in class '%s' in file '%s'.";
        private static final String REMOVED_FIELD_STR =
                "Unsupported deletion of field '%s' in class '%s' in file '%s'.";
        private static final String MODIFIED_FIELD_STR =
                "Unsupported change to field '%s' in class '%s' in file '%s'.";
        private static final String MODIFIED_SUPER_STR =
                "Unsupported change to superclass of class '%s' in file '%s'.";
        private static final String ADDED_INTERFACE_STR =
                "Unsupported change to interfaces of class '%s' in file '%s'.";
        private static final String REMOVED_INTERFACE_STR =
                "Unsupported change to interfaces of class '%s' in file '%s'.";

        private static final String UNSUPPORTED_COMPOSE_VERSION =
                "Update Compose runtime and compiler versions 1.3.0 or higher "
                        + "in order to view the latest Live Edit changes.";

        private final String msg;
        private final Deploy.UnsupportedChange.Type type;

        public UpdateLiveEditError(Exception e) {
            this.msg = e.getMessage();
            this.type = Deploy.UnsupportedChange.Type.UNKNOWN;
        }

        public UpdateLiveEditError(String msg) {
            this.msg = msg;
            this.type = Deploy.UnsupportedChange.Type.UNKNOWN;
        }

        public UpdateLiveEditError(Deploy.UnsupportedChange error) {
            this.type = error.getType();
            switch (error.getType()) {
                case ADDED_CLASS:
                    msg =
                            String.format(
                                    Locale.US,
                                    ADDED_CLASS_STR,
                                    error.getClassName(),
                                    error.getFileName());
                    break;
                case ADDED_METHOD:
                    msg =
                            String.format(
                                    Locale.US,
                                    ADDED_METHOD_STR,
                                    error.getClassName(),
                                    error.getTargetName(),
                                    error.getFileName(),
                                    error.getLineNumber());
                    break;
                case REMOVED_METHOD:
                    msg =
                            String.format(
                                    Locale.US,
                                    REMOVED_METHOD_STR,
                                    error.getClassName(),
                                    error.getTargetName(),
                                    error.getFileName());
                    break;
                case ADDED_FIELD:
                    msg =
                            String.format(
                                    Locale.US,
                                    ADDED_FIELD_STR,
                                    error.getClassName(),
                                    error.getTargetName(),
                                    error.getFileName());
                    break;
                case REMOVED_FIELD:
                    msg =
                            String.format(
                                    Locale.US,
                                    REMOVED_FIELD_STR,
                                    error.getClassName(),
                                    error.getTargetName(),
                                    error.getFileName());
                    break;
                case MODIFIED_FIELD:
                    msg =
                            String.format(
                                    Locale.US,
                                    MODIFIED_FIELD_STR,
                                    error.getClassName(),
                                    error.getTargetName(),
                                    error.getFileName());
                    break;
                case MODIFIED_SUPER:
                    msg =
                            String.format(
                                    Locale.US,
                                    MODIFIED_SUPER_STR,
                                    error.getClassName(),
                                    error.getFileName());
                    break;
                case ADDED_INTERFACE:
                    msg =
                            String.format(
                                    Locale.US,
                                    ADDED_INTERFACE_STR,
                                    error.getClassName(),
                                    error.getFileName());
                    break;
                case REMOVED_INTERFACE:
                    msg =
                            String.format(
                                    Locale.US,
                                    REMOVED_INTERFACE_STR,
                                    error.getClassName(),
                                    error.getFileName());
                    break;
                case UNSUPPORTED_COMPOSE_VERSION:
                    msg = UNSUPPORTED_COMPOSE_VERSION;
                    break;
                default:
                    msg = "Unknown error";
            }
        }

        public String getMessage() {
            if (type == Deploy.UnsupportedChange.Type.UNSUPPORTED_COMPOSE_VERSION) {
                return msg;
            }
            return msg + APP_RESTART_STR;
        }

        public Deploy.UnsupportedChange.Type getType() {
            return type;
        }
    }

    /** Temp solution. Going to refactor / move this elsewhere later. */
    public List<UpdateLiveEditError> updateLiveLiteral(
            Installer installer,
            AdbClient adb,
            String packageName,
            Collection<UpdateLiveLiteralParam> params) {

        List<Integer> pids = adb.getPids(packageName);
        Deploy.Arch arch = adb.getArch(pids);

        Deploy.LiveLiteralUpdateRequest.Builder requestBuilder =
                Deploy.LiveLiteralUpdateRequest.newBuilder();
        for (UpdateLiveLiteralParam param : params) {
            requestBuilder.addUpdates(
                    Deploy.LiveLiteral.newBuilder()
                            .setKey(param.key)
                            .setOffset(param.offset)
                            .setHelperClass(param.helper)
                            .setType(param.type)
                            .setValue(param.value));
        }

        requestBuilder.setPackageName(packageName);
        requestBuilder.addAllProcessIds(pids);
        requestBuilder.setArch(arch);

        Deploy.LiveLiteralUpdateRequest request = requestBuilder.build();

        List<UpdateLiveEditError> errors = new LinkedList<>();
        try {
            Deploy.LiveLiteralUpdateResponse response = installer.updateLiveLiterals(request);
            for (Deploy.AgentResponse failure : response.getFailedAgentsList()) {
                errors.add(new UpdateLiveEditError(failure.getLiveLiteralResponse().getExtra()));
            }
        } catch (IOException e) {
            logger.error(e, Arrays.toString(errors.toArray()));
        }
        return errors;
    }

    /** Temp solution. Going to refactor / move this elsewhere later. */
    public UpdateLiveEditResult updateLiveEdit(
            Installer installer, AdbClient adb, String packageName, UpdateLiveEditsParam param) {

        // Sometimes we get a PSI event for a top-level file when no top-level class exists. In this
        // case, just treat it as a no-op success.
        if (param.classes.isEmpty()) {
            return new UpdateLiveEditResult();
        }

        List<Integer> pids = adb.getPids(packageName);
        if (pids.isEmpty()) {
            System.out.println("Cancelling LiveEdit request(No target pids)");
            List<UpdateLiveEditError> error = new LinkedList<>();
            error.add(new UpdateLiveEditError("No target pids to Live Edit"));
            return new UpdateLiveEditResult(error);
        }

        Deploy.Arch arch = adb.getArch(pids);

        Deploy.LiveEditRequest.Builder requestBuilder = Deploy.LiveEditRequest.newBuilder();

        requestBuilder.addAllProcessIds(pids);
        requestBuilder.setArch(arch);
        requestBuilder.setPackageName(packageName);
        requestBuilder.setComposable(param.isComposable);
        requestBuilder.addAllGroupIds(param.groupIds);

        for (String name : param.classes.keySet()) {
            ByteString data = ByteString.copyFrom(param.classes.get(name));
            requestBuilder.addTargetClasses(
                    Deploy.LiveEditClass.newBuilder().setClassName(name).setClassData(data));
        }

        for (String name : param.classes.keySet()) {
            ByteString data = ByteString.copyFrom(param.classes.get(name));
            requestBuilder.addTargetClasses(
                    Deploy.LiveEditClass.newBuilder().setClassName(name).setClassData(data));
        }
        requestBuilder.addAllGroupIds(param.groupIds);

        for (String name : param.classes.keySet()) {
            ByteString data = ByteString.copyFrom(param.classes.get(name));
            requestBuilder.addTargetClasses(
                    Deploy.LiveEditClass.newBuilder().setClassName(name).setClassData(data));
        }

        for (String name : param.supportClasses.keySet()) {
            ByteString data = ByteString.copyFrom(param.supportClasses.get(name));
            requestBuilder.addSupportClasses(
                    Deploy.LiveEditClass.newBuilder().setClassName(name).setClassData(data));
        }
        requestBuilder.setDebugModeEnabled(param.debugModeEnabled);
        Deploy.LiveEditRequest request = requestBuilder.build();

        UpdateLiveEditResult result = null;
        try {
            List<UpdateLiveEditError> errors = new LinkedList<>();

            Deploy.LiveEditResponse response = installer.liveEdit(request);
            Deploy.AgentLiveEditResponse.RecomposeType recomposeType =
                    Deploy.AgentLiveEditResponse.RecomposeType.NONE;

            if (response.getStatus() == Deploy.LiveEditResponse.Status.AGENT_ERROR) {
                for (Deploy.AgentResponse failure : response.getFailedAgentsList()) {
                    failure.getLeResponse()
                            .getErrorsList()
                            .forEach(error -> errors.add(new UpdateLiveEditError(error)));
                }
            }
            if (response.getStatus() == Deploy.LiveEditResponse.Status.OK) {
                for (Deploy.AgentResponse success : response.getSuccessAgentsList()) {
                    if (!success.hasLeResponse()) {
                        throw new RuntimeException(
                                "Live Edit response does not contain agent response object");
                    }
                    Deploy.AgentLiveEditResponse ler = success.getLeResponse();
                    recomposeType = ler.getRecomposeType();

                }
            } else {
                errors.add(new UpdateLiveEditError(response.getStatus().toString()));
            }
            result = new UpdateLiveEditResult(errors, recomposeType);
        } catch (IOException e) {
            result =
                    new UpdateLiveEditResult(
                            Collections.singletonList(new UpdateLiveEditError(e)),
                            Deploy.AgentLiveEditResponse.RecomposeType.NONE);
        }

        // TODO: Next CL: Change the return type and return the result object instead.
        return result;
    }

    /** @return True if there are recomposition errors, false otherwise. */
    public boolean retrieveComposeStatus(Installer installer, AdbClient adb, String appId) {
        List<Integer> pids = adb.getPids(appId);
        Deploy.Arch arch = adb.getArch(pids);

        Deploy.ComposeStatusRequest.Builder requestBuilder =
                Deploy.ComposeStatusRequest.newBuilder();
        requestBuilder.setApplicationId(appId);
        requestBuilder.addAllProcessIds(pids);
        requestBuilder.setArch(arch);

        Deploy.ComposeStatusRequest request = requestBuilder.build();

        try {
            Deploy.ComposeStatusResponse response = installer.composeStatus(request);

            // *****************************
            // TODO: PRINT THESE IN THE UI!
            // *****************************

            for (Deploy.ComposeException exception : response.getExceptionsList()) {
                System.out.print("Live Edit Recompose Status Exception: (");
                System.out.print(exception.getRecoverable() ? "Recoverable" : "Not Recoverable");
                System.out.println(") " + exception.getMessage());
                return false;
            }
        } catch (IOException e) {
            logger.error(e, "Cannot retrieve Compose Status");
        }
        return true;
    }
}
