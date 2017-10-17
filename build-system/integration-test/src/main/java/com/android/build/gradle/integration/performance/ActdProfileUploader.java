/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.build.gradle.integration.performance;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.annotations.VisibleForTesting;
import com.android.testutils.TestUtils;
import com.android.tools.build.gradle.internal.profile.GradleTaskExecutionType;
import com.android.tools.build.gradle.internal.profile.GradleTransformExecutionType;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpBackOffUnsuccessfulResponseHandler;
import com.google.api.client.http.HttpResponse;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.InputStreamContent;
import com.google.api.client.util.ExponentialBackOff;
import com.google.api.client.util.Maps;
import com.google.api.client.util.Preconditions;
import com.google.gson.Gson;
import com.google.wireless.android.sdk.gradlelogging.proto.Logging.GradleBenchmarkResult;
import com.google.wireless.android.sdk.stats.GradleBuildProfileSpan;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.security.GeneralSecurityException;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/** Uploader that pushes profile results to act-d. */
public final class ActdProfileUploader implements ProfileUploader {
    @NonNull private static final String ACTD_ADD_BUILD_URL = "/apis/addBuild";
    @NonNull private static final String ACTD_ADD_SAMPLE_URL = "/apis/addSample";

    /**
     * Git-specific constants used to ascertain what the current HEAD commit is, which is used to
     * send to the act-d API to make the dashboards more useful.
     *
     * <p>It's quite hacky to shell out to git in this way to get the information we need, so if you
     * feel motivated enough to find a better solution (something something libgit2?) feel free to
     * send the review to samwho@.
     *
     * <p>Note: this will likely fail when run in Bazel (it currently isn't because the BUILD file
     * associated with this project specifically does not run tests that have a directory named
     * "performance" on their path).
     */
    @NonNull private static final String GIT_BINARY = "/usr/bin/git";

    @NonNull
    private static final String[] GIT_LAST_COMMIT_JSON_CMD = {
        GIT_BINARY,
        "--no-pager",
        "log",
        "-n1",
        "--pretty=format:"
                + "{%n"
                + "  \"hash\": \"%H\",%n"
                + "  \"abbrevHash\": \"%h\",%n"
                + "  \"authorName\": \"%aN\",%n"
                + "  \"authorEmail\": \"%aE\",%n"
                + "  \"subject\": \"%s\"%n"
                + "}%n"
    };

    private final int buildId;
    private final File repo;
    @NonNull private final String actdBaseUrl;
    @NonNull private final String actdProjectId;
    @Nullable private final String actdBuildUrl;
    @Nullable private final String actdCommitUrl;

    private ActdProfileUploader(
            int buildId,
            @NonNull File repo,
            @NonNull String actdBaseUrl,
            @NonNull String actdProjectId,
            @Nullable String actdBuildUrl,
            @Nullable String actdCommitUrl) {
        this.buildId = buildId;
        this.repo = repo;
        this.actdBaseUrl = actdBaseUrl;
        this.actdProjectId = actdProjectId;
        this.actdBuildUrl = actdBuildUrl;
        this.actdCommitUrl = actdCommitUrl;
    }

    /**
     * Creates an ActdProfileUploader object. If you're looking to use the act-d uploader, use
     * {@code fromEnvironment()} instead and set the relevant environment variables.
     */
    @VisibleForTesting
    @NonNull
    public static ActdProfileUploader create(
            int buildId,
            @NonNull File repo,
            @NonNull String actdBaseUrl,
            @NonNull String actdProjectId,
            @Nullable String actdBuildUrl,
            @Nullable String actdCommitUrl) {
        return new ActdProfileUploader(
                buildId, repo, actdBaseUrl, actdProjectId, actdBuildUrl, actdCommitUrl);
    }

    /**
     * Constructs an ActdProfileUploader from the relevant environment variables.
     *
     * @throws IllegalStateException if any required environment variables are not present or empty.
     */
    @NonNull
    public static ActdProfileUploader fromEnvironment() {
        /** Required properties for act-d uploading to work. */
        String actdBaseUrl = System.getenv("ACTD_BASE_URL");
        if (actdBaseUrl == null || actdBaseUrl.isEmpty()) {
            throw new IllegalStateException("no ACTD_BASE_URL environment variable set");
        }

        String actdProjectId = System.getenv("ACTD_PROJECT_ID");
        if (actdProjectId == null || actdProjectId.isEmpty()) {
            throw new IllegalStateException("no ACTD_PROJECT_ID environment variable set");
        }

        String buildbotBuildNumber = System.getenv("BUILDBOT_BUILDNUMBER");
        if (buildbotBuildNumber == null || buildbotBuildNumber.isEmpty()) {
            throw new IllegalStateException("no BUILDBOT_BUILDNUMBER environment variable set");
        }

        /**
         * Optional URLs for use in API calls to make the dashboards more useful.
         *
         * <p>Remember that the environment variable cannot use format strings (e.g. %s) because
         * buildbot uses those for other things and the values won't come out as you expect them.
         * Because of this, we simply append a slash followed by the commit hash or build ID to get
         * the final URL.
         */
        String actdBuildUrl = System.getenv("ACTD_BUILD_URL");
        String actdCommitUrl = System.getenv("ACTD_COMMIT_URL");

        /**
         * Hardcoding the repo for the time being. It should be relatively easy to change this later
         * if the need arises.
         */
        File repo = TestUtils.getWorkspaceFile("tools/base");

        return new ActdProfileUploader(
                Integer.parseInt(buildbotBuildNumber),
                repo,
                actdBaseUrl,
                actdProjectId,
                actdBuildUrl,
                actdCommitUrl);
    }

    /**
     * Returns a stable "series ID" for this benchmark result. These need to be unique per benchmark
     * scenario, e.g. AntennaPod no-op with a specific set of flags.
     */
    @VisibleForTesting
    @NonNull
    public static String seriesId(
            @NonNull GradleBenchmarkResult result, @NonNull GradleBuildProfileSpan span) {
        String task = GradleTaskExecutionType.forNumber(span.getTask().getType()).name();
        if (span.getTask().getType() == GradleTaskExecutionType.TRANSFORM_VALUE) {
            String transform =
                    GradleTransformExecutionType.forNumber(span.getTransform().getType()).name();
            task = task + " " + transform;
        }

        return result.getBenchmark()
                + " "
                + result.getBenchmarkMode()
                + " "
                + task
                + " ("
                + flags(result)
                + ")";
    }

    /**
     * Returns a string describing the GradleBenchmarkResult. This is surfaced in the UI, so use it
     * to include vital information about the benchmark (e.g. flags).
     */
    @VisibleForTesting
    @NonNull
    public static String description(
            @NonNull GradleBenchmarkResult result, @NonNull GradleBuildProfileSpan span) {
        return flags(result);
    }

    /**
     * Returns a stable string representing the flags enabled for this GradleBenchmarkResult. When I
     * say "stable", I mean that the same set of enabled flags will always return the same string,
     * because the flags are first sorted before being serialised to string. This makes the result
     * useful in, for example, the series ID.
     */
    @VisibleForTesting
    @NonNull
    public static String flags(@NonNull GradleBenchmarkResult result) {
        return result.getFlags()
                .getAllFields()
                .entrySet()
                .stream()
                .sorted(Comparator.comparing(e -> e.getKey().getName()))
                .map(e -> e.getKey().getName() + "=" + e.getValue())
                .collect(Collectors.joining(", "));
    }

    /**
     * Creates a valid {@code Infos} object for the head of the given repository. This relies on
     * shelling out to git, see {@code lastCommitJson()} for details.
     */
    @VisibleForTesting
    @NonNull
    public static Infos infos(File repo) throws IOException {
        return new Gson().fromJson(lastCommitJson(repo), Infos.class);
    }

    /**
     * Gets information about the most recent commit in a repository (given as a {@code File} as a
     * parameter to this function), in a JSON format suitable for consumption by the {@code Infos}
     * object, which itself is suitable for consumption by the act-d API.
     */
    @VisibleForTesting
    @NonNull
    public static String lastCommitJson(@NonNull File repo) throws IOException {
        return runCmd(repo, GIT_LAST_COMMIT_JSON_CMD);
    }

    @VisibleForTesting
    @NonNull
    public static String hostname() throws IOException {
        String hostname;

        hostname = System.getenv("TESTING_SLAVENAME");
        if (hostname != null && !hostname.isEmpty()) {
            return hostname;
        }

        hostname = System.getenv("BUILDBOT_SLAVENAME");
        if (hostname != null && !hostname.isEmpty()) {
            return hostname;
        }

        return runCmd(null, new String[] {"hostname"});
    }

    /**
     * Runs a command from a given working directory and returns its stdout.
     *
     * <p>Note: stdout will be buffered entirely in to memory. Don't run command that produce
     * enormous amounts of output, as they might cause the process to OOM.
     *
     * <p>Note: this function will block until the command has finished.
     *
     * @throws IOException if the command takes longer than 60 seconds, returns a non-0 exit status
     *     or we are interrupted while waiting for it to finish.
     * @throws IllegalArgumentException if the given cmd is empty.
     */
    @NonNull
    private static String runCmd(@Nullable File cwd, @NonNull String[] cmd) throws IOException {
        Preconditions.checkArgument(cmd.length > 0, "cmd specified is empty");

        ProcessBuilder pb = new ProcessBuilder(cmd);
        if (cwd != null) {
            pb.directory(cwd);
        }

        pb.redirectError(ProcessBuilder.Redirect.INHERIT);
        pb.redirectOutput(ProcessBuilder.Redirect.PIPE);

        Process proc = pb.start();
        String stdout;
        try (BufferedReader br = new BufferedReader(new InputStreamReader(proc.getInputStream()))) {
            stdout = br.lines().collect(Collectors.joining("\n"));
        }

        try {
            if (!proc.waitFor(60, TimeUnit.SECONDS)) {
                throw new IOException("timed out waiting for command to run");
            }
        } catch (InterruptedException e) {
            throw new IOException(e);
        }

        if (proc.exitValue() != 0) {
            throw new IOException("command returned non-0 status of " + proc.exitValue());
        }

        return stdout;
    }

    /**
     * POSTs JSON to a given URL. The JSON is created by using {@code Gson} on the {@code req}
     * parameter to this function.
     *
     * <p>Don't use this function directly, instead use {@code addBuild(BuildRequest)} and {@code
     * addSample(SampleRequest)}.
     */
    private static void jsonPost(@NonNull String url, @NonNull Object req) throws IOException {
        Preconditions.checkNotNull(url);
        Preconditions.checkNotNull(req);

        HttpTransport transport;
        try {
            transport = GoogleNetHttpTransport.newTrustedTransport();
        } catch (GeneralSecurityException e) {
            throw new IOException(e);
        }

        String jsonStr = new Gson().toJson(req);
        ByteArrayInputStream json = new ByteArrayInputStream(jsonStr.getBytes());
        InputStreamContent content = new InputStreamContent("application/json", json);
        GenericUrl gurl = new GenericUrl(url);
        HttpResponse res =
                transport
                        .createRequestFactory()
                        .buildPostRequest(gurl, content)
                        .setNumberOfRetries(5)
                        .setUnsuccessfulResponseHandler(
                                new HttpBackOffUnsuccessfulResponseHandler(
                                        new ExponentialBackOff()))
                        .setFollowRedirects(true)
                        .execute();

        try (BufferedReader br = new BufferedReader(new InputStreamReader(res.getContent()))) {
            String resContent = br.lines().collect(Collectors.joining("\n"));

            // The check for the string "successful" is necessitated by the API not always returning
            // a non-200 status code in the event of bad requests.
            if (res.getStatusCode() != 200 || !resContent.contains("successful")) {
                throw new IOException(
                        "unsuccessful response: " + res.getStatusCode() + " -> " + resContent);
            }
        } finally {
            res.disconnect();
        }
    }

    @Nullable
    private String buildUrl() {
        if (actdBuildUrl == null || actdBuildUrl.isEmpty()) {
            return null;
        }

        String sep = actdBuildUrl.endsWith("/") ? "" : "/";
        return actdBuildUrl + sep + buildId;
    }

    @Nullable
    private String commitUrl(@Nullable String hash) {
        if (actdCommitUrl == null || actdCommitUrl.isEmpty()) {
            return null;
        }

        String sep = actdCommitUrl.endsWith("/") ? "" : "/";
        return actdCommitUrl + sep + hash;
    }

    /**
     * Adds a Build to the act-d dashboard using the act-d API.
     *
     * <p>This Build should have a unique buildId and it is required that a Build exists before any
     * Samples can be added to the dashboard, because Samples have a relationship with Builds.
     *
     * @throws IOException if anything networky goes wrong, or if the API returns an unsuccessful
     *     response.
     */
    private void addBuild(@NonNull BuildRequest req) throws IOException {
        jsonPost(actdBaseUrl + ACTD_ADD_BUILD_URL, req);
    }

    /**
     * Adds a Sample to the act-d dashboard using the act-d API.
     *
     * <p>Samples each belong to a Build, and the Build must exist in the act-d API before you can
     * add a Sample. See {@code addBuild(BuildRequest)} for more information.
     *
     * @throws IOException if anything networky goes wrong, or if the API returns an unsuccessful
     *     response.
     */
    private void addSample(@NonNull SampleRequest req) throws IOException {
        jsonPost(actdBaseUrl + ACTD_ADD_SAMPLE_URL, req);
    }

    /**
     * Because there can be multiple of each task happening per build (e.g. if there are multiple
     * projects), we sum the times for each task and take the overall time spent doing that type of
     * task per build.
     */
    @VisibleForTesting
    @NonNull
    public Collection<SampleRequest> sampleRequests(@NonNull List<GradleBenchmarkResult> results)
            throws IOException {
        Map<String, SampleRequest> summedSeriesDurations = Maps.newHashMap();

        for (GradleBenchmarkResult result : results) {
            if (result.getProfile() == null) {
                // Shouldn't happen, but it's worth being defensive.
                continue;
            }

            for (GradleBuildProfileSpan span : result.getProfile().getSpanList()) {
                String seriesId = seriesId(result, span);
                SampleRequest sampleReq =
                        summedSeriesDurations.computeIfAbsent(
                                seriesId,
                                key -> {
                                    SampleRequest req = new SampleRequest();
                                    req.projectId = actdProjectId;
                                    req.serieId = seriesId;
                                    req.description = description(result, span);
                                    req.sample.buildId = buildId;
                                    req.sample.value = 0;
                                    req.sample.url = buildUrl();
                                    return req;
                                });

                sampleReq.sample.value += span.getDurationInMs();
            }
        }

        // act-d doesn't like samples with a value of 0, so we filter them out before returning
        return summedSeriesDurations
                .values()
                .stream()
                .filter(req -> req.sample.value > 0)
                .collect(Collectors.toList());
    }

    @VisibleForTesting
    @NonNull
    public BuildRequest buildRequest() throws IOException {
        BuildRequest buildReq = new BuildRequest();
        buildReq.projectId = actdProjectId;
        buildReq.build.infos = infos(repo);
        buildReq.build.infos.url = commitUrl(buildReq.build.infos.hash);
        buildReq.build.buildId = buildId;
        return buildReq;
    }

    @Override
    public void uploadData(@NonNull List<GradleBenchmarkResult> results) throws IOException {
        BuildRequest br = buildRequest();
        addBuild(br);

        for (SampleRequest sampleReq : sampleRequests(results)) {
            sampleReq.infos = br.build.infos;
            addSample(sampleReq);
        }

        System.out.println("successfully uploaded act-d data for build ID " + buildId);
    }

    /**
     * The following are just value classes for use with Gson to represent JSON requests to the
     * act-d API. All properties should be public and there should not be any methods associated
     * with them.
     */
    @VisibleForTesting
    public static final class Infos {
        public String hash;
        public String abbrevHash;
        public String authorName;
        public String authorEmail;
        public String subject;
        public String url;
    }

    @VisibleForTesting
    @SuppressWarnings("unused") // matches the structure act-d requires, not all params are used
    public static final class Build {
        public long buildId;
        public Infos infos;
    }

    @VisibleForTesting
    @SuppressWarnings("unused") // matches the structure act-d requires, not all params are used
    public static final class Sample {
        public long buildId;
        public long value;
        public String url;
    }

    @VisibleForTesting
    @SuppressWarnings("unused") // matches the structure act-d requires, not all params are used
    public static final class Benchmark {
        public String range = "10%";
        public int required = 3;
        public String trend = "smaller";
    }

    @VisibleForTesting
    public static final class Analyse {
        public Benchmark benchmark = new Benchmark();
    }

    @VisibleForTesting
    @SuppressWarnings("unused") // matches the structure act-d requires, not all params are used
    public static final class BuildRequest {
        public String projectId;
        public Build build = new Build();
    }

    @VisibleForTesting
    @SuppressWarnings("unused") // matches the structure act-d requires, not all params are used
    public static final class SampleRequest {
        public String projectId;
        public String serieId;
        public String description;
        public Infos infos;
        public Sample sample = new Sample();
        public Analyse analyse = new Analyse();
    }
}
