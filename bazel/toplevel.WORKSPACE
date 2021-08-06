load("//tools/base/bazel:repositories.bzl", "setup_external_repositories")
load("//tools/base/bazel:emulator.bzl", "setup_external_sdk")
load("@bazel_tools//tools/build_defs/repo:http.bzl", "http_archive")
load("//tools/base/bazel:maven.bzl", "local_maven_repository")

setup_external_repositories()

register_toolchains(
    "@native_toolchain//:cc-toolchain-x64_linux",
    "@native_toolchain//:cc-toolchain-darwin",
    "@native_toolchain//:cc-toolchain-x64_windows-clang-cl",
)

local_repository(
    name = "blaze",
    path = "tools/vendor/google3/blaze",
)

load("@blaze//:binds.bzl", "blaze_binds")

blaze_binds()

local_repository(
    name = "io_bazel_rules_kotlin",
    path = "tools/external/bazelbuild-rules-kotlin",
)

local_repository(
    name = "windows_toolchains",
    path = "tools/base/bazel/toolchains/windows",
)

# Bazel cannot auto-detect python on Windows yet
# See: https://github.com/bazelbuild/bazel/issues/7844
register_toolchains("@windows_toolchains//:python_toolchain")

http_archive(
    name = "bazel_toolchains",
    urls = [
        "https://mirror.bazel.build/github.com/bazelbuild/bazel-toolchains/archive/1.1.3.tar.gz",
        "https://github.com/bazelbuild/bazel-toolchains/archive/1.1.3.tar.gz",
    ],
    strip_prefix = "bazel-toolchains-1.1.3",
    sha256 = "83352b6e68fa797184071f35e3b67c7c8815efadcea81bb9cdb6bbbf2e07d389",
)

load(
    "@bazel_toolchains//repositories:repositories.bzl",
    bazel_toolchains_repositories = "repositories",
)

bazel_toolchains_repositories()

setup_external_sdk(
    name = "externsdk",
)

## Coverage related workspaces
# Coverage reports construction
local_repository(
    name = "cov",
    path = "tools/base/bazel/coverage",
)

# Coverage results processing
load("@cov//:results.bzl", "setup_testlogs_loop_repo")

setup_testlogs_loop_repo()

# Coverage baseline construction
load("@cov//:baseline.bzl", "setup_bin_loop_repo")

setup_bin_loop_repo()

load(
    "@bazel_toolchains//rules/exec_properties:exec_properties.bzl",
    "create_rbe_exec_properties_dict",
    "custom_exec_properties",
)

custom_exec_properties(
    name = "exec_properties",
    constants = {
        "LARGE_MACHINE": create_rbe_exec_properties_dict(
            labels = {"machine-size": "large"},
        ),
    },
)

# Download system images when needed by avd.
http_archive(
    name = "system_image_android-28_default_x86",
    url = "https://dl.google.com/android/repository/sys-img/android/x86-28_r04.zip",
    sha256 = "7c3615c55b64713fe56842a12fe6827d6792cb27a9f95f9fa3aee1ff1be47f16",
    strip_prefix = "x86",
    build_file = "//tools/base/bazel/avd:system_images.BUILD",
)
http_archive(
    name = "system_image_android-29_default_x86_64",
    url = "https://dl.google.com/android/repository/sys-img/android/x86_64-29_r06.zip",
    sha256 = "5d866d9925ad7b142c89bbffc9ce9941961e08747d6f64e28b5158cc44ad95cd",
    strip_prefix = "x86_64",
    build_file = "//tools/base/bazel/avd:system_images.BUILD",
)

# An empty local repository which must be overridden according to the instructions at
# go/agp-profiled-benchmarks if running the "_profiled" AGP build benchmarks.
local_repository(
    name = "yourkit_controller",
    path = "tools/base/yourkit-controller",
)

local_maven_repository(
    name = "maven",
    path = "prebuilts/tools/common/m2/repository/",
    resolve = True,
    artifacts = [
        "commons-codec:commons-codec:1.10",
        "commons-io:commons-io:2.4",
        "commons-logging:commons-logging:1.2",
        "com.android.tools.build:aapt2-proto:7.0.0-beta04-7396180",
        "com.android.tools.build:bundletool:1.7.0",
        "com.android.tools.build.jetifier:jetifier-core:1.0.0-beta09",
        "com.android.tools.build.jetifier:jetifier-processor:1.0.0-beta09",
        "com.android.tools.build:transform-api:2.0.0-deprecated-use-gradle-api",
        "com.googlecode.json-simple:json-simple:1.1",
        "com.googlecode.juniversalchardet:juniversalchardet:1.0.3",
        "com.google.auto:auto-common:0.10",
        "com.google.auto.value:auto-value:1.6.2",
        "com.google.code.findbugs:jsr305:3.0.2",
        "com.google.code.gson:gson:2.8.6",
        "com.google.crypto.tink:tink:1.3.0-rc2",
        "com.google.flatbuffers:flatbuffers-java:1.12.0",
        "com.google.guava:guava:30.1-jre",
        "com.google.jimfs:jimfs:1.1",
        "com.google.protobuf:protobuf-java:3.10.0",
        "com.google.protobuf:protobuf-java-util:3.10.0",
        "com.google.testing.platform:core-proto:0.0.8-alpha07",
        "com.google.truth:truth:0.44",
        "com.squareup:javapoet:1.10.0",
        "com.squareup:javawriter:2.5.0",
        "it.unimi.dsi:fastutil:8.4.0",
        "com.google.testing.platform:android-device-provider-local::0.0.8-alpha07",
        "com.google.testing.platform:core-proto:0.0.8-alpha07",
        "com.google.testing.platform:launcher:0.0.8-alpha07",
        "com.google.truth:truth:0.44",
        "commons-io:commons-io:2.4",
        "commons-logging:commons-logging:1.2",
        "io.grpc:grpc-all:1.21.1",
        "io.grpc:grpc-api:1.21.1",
        "jakarta.xml.bind:jakarta.xml.bind-api:2.3.2",
        "javax.annotation:javax.annotation-api:1.3.2",
        "javax.inject:javax.inject:1",
        "junit:junit:4.13.2",
        "net.java.dev.jna:jna:5.6.0",
        "net.java.dev.jna:jna-platform:5.6.0",
        "net.sf.jopt-simple:jopt-simple:4.9",
        "net.sf.kxml:kxml2:2.3.0",
        "nl.jqno.equalsverifier:equalsverifier:3.4.1",
        "org.antlr:antlr4:4.5.3",
        "org.apache.commons:commons-compress:1.20",
        "org.apache.httpcomponents:httpclient:4.5.6",
        "org.apache.httpcomponents:httpcore:4.4.10",
        "org.apache.httpcomponents:httpmime:4.5.6",
        "org.bouncycastle:bcpkix-jdk15on:1.56",
        "org.bouncycastle:bcprov-jdk15on:1.56",
        "org.codehaus.groovy:groovy-all:pom:3.0.8",
        "org.easymock:easymock:3.3",
        "org.glassfish.jaxb:jaxb-runtime:2.3.2",
        "org.jetbrains.dokka:dokka-core:1.4.32",
        "org.jetbrains.intellij.deps:trove4j:1.0.20181211",
        "org.jetbrains.kotlin:kotlin-reflect:1.4.32",
        "org.jetbrains.kotlin:kotlin-stdlib-jdk7:1.4.32",
        "org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.4.32",
        "org.jetbrains.kotlin:kotlin-stdlib:1.4.32",
        "org.jetbrains.kotlin:kotlin-stdlib-common:1.4.0",
        "org.jetbrains.kotlin:kotlin-test:1.4.32",
        "org.jetbrains.kotlinx:kotlinx-coroutines-core:1.3.8",
        "org.mockito:mockito-core:3.4.6",
        "org.mockito:mockito-all:1.9.5",
        "org.ow2.asm:asm-analysis:9.1",
        "org.ow2.asm:asm-commons:9.1",
        "org.ow2.asm:asm-tree:9.1",
        "org.ow2.asm:asm-util:9.1",
        "org.ow2.asm:asm:9.1",
        "org.smali:dexlib2:2.2.4",
        "org.smali:baksmali:2.2.4",
        "org.tensorflow:tensorflow-lite-metadata:0.1.0-rc2",
        "xerces:xercesImpl:2.12.0",
    ],
)

local_maven_repository(
    name = "maven_tests",
    path = "prebuilts/tools/common/m2/repository/",
    resolve = False,
    artifacts = [
        "android.arch.core:common:1.1.1",
        "android.arch.core:runtime:1.1.1",
        "android.arch.lifecycle:common:1.1.1",
        "android.arch.lifecycle:livedata-core:1.1.1",
        "android.arch.lifecycle:livedata:1.1.1",
        "android.arch.lifecycle:runtime:1.1.1",
        "android.arch.lifecycle:viewmodel:1.1.1",
        "android.arch.navigation:navigation-common:1.0.0",
        "android.arch.navigation:navigation-fragment:1.0.0",
        "androidx.activity:activity:1.0.0",
        "androidx.annotation:annotation:1.1.0",
        "androidx.appcompat:appcompat-resources:1.1.0",
        "androidx.appcompat:appcompat:1.1.0",
        "androidx.appcompat:appcompat:1.3.0",
        "androidx.arch.core:core-common:2.0.0",
        "androidx.arch.core:core-common:2.1.0",
        "androidx.arch.core:core-runtime:2.0.0",
        "androidx.collection:collection:1.0.0",
        "androidx.collection:collection:1.1.0",
        "androidx.core:core:1.1.0",
        "androidx.cursoradapter:cursoradapter:1.0.0",
        "androidx.customview:customview:1.0.0",
        "androidx.drawerlayout:drawerlayout:1.0.0",
        "androidx.fragment:fragment:1.1.0",
        "androidx.fragment:fragment:1.3.4",
        "androidx.interpolator:interpolator:1.0.0",
        "androidx.lifecycle:lifecycle-common:2.0.0",
        "androidx.lifecycle:lifecycle-common:2.1.0",
        "androidx.lifecycle:lifecycle-livedata-core:2.0.0",
        "androidx.lifecycle:lifecycle-livedata:2.0.0",
        "androidx.lifecycle:lifecycle-runtime:2.0.0",
        "androidx.lifecycle:lifecycle-runtime:2.1.0",
        "androidx.lifecycle:lifecycle-viewmodel:2.0.0",
        "androidx.lifecycle:lifecycle-viewmodel:2.1.0",
        "androidx.loader:loader:1.0.0",
        "androidx.navigation:navigation-fragment:2.3.5",
        "androidx.navigation:navigation-safe-args-generator:2.3.1",
        "androidx.navigation:navigation-safe-args-gradle-plugin:2.3.1",
        "androidx.savedstate:savedstate:1.0.0",
        "androidx.vectordrawable:vectordrawable-animated:1.1.0",
        "androidx.vectordrawable:vectordrawable:1.1.0",
        "androidx.versionedparcelable:versionedparcelable:1.1.0",
        "androidx.viewpager:viewpager:1.0.0",
        "com.android.support.constraint:constraint-layout-solver:1.0.2",
        "com.android.support.constraint:constraint-layout-solver:2.0.4",
        "com.android.support.constraint:constraint-layout:1.0.2",
        "com.android.support.constraint:constraint-layout:2.0.4",
        "com.android.support.test.espresso:espresso-core:3.0.2",
        "com.android.support.test.espresso:espresso-idling-resource:3.0.2",
        "com.android.support.test:monitor:1.0.2",
        "com.android.support.test:runner:1.0.2",
        "com.android.support:animated-vector-drawable:28.0.0",
        "com.android.support:appcompat-v7:28.0.0",
        "com.android.support:asynclayoutinflater:28.0.0",
        "com.android.support:collections:28.0.0",
        "com.android.support:coordinatorlayout:28.0.0",
        "com.android.support:cursoradapter:28.0.0",
        "com.android.support:customview:28.0.0",
        "com.android.support:design:28.0.0",
        "com.android.support:documentfile:28.0.0",
        "com.android.support:drawerlayout:28.0.0",
        "com.android.support:interpolator:28.0.0",
        "com.android.support:loader:28.0.0",
        "com.android.support:localbroadcastmanager:28.0.0",
        "com.android.support:print:28.0.0",
        "com.android.support:recyclerview-v7:28.0.0",
        "com.android.support:slidingpanelayout:28.0.0",
        "com.android.support:support-annotations:28.0.0",
        "com.android.support:support-compat:28.0.0",
        "com.android.support:support-core-ui:28.0.0",
        "com.android.support:support-core-utils:28.0.0",
        "com.android.support:support-fragment:28.0.0",
        "com.android.support:support-vector-drawable:28.0.0",
        "com.android.support:swiperefreshlayout:28.0.0",
        "com.android.support:versionedparcelable:28.0.0",
        "com.android.support:viewpager:28.0.0",
        "com.google.android.material:material:1.4.0",
        "com.google.auto.value:auto-value:1.6.2",
        "com.google.code.findbugs:jsr305:2.0.1",
        "com.google.errorprone:error_prone_annotations:2.3.2",
        "com.google.guava:guava:19.0",
        "com.google.jimfs:jimfs:1.1",
        "com.squareup:javapoet:1.12.1",
        "com.squareup:javawriter:2.1.1",
        "com.squareup:kotlinpoet:1.6.0",
        "com.sun.activation:javax.activation:1.2.0",
        "commons-lang:commons-lang:2.4",
        "javax.inject:javax.inject:1",
        "junit:junit:4.12",
        "org.codehaus.mojo:animal-sniffer-annotations:1.17",
        "org.hamcrest:hamcrest-core:1.3",
        "org.hamcrest:hamcrest-integration:1.3",
        "org.hamcrest:hamcrest-library:1.3",
        "org.jetbrains.intellij.deps:trove4j:1.0.20181211",
        "org.jetbrains.kotlin:kotlin-reflect:1.4.32",
        "org.jetbrains.kotlin:kotlin-stdlib-common:1.4.32",
        "org.jetbrains.kotlin:kotlin-stdlib-jdk7:1.4.32",
        "org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.5.0",
        "org.jetbrains.kotlin:kotlin-stdlib:1.4.32",
        "xmlpull:xmlpull:1.1.3.1",
        "xpp3:xpp3:1.1.4c",
    ],
)
