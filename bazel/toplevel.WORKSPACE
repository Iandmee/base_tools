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
    # keep sorted: for buildifier
    artifacts = [
        "com.android.tools.build:aapt2-proto:7.0.0-beta04-7396180",
        "com.android.tools.build:bundletool:1.7.0",
        "com.android.tools.build.jetifier:jetifier-core:1.0.0-beta09",
        "com.android.tools.build.jetifier:jetifier-processor:1.0.0-beta09",
        "com.android.tools.build:transform-api:2.0.0-deprecated-use-gradle-api",
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
        "com.google.testing.platform:android-device-provider-local::0.0.8-alpha07",
        "com.google.testing.platform:core-proto:0.0.8-alpha07",
        "com.google.testing.platform:launcher:0.0.8-alpha07",
        "com.google.truth:truth:0.44",
        "com.googlecode.json-simple:json-simple:1.1",
        "com.googlecode.juniversalchardet:juniversalchardet:1.0.3",
        "com.squareup:javapoet:1.10.0",
        "com.squareup:javawriter:2.5.0",
        "commons-codec:commons-codec:1.10",
        "commons-io:commons-io:2.4",
        "commons-logging:commons-logging:1.2",
        "io.grpc:grpc-all:1.21.1",
        "io.grpc:grpc-api:1.21.1",
        "it.unimi.dsi:fastutil:8.4.0",
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
        "org.jetbrains.kotlin:kotlin-stdlib:1.4.32",
        "org.jetbrains.kotlin:kotlin-stdlib-common:1.4.0",
        "org.jetbrains.kotlin:kotlin-stdlib-jdk7:1.4.32",
        "org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.4.32",
        "org.jetbrains.kotlin:kotlin-test:1.4.32",
        "org.jetbrains.kotlinx:kotlinx-coroutines-core:1.3.8",
        "org.mockito:mockito-all:1.9.5",
        "org.mockito:mockito-core:3.4.6",
        "org.ow2.asm:asm:9.1",
        "org.ow2.asm:asm-analysis:9.1",
        "org.ow2.asm:asm-commons:9.1",
        "org.ow2.asm:asm-tree:9.1",
        "org.ow2.asm:asm-util:9.1",
        "org.smali:baksmali:2.2.4",
        "org.smali:dexlib2:2.2.4",
        "org.tensorflow:tensorflow-lite-metadata:0.1.0-rc2",
        "xerces:xercesImpl:2.12.0",
    ],
)

local_maven_repository(
    name = "maven_tests",
    path = "prebuilts/tools/common/m2/repository/",
    resolve = False,
    # keep sorted: for buildifier
    artifacts = [
        "android.arch.core:common:1.1.1",
        "android.arch.core:runtime:1.1.1",
        "android.arch.lifecycle:common:1.0.3",
        "android.arch.lifecycle:common:1.1.1",
        "android.arch.lifecycle:compiler:1.0.0",
        "android.arch.lifecycle:extensions:1.0.0",
        "android.arch.lifecycle:livedata:1.1.1",
        "android.arch.lifecycle:livedata-core:1.1.1",
        "android.arch.lifecycle:runtime:1.0.3",
        "android.arch.lifecycle:runtime:1.1.1",
        "android.arch.lifecycle:viewmodel:1.1.1",
        "android.arch.navigation:navigation-common:1.0.0",
        "android.arch.navigation:navigation-fragment:1.0.0",
        "android.arch.navigation:navigation-ui:1.0.0",
        "android.arch.persistence.room:runtime:1.0.0",
        "androidx.activity:activity-ktx:1.2.3",
        "androidx.annotation:annotation:1.2.0-beta01",
        "androidx.appcompat:appcompat:1.0.2",
        "androidx.appcompat:appcompat:1.3.0",
        "androidx.benchmark:benchmark-gradle-plugin:1.0.0",
        "androidx.compose.compiler:compiler:1.0.0",
        "androidx.compose.ui:ui-test-junit4:1.0.0",
        "androidx.compose.ui:ui-tooling:1.0.0",
        "androidx.constraintlayout:constraintlayout:1.1.3",
        "androidx.constraintlayout:constraintlayout:2.0.4",
        "androidx.core:core:1.5.0-beta01",
        "androidx.core:core:1.5.0-rc02",
        "androidx.core:core-ktx:1.0.1",
        "androidx.core:core-ktx:1.2.0",
        "androidx.core:core-ktx:1.3.0",
        "androidx.core:core-ktx:1.3.2",
        "androidx.core:core-ktx:1.6.0",
        "androidx.fragment:fragment:1.3.0",
        "androidx.fragment:fragment:1.3.0-rc01",
        "androidx.leanback:leanback:1.0.0",
        "androidx.legacy:legacy-support-v13:1.0.0",
        "androidx.lifecycle:lifecycle-extensions:2.2.0",
        "androidx.lifecycle:lifecycle-livedata-ktx:2.2.0",
        "androidx.lifecycle:lifecycle-livedata-ktx:2.3.1",
        "androidx.media:media:1.3.1",
        "androidx.navigation:navigation-fragment-ktx:2.3.5",
        "androidx.navigation:navigation-safe-args-gradle-plugin:2.3.1",
        "androidx.navigation:navigation-ui-ktx:2.3.5",
        "androidx.palette:palette-ktx:1.0.0",
        "androidx.recyclerview:recyclerview:1.2.1",
        "androidx.slice:slice-builders:1.0.0",
        "androidx.test.espresso:espresso-core:3.1.0",
        "androidx.test.espresso:espresso-core:3.2.0",
        "androidx.test.ext:junit:1.1.1",
        "androidx.wear:wear:1.1.0",
        "com.android.support:animated-vector-drawable:26.1.0",
        "com.android.support:appcompat-v7:25.4.0",
        "com.android.support:appcompat-v7:26.1.0",
        "com.android.support:cardview-v7:26.1.0",
        "com.android.support.constraint:constraint-layout:1.0.2",
        "com.android.support.constraint:constraint-layout:1.1.0",
        "com.android.support.constraint:constraint-layout:1.1.3",
        "com.android.support.constraint:constraint-layout:2.0.4",
        "com.android.support:multidex:1.0.1",
        "com.android.support:multidex-instrumentation:1.0.2",
        "com.android.support:recyclerview-v7:27.1.1",
        "com.android.support:support-v13:28.0.0",
        "com.android.support:support-v4:27.1.1",
        "com.android.support:support-vector-drawable:26.1.0",
        "com.android.support.test.espresso:espresso-core:3.0.1",
        "com.android.support.test.espresso:espresso-core:3.0.2",
        "com.android.support.test.uiautomator:uiautomator-v18:2.1.1",
        "com.android.tools:desugar_jdk_libs:1.0.5",
        "com.android.tools.lint:lint-checks:26.5.0",
        "com.facebook.testing.screenshot:core:0.2.3",
        "com.github.bumptech.glide:glide:4.11.0",
        "com.google.android:android:4.1.1.4",
        "com.google.android.gms:play-services-ads:20.2.0",
        "com.google.android.gms:play-services-base:17.6.0",
        "com.google.android.gms:play-services-gass:20.0.0",
        "com.google.android.gms:play-services-maps:17.0.1",
        "com.google.android.gms:play-services-wallet:18.1.3",
        "com.google.android.gms:play-services-wearable:17.1.0",
        "com.google.android.libraries.mapsplatform.secrets-gradle-plugin:secrets-gradle-plugin:1.3.0",
        "com.google.android.material:material:1.1.0",
        "com.google.android.material:material:1.3.0",
        "com.google.android.material:material:1.4.0",
        "com.google.android.support:wearable:2.8.1",
        "com.google.android.wearable:wearable:2.8.1",
        "com.google.auto.value:auto-value:1.4-rc1",
        "com.google.auto.value:auto-value:1.6.2",
        "com.google.code.gson:gson:2.2.4",
        "com.google.code.gson:gson:2.6.2",
        "com.google.dagger:dagger-compiler:2.6",
        "com.google.dexmaker:dexmaker-mockito:1.2",
        "com.google.firebase:firebase-common:12.0.1",
        "com.google.guava:guava:28.2-jre",
        "com.google.truth:truth:0.44",
        "com.jakewharton.auto.value:auto-value-annotations:1.2-update1",
        "com.linkedin.dexmaker:dexmaker-mockito:2.19.0",
        "com.linkedin.testbutler:test-butler-app:1.3.1",
        "commons-codec:commons-codec:1.9",
        "commons-lang:commons-lang:2.4",
        "io.opentracing:opentracing-impl:0.13.0",
        "javax.annotation:javax.annotation-api:1.2",
        "javax.annotation:jsr250-api:1.0",
        "junit:junit:4.13.2",
        "org.jdeferred:jdeferred-android-aar:1.2.3",
        "org.jetbrains.kotlin:kotlin-android-extensions-runtime:1.4.32",
        "org.jetbrains.kotlin:kotlin-android-extensions-runtime:1.5.10",
        "org.jetbrains.kotlin:kotlin-android-extensions-runtime:1.5.20",
        "org.jetbrains.kotlin:kotlin-gradle-plugin:1.4.32",
        "org.jetbrains.kotlin:kotlin-gradle-plugin:1.5.10",
        "org.jetbrains.kotlin:kotlin-gradle-plugin:1.5.20",
        "org.jetbrains.kotlin:kotlin-reflect:1.4.31",
        "org.jetbrains.kotlin:kotlin-reflect:1.5.10",
        "org.jetbrains.kotlin:kotlin-reflect:1.5.20",
        "org.jetbrains.kotlin:kotlin-reflect:1.5.21",
        "org.jetbrains.kotlin:kotlin-script-runtime:1.5.10",
        "org.jetbrains.kotlin:kotlin-script-runtime:1.5.20",
        "org.jetbrains.kotlin:kotlin-stdlib-jdk7:1.4.32",
        "org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.4.31",
        "org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.5.10",
        "org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.5.20",
        "org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.5.21",
        "org.mockito:mockito-core:3.0.0",
    ],
)
