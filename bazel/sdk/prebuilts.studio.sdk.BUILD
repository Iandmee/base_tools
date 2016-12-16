load("//tools/base/bazel:bazel.bzl", "platform_filegroup")

filegroup(
    name = "licenses",
    srcs = glob(
        include = ["*/licenses/**"],
    ),
    visibility = ["//visibility:public"],
)

filegroup(
    name = "build-tools/latest",
    srcs = [":build-tools/25.0.1"],
    visibility = ["//visibility:public"],
)

filegroup(
    name = "build-tools/minimum",
    srcs = [":build-tools/25.0.0"],
    visibility = ["//visibility:public"],
)

filegroup(
    name = "build-tools/25.0.1",
    srcs = glob(
        include = ["*/build-tools/25.0.1/**"],
    ),
)

filegroup(
    name = "build-tools/25.0.0",
    srcs = glob(
        include = ["*/build-tools/25.0.0/**"],
    ),
    visibility = [
        "//tools/base/build-system/gradle:__pkg__",
    ],
)

filegroup(
    name = "build-tools/24.0.3",
    srcs = glob(
        include = ["*/build-tools/24.0.3/**"],
    ),
    visibility = [
        "//tools/base/build-system/integration-test:__pkg__",
    ],
)

filegroup(
    name = "platform-tools",
    srcs = glob(
        include = ["*/platform-tools/**"],
    ),
    visibility = ["//visibility:public"],
)

filegroup(
    name = "constraint-layout_latest",
    srcs = [":constraint-layout_1.0.0-beta4"],
    visibility = ["//visibility:public"],
)

filegroup(
    name = "constraint-layout_1.0.0-beta4",
    srcs = glob(
        include = [
            "*/extras/m2repository/com/android/support/constraint/constraint-layout/1.0.0-beta4/**",
            "*/extras/m2repository/com/android/support/constraint/constraint-layout-solver/1.0.0-beta4/**",
        ],
    ),
)

filegroup(
    name = "support_latest",
    srcs = [":support_25.1.0"],
    visibility = ["//visibility:public"],
)

filegroup(
    name = "support_25.1.0",
    srcs = glob(["*/extras/android/m2repository/com/android/support/*/25.1.0/**"]),
)

filegroup(
    name = "gms_latest",
    srcs = [":gms_9.6.1"],
    visibility = ["//visibility:public"],
)

filegroup(
    name = "gms_9.6.1",
    srcs = glob(["*/extras/google/m2repository/com/google/android/gms/*/9.6.1/**"]),
)

filegroup(
    name = "multidex",
    srcs = glob(["*/extras/android/m2repository/com/android/support/multidex*/1.0.1/**"]),
    visibility = ["//visibility:public"],
)

filegroup(
    name = "platforms/latest",
    srcs = [":platforms/android-25"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "platforms/latest_jar",
    jars = glob(["*/platforms/android-25/android.jar"]),
    neverlink = 1,
    visibility = ["//tools/base/instant-run/instant-run-server:__pkg__"],
)

filegroup(
    name = "typos",
    srcs = glob(["*/tools/support/typos-*.txt"]),
    visibility = ["//visibility:public"],
)

# Version-specific rule left private in hopes we can depend on platforms/latest instead.
# TODO: Migrate the packages below that depend on specific versions.
platform_filegroup(
    name = "platforms/android-25",
)

platform_filegroup(
    name = "platforms/android-24",
    visibility = [
        "//tools/base/build-system/gradle:__pkg__",
        "//tools/base/build-system/integration-test:__pkg__",
        "//tools/data-binding:__pkg__",
    ],
)

platform_filegroup(
    name = "platforms/android-23",
    visibility = ["//tools/base/build-system/integration-test:__pkg__"],
)

platform_filegroup(
    name = "platforms/android-21",
    visibility = ["//tools/base/build-system/integration-test:__pkg__"],
)

platform_filegroup(
    name = "platforms/android-19",
    visibility = ["//tools/base/build-system/integration-test:__pkg__"],
)

filegroup(
    name = "add-ons/addon-google_apis-google-latest",
    srcs = ["add-ons/addon-google_apis-google-24"],
    visibility = ["//visibility:public"],
)

filegroup(
    name = "add-ons/addon-google_apis-google-24",
    srcs = glob(["*/add-ons/addon-google_apis-google-24/**"]),
)

filegroup(
    name = "_android_jar",
    srcs = glob(["*/platforms/android-25/android.jar"]),
)

filegroup(
    name = "espresso_latest",
    srcs = [":espresso-2.2.2"],
    visibility = ["//visibility:public"],
)

filegroup(
    name = "espresso-2.2.2",
    srcs = glob(
        include = [
            "*/extras/android/m2repository/com/android/support/test/espresso/espresso-core/2.2.2/**",
            "*/extras/android/m2repository/com/android/support/test/espresso/espresso-idling-resource/2.2.2/**",
        ],
    ),
)

filegroup(
    name = "test-runner_latest",
    srcs = [":test-runner-0.5"],
    visibility = ["//visibility:public"],
)

filegroup(
    name = "test-runner-0.5",
    srcs = glob(
        include = [
            "*/extras/android/m2repository/com/android/support/test/exposed-instrumentation-api-publish/0.5/**",
            "*/extras/android/m2repository/com/android/support/test/rules/0.5/**",
            "*/extras/android/m2repository/com/android/support/test/runner/0.5/**",
        ],
    ),
)

filegroup(
    name = "docs",
    srcs = glob(["*/docs/**"]),
    visibility = ["//visibility:public"],
)

filegroup(
    name = "ndk-bundle",
    srcs = glob(["*/ndk-bundle/**"]),
    visibility = ["//visibility:public"],
)

filegroup(
    name = "cmake",
    srcs = glob(
        include = ["*/cmake/**"],
        exclude = ["*/cmake/**/Help/**"],
    ),
    visibility = ["//visibility:public"],
)
