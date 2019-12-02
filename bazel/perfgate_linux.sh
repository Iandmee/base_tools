#!/bin/bash -x
# Invoked by Android Build Launchcontrol for continuous builds.

# Expected arguments:
readonly out_dir="$1"
readonly dist_dir="$2"
readonly build_number="$3"

readonly script_dir="$(dirname "$0")"
readonly script_name="$(basename "$0")"

build_tag_filters=-no_linux
test_tag_filters=perfgate,-no_linux,-no_test_linux

config_options="--config=remote"

# Generate a UUID for use as the bazel invocation id
readonly invocation_id="$(uuidgen)"

# Run Bazel
"${script_dir}/bazel" \
  --max_idle_secs=60 \
  test \
  --keep_going \
  ${config_options} \
  --invocation_id=${invocation_id} \
  --build_tag_filters=${build_tag_filters} \
  --define=meta_android_build_number=${build_number} \
  --build_event_binary_file="${dist_dir:-/tmp}/bazel-${build_number}.bes" \
  --test_tag_filters=${test_tag_filters} \
  --tool_tag=${script_name} \
  --profile=${dist_dir}/perfgate-profile-${build_number}.json.gz \
  --runs_per_test=5 \
  -- \
  //tools/base/bazel:perfgate_logs_collector_deploy.jar \
  $(< "${script_dir}/targets")

readonly bazel_status=$?

if [[ -d "${dist_dir}" ]]; then

  # Generate a simple html page that redirects to the test results page.
  echo "<meta http-equiv=\"refresh\" content=\"0; URL='https://source.cloud.google.com/results/invocations/${invocation_id}'\" />" > "${dist_dir}"/upsalite_test_results.html

  readonly java="prebuilts/studio/jdk/linux/jre/bin/java"
  readonly testlogs_dir="$("${script_dir}/bazel" info bazel-testlogs ${config_options})"
  readonly bin_dir="$("${script_dir}"/bazel info ${config_options} bazel-bin)"

  ${java} -jar ${bin_dir}/tools/base/bazel/perfgate_logs_collector_deploy.jar "${testlogs_dir}" "${dist_dir}/bazel-${build_number}.bes" "${dist_dir}/perfgate_data.zip" "${dist_dir}/logs/perfgate_logs_collector.log"

  # Upload all test logs
  find "${testlogs_dir}" -type f -name outputs.zip -exec zip -r "${dist_dir}/bazel_test_logs.zip" {} \;
fi

exit $bazel_status
