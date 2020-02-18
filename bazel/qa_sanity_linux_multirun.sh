#!/bin/bash -x
# Invoked by Android Build Launchcontrol for continuous builds.

# Invokes UI tests with a large value for runs_per_test. Expected to take a long
# time to finish.

# Expected arguments:
readonly out_dir="$1"
readonly dist_dir="$2"
readonly build_number="$3"

readonly script_dir="$(dirname "$0")"
readonly script_name="$(basename "$0")"

# Invalidate local cache to avoid picking up obsolete test result xmls
"${script_dir}/bazel" clean --async

config_options="--config=remote"
runs_per_test=2000


readonly invocation_id_sanity_longrunning="$(uuidgen)"

target_filters=qa_sanity,-qa_unreliable,-no_linux,-no_test_linux,-requires_emulator
"${script_dir}/bazel" \
  --max_idle_secs=60 \
  test \
  --runs_per_test=${runs_per_test} \
  --runs_per_test_detects_flakes \
  --jobs=200 \
  --keep_going \
  ${config_options} \
  --invocation_id=${invocation_id_sanity_longrunning} \
  --define=meta_android_build_number=${build_number} \
  --build_tag_filters=${target_filters} \
  --test_tag_filters=${target_filters} \
  --tool_tag=${script_name} \
  -- \
  //tools/adt/idea/android-uitests:CreateNewFlavorsTest

readonly bazel_status_sanity_longrunning=$?

if [[ -d "${dist_dir}" ]]; then
  echo "<meta http-equiv=\"refresh\" content=\"0; URL='https://source.cloud.google.com/results/invocations/${invocation_id_sanity_longrunning}'\" />" > "${dist_dir}"/upsalite_test_results.html
fi

exit $bazel_status_sanity_longrunning
