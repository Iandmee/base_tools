/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http:>>www.apache.org>licenses>LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.build.gradle.internal.cxx.configure

import com.android.build.gradle.internal.cxx.RandomInstanceGenerator
import com.android.build.gradle.internal.cxx.caching.CachingEnvironment
import com.android.build.gradle.internal.cxx.configure.SdkSourceProperties.Companion.SdkSourceProperty.SDK_PKG_REVISION
import com.android.build.gradle.internal.cxx.logging.LoggingLevel
import com.android.build.gradle.internal.cxx.logging.LoggingMessage
import com.android.build.gradle.internal.cxx.logging.PassThroughDeduplicatingLoggingEnvironment
import com.android.build.gradle.internal.cxx.logging.ThreadLoggingEnvironment
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class NdkLocatorKtTest {
    class TestLoggingEnvironment() : ThreadLoggingEnvironment() {
        val messages = mutableListOf<LoggingMessage>()
        override fun log(message: LoggingMessage) {
            messages.add(message)
        }
        fun errors() = messages.filter { it -> it.level == LoggingLevel.ERROR }
        fun warnings() = messages.filter { it -> it.level == LoggingLevel.WARN }
    }

    @get:Rule
    val temporaryFolder = TemporaryFolder()
    val log = TestLoggingEnvironment()

    private fun String.toSlash(): String {
        return replace("/", File.separator)
    }

    private fun String?.toSlashFile() = if (this == null) null else File(toSlash())

    private fun findNdkPath(
        ndkVersionFromDsl: String?,
        ndkDirProperty: String?,
        sdkFolder: File?,
        getNdkVersionedFolderNames: (File) -> List<String>,
        getNdkSourceProperties: (File) -> SdkSourceProperties?
    ) =
        CachingEnvironment(temporaryFolder.newFolder()).use {
            findNdkPathImpl(
                ndkVersionFromDsl,
                ndkDirProperty,
                sdkFolder,
                getNdkVersionedFolderNames,
                getNdkSourceProperties
            )
        }

    @Test
    fun getVersionedFolderNames() {
        val versionRoot = temporaryFolder.newFolder("versionedRoot")
        val v1 = versionRoot.resolve("17.1.2")
        val v2 = versionRoot.resolve("18.1.2")
        val f1 = versionRoot.resolve("my-file")
        v1.mkdirs()
        v2.mkdirs()
        f1.writeText("touch")
        assertThat(getNdkVersionedFolders(versionRoot)).containsExactly(
            "17.1.2", "18.1.2"
        )
    }

    @Test
    fun getVersionedFolderNamesNonExistent() {
        val versionRoot = "./getVersionedFolderNamesNonExistent".toSlashFile()!!
        assertThat(getNdkVersionedFolders(versionRoot).toList()).isEmpty()
    }

    @Test
    fun getNdkVersionInfoNoFolder() {
        val versionRoot = "./non-existent-folder".toSlashFile()!!
        assertThat(getNdkVersionInfo(versionRoot)).isNull()
    }

    @Test
    fun `non-existing ndk dir without NDK version in DSL (bug 129789776)`() {
        val path =
            findNdkPath(
                ndkVersionFromDsl = null,
                ndkDirProperty = "/my/ndk/folder".toSlash(),
                sdkFolder = null,
                getNdkVersionedFolderNames = { listOf() },
                getNdkSourceProperties = { path ->
                    when (path.path) {
                        "/my/ndk/folder".toSlash() -> null
                        "/my/ndk/environment-folder".toSlash() -> SdkSourceProperties(
                            mapOf(
                                SDK_PKG_REVISION.key to ANDROID_GRADLE_PLUGIN_FIXED_DEFAULT_NDK_VERSION
                            )
                        )
                        else -> throw RuntimeException(path.path)
                    }
                })
        assertThat(path).isNull()
    }

    @Test
    fun `non-existing ndk dir without NDK version in DSL and with side-by-side versions available (bug 129789776)`() {
        val path =
            findNdkPath(
                ndkVersionFromDsl = null,
                ndkDirProperty = "/my/ndk/folder".toSlash(),
                sdkFolder = "/my/sdk/folder".toSlashFile(),
                getNdkVersionedFolderNames = { listOf("18.1.00000", "18.1.23456") },
                getNdkSourceProperties = { path ->
                    when (path.path) {
                        "/my/sdk/folder/ndk/18.1.23456".toSlash() -> SdkSourceProperties(
                            mapOf(
                                SDK_PKG_REVISION.key to "18.1.23456"
                            )
                        )
                        "/my/sdk/folder/ndk/18.1.00000".toSlash() -> SdkSourceProperties(
                            mapOf(
                                SDK_PKG_REVISION.key to "18.1.00000"
                            )
                        )
                        else -> null
                    }
                })
        assertThat(path).isNull()
    }

    @Test
    fun `same version in legacy folder and side-by-side folder (bug 129488603)`() {
        val path = findNdkPath(
            ndkVersionFromDsl = "18.1",
            ndkDirProperty = null,
            sdkFolder = "/my/sdk/folder".toSlashFile(),
            getNdkVersionedFolderNames = { listOf("18.1.00000", "18.1.23456") },
            getNdkSourceProperties = { path ->
                when (path.path) {
                    "/my/sdk/folder/ndk/18.1.23456".toSlash() -> SdkSourceProperties(
                        mapOf(
                            SDK_PKG_REVISION.key to "18.1.23456"
                        )
                    )
                    "/my/sdk/folder/ndk-bundle".toSlash() -> SdkSourceProperties(
                        mapOf(
                            SDK_PKG_REVISION.key to "18.1.23456"
                        )
                    )
                    else -> null
                }
            })
        assertThat(path).isNull()
    }

    @Test
    fun ndkNotConfigured() {
        findNdkPath(
            ndkVersionFromDsl = null,
            ndkDirProperty = null,
            sdkFolder = null,
            getNdkVersionedFolderNames = { listOf() },
            getNdkSourceProperties = { null }
        )
        assertThat(log.errors()).hasSize(0) // Only expect a warning
    }

    @Test
    fun ndkDirPropertyLocationDoesntExist() {
        val path = findNdkPath(
            ndkVersionFromDsl = null,
            ndkDirProperty = "/my/ndk/folder".toSlash(),
            sdkFolder = null,
            getNdkVersionedFolderNames = { listOf() },
            getNdkSourceProperties = { null }
        )
        assertThat(path).isNull()
    }

    @Test
    fun ndkDirPropertyLocationExists() {
        val path = findNdkPath(
            ndkVersionFromDsl = null,
            ndkDirProperty = "/my/ndk/folder".toSlash(),
            sdkFolder = null,
            getNdkVersionedFolderNames = { listOf() },
            getNdkSourceProperties = { path ->
                when (path.path) {
                    "/my/ndk/folder".toSlash() -> SdkSourceProperties(
                        mapOf(
                            SDK_PKG_REVISION.key to ANDROID_GRADLE_PLUGIN_FIXED_DEFAULT_NDK_VERSION
                        )
                    )
                    else -> throw RuntimeException(path.path)
                }
            })
        assertThat(path).isEqualTo("/my/ndk/folder".toSlashFile())
    }

    @Test
    fun `ndk dir properties has -rc2 in version`() {
        val path = findNdkPath(
            ndkVersionFromDsl = "21.0.6011959-rc2",
            ndkDirProperty = "/my/ndk/folder".toSlash(),
            sdkFolder = null,
            getNdkVersionedFolderNames = { listOf() },
            getNdkSourceProperties = { path ->
                when (path.path) {
                    "/my/ndk/folder".toSlash() -> SdkSourceProperties(
                        mapOf(
                            SDK_PKG_REVISION.key to "21.0.6011959-rc2"
                        )
                    )
                    else -> throw RuntimeException(path.path)
                }
            })
        assertThat(path).isEqualTo("/my/ndk/folder".toSlashFile())
    }

    @Test
    fun nonExistingNdkDirWithNdkVersionInDsl() {
        findNdkPath(
            ndkVersionFromDsl = "18.1",
            ndkDirProperty = "/my/ndk/folder".toSlash(),
            sdkFolder = null,
            getNdkVersionedFolderNames = { listOf() },
            getNdkSourceProperties = { path ->
                when (path.path) {
                    "/my/ndk/folder".toSlash() -> null
                    "/my/ndk/environment-folder".toSlash() -> SdkSourceProperties(
                        mapOf(
                            SDK_PKG_REVISION.key to "18.1.23456"
                        )
                    )
                    else -> throw RuntimeException(path.path)
                }
            })
    }

    @Test
    fun sdkFolderNdkBundleExists() {
        val path = findNdkPath(
            ndkVersionFromDsl = null,
            ndkDirProperty = null,
            sdkFolder = "/my/sdk/folder".toSlashFile(),
            getNdkVersionedFolderNames = { listOf() },
            getNdkSourceProperties = { path ->
                when (path.path) {
                    "/my/sdk/folder/ndk-bundle".toSlash() -> SdkSourceProperties(
                        mapOf(
                            SDK_PKG_REVISION.key to ANDROID_GRADLE_PLUGIN_FIXED_DEFAULT_NDK_VERSION
                        )
                    )
                    else -> null
                }
            })
        assertThat(path).isEqualTo("/my/sdk/folder/ndk-bundle".toSlashFile())
    }

    @Test
    fun `no version specified by user and only old ndk available (bug 148189425)`() {
        val path = findNdkPath(
            ndkVersionFromDsl = null,
            ndkDirProperty = null,
            sdkFolder = "/my/sdk/folder".toSlashFile(),
            getNdkVersionedFolderNames = { listOf("18.1.23456") },
            getNdkSourceProperties = { path ->
                when (path.path) {
                    "/my/sdk/folder/ndk/18.1.23456".toSlash() -> SdkSourceProperties(
                        mapOf(SDK_PKG_REVISION.key to "18.1.23456")
                    )
                    else -> null
                }
            }
        )
        assertThat(path).isEqualTo(null)
        assertThat(log.errors()).hasSize(0)
        assertThat(log.warnings()).hasSize(1)
        assertThat(log.warnings()[0].message).isEqualTo(
            "No version of NDK matched the required version " +
                    "$ANDROID_GRADLE_PLUGIN_FIXED_DEFAULT_NDK_VERSION. Versions available " +
                    "locally: 18.1.23456"
        )
    }

    @Test
    fun `version specified by user and only old ndk available (bug 148189425)`() {
        val path = findNdkPath(
            ndkVersionFromDsl = "18.9.99999",
            ndkDirProperty = null,
            sdkFolder = "/my/sdk/folder".toSlashFile(),
            getNdkVersionedFolderNames = { listOf("18.1.23456") },
            getNdkSourceProperties = { path ->
                when (path.path) {
                    "/my/sdk/folder/ndk/18.1.23456".toSlash() -> SdkSourceProperties(
                        mapOf(SDK_PKG_REVISION.key to "18.1.23456")
                    )
                    else -> null
                }
            }
        )
        assertThat(path).isEqualTo(null)
        assertThat(log.errors()).hasSize(0)
        assertThat(log.warnings()).hasSize(1)
        assertThat(log.warnings()[0].message).isEqualTo(
            "No version of NDK matched the required version 18.9.99999. Versions " +
                    "available locally: 18.1.23456"
        )
    }

    @Test
    fun ndkNotConfiguredWithDslVersion() {
        val path = findNdkPath(
            ndkVersionFromDsl = "18.1.23456",
            ndkDirProperty = null,
            sdkFolder = null,
            getNdkVersionedFolderNames = { listOf() },
            getNdkSourceProperties = { null }
        )
        assertThat(path).isNull()
        assertThat(log.errors()).hasSize(0) // Only expect a warning
    }

    @Test
    fun `ndk rc configured with space-rc1 version in DSL`() {
        val path = findNdkPath(
            ndkVersionFromDsl = "18.1.23456 rc1",
            ndkDirProperty = null,
            sdkFolder = "/my/sdk/folder".toSlashFile(),
            getNdkVersionedFolderNames = { listOf("18.1.23456") },
            getNdkSourceProperties = { path ->
                when (path.path) {
                    "/my/sdk/folder/ndk/18.1.23456".toSlash() -> SdkSourceProperties(
                        mapOf(
                            SDK_PKG_REVISION.key to "18.1.23456 rc1"
                        )
                    )
                    else -> null
                }
            })
        assertThat(path).isEqualTo("/my/sdk/folder/ndk/18.1.23456".toSlashFile())
    }

    @Test
    fun `ndk rc configured with dash-rc1 version in DSL`() {
        val path = findNdkPath(
            ndkVersionFromDsl = "18.1.23456-rc1",
            ndkDirProperty = null,
            sdkFolder = "/my/sdk/folder".toSlashFile(),
            getNdkVersionedFolderNames = { listOf("18.1.23456") },
            getNdkSourceProperties = { path ->
                when (path.path) {
                    "/my/sdk/folder/ndk/18.1.23456".toSlash() -> SdkSourceProperties(
                        mapOf(
                            SDK_PKG_REVISION.key to "18.1.23456 rc1"
                        )
                    )
                    else -> null
                }
            })
        assertThat(path).isEqualTo("/my/sdk/folder/ndk/18.1.23456".toSlashFile())
    }

    @Test
    fun ndkDirPropertyLocationDoesntExistWithDslVersion() {
        val path = findNdkPath(
            ndkVersionFromDsl = "18.1.23456",
            ndkDirProperty = "/my/ndk/folder".toSlash(),
            sdkFolder = null,
            getNdkVersionedFolderNames = { listOf() },
            getNdkSourceProperties = { null }
        )
        assertThat(path).isNull()
    }

    @Test
    fun ndkDirPropertyLocationExistsWithDslVersion() {
        val path = findNdkPath(
            ndkVersionFromDsl = "18.1.23456",
            ndkDirProperty = "/my/ndk/folder".toSlash(),
            sdkFolder = null,
            getNdkVersionedFolderNames = { listOf() },
            getNdkSourceProperties = { path ->
                when (path.path) {
                    "/my/ndk/folder".toSlash() -> SdkSourceProperties(
                        mapOf(
                            SDK_PKG_REVISION.key to "18.1.23456"
                        )
                    )
                    else -> throw RuntimeException(path.path)
                }
            })
        assertThat(path).isEqualTo("/my/ndk/folder".toSlashFile())
    }

    @Test
    fun sdkFolderNdkBundleExistsWithDslVersion() {
        val path = findNdkPath(
            ndkVersionFromDsl = "18.1.23456",
            ndkDirProperty = null,
            sdkFolder = "/my/sdk/folder".toSlashFile(),
            getNdkVersionedFolderNames = { listOf() },
            getNdkSourceProperties = { path ->
                when (path.path) {
                    "/my/sdk/folder/ndk-bundle".toSlash() -> SdkSourceProperties(
                        mapOf(
                            SDK_PKG_REVISION.key to "18.1.23456"
                        )
                    )
                    else -> null
                }
            })
        assertThat(path).isEqualTo("/my/sdk/folder/ndk-bundle".toSlashFile())
    }

    @Test
    fun ndkNotConfiguredWithVersionedNdk() {
        val path = findNdkPath(
            ndkVersionFromDsl = null,
            ndkDirProperty = null,
            sdkFolder = null,
            getNdkVersionedFolderNames = { listOf(ANDROID_GRADLE_PLUGIN_FIXED_DEFAULT_NDK_VERSION) },
            getNdkSourceProperties = { null }
        )
        assertThat(path).isNull()
        assertThat(log.errors()).hasSize(0) // Only expect a warning
    }

    @Test
    fun ndkDirPropertyLocationDoesntExistWithVersionedNdk() {
        val path = findNdkPath(
            ndkVersionFromDsl = null,
            ndkDirProperty = "/my/ndk/folder".toSlash(),
            sdkFolder = null,
            getNdkVersionedFolderNames = { listOf("18.1.23456") },
            getNdkSourceProperties = { null }
        )
        assertThat(path).isNull()
    }

    @Test
    fun ndkDirPropertyLocationExistsWithVersionedNdk() {
        val path = findNdkPath(
            ndkVersionFromDsl = null,
            ndkDirProperty = "/my/ndk/folder".toSlash(),
            sdkFolder = null,
            getNdkVersionedFolderNames = { listOf(ANDROID_GRADLE_PLUGIN_FIXED_DEFAULT_NDK_VERSION) },
            getNdkSourceProperties = { path ->
                when (path.path) {
                    "/my/ndk/folder".toSlash() -> SdkSourceProperties(
                        mapOf(
                            SDK_PKG_REVISION.key to ANDROID_GRADLE_PLUGIN_FIXED_DEFAULT_NDK_VERSION
                        )
                    )
                    else -> throw RuntimeException(path.path)
                }
            })
        assertThat(path).isEqualTo("/my/ndk/folder".toSlashFile())
    }

    @Test
    fun sdkFolderNdkBundleExistsWithVersionedNdk() {
        val path = findNdkPath(
            ndkVersionFromDsl = null,
            ndkDirProperty = null,
            sdkFolder = "/my/sdk/folder".toSlashFile(),
            getNdkVersionedFolderNames = { listOf(ANDROID_GRADLE_PLUGIN_FIXED_DEFAULT_NDK_VERSION) },
            getNdkSourceProperties = { path ->
                when (path.path) {
                    "/my/sdk/folder/ndk/$ANDROID_GRADLE_PLUGIN_FIXED_DEFAULT_NDK_VERSION".toSlash()
                    -> SdkSourceProperties(mapOf(SDK_PKG_REVISION.key to ANDROID_GRADLE_PLUGIN_FIXED_DEFAULT_NDK_VERSION))
                    else -> null
                }
            })
        assertThat(path).isEqualTo("/my/sdk/folder/ndk/$ANDROID_GRADLE_PLUGIN_FIXED_DEFAULT_NDK_VERSION".toSlashFile())
    }

    @Test
    fun ndkNotConfiguredWithDslVersionWithVersionedNdk() {
        val path = findNdkPath(
            ndkVersionFromDsl = "18.1.23456",
            ndkDirProperty = null,
            sdkFolder = null,
            getNdkVersionedFolderNames = { listOf("18.1.23456") },
            getNdkSourceProperties = { null }
        )
        assertThat(path).isNull()
        assertThat(log.errors()).hasSize(0) // Only expect a warning
    }

    @Test
    fun ndkDirPropertyLocationDoesntExistWithDslVersionWithVersionedNdk() {
        val path = findNdkPath(
            ndkVersionFromDsl = "18.1.23456",
            ndkDirProperty = "/my/ndk/folder".toSlash(),
            sdkFolder = null,
            getNdkVersionedFolderNames = { listOf("18.1.23456") },
            getNdkSourceProperties = { null }
        )
        assertThat(path).isNull()
    }

    @Test
    fun ndkDirPropertyLocationExistsWithDslVersionWithVersionedNdk() {
        val path = findNdkPath(
            ndkVersionFromDsl = "18.1.23456",
            ndkDirProperty = "/my/ndk/folder".toSlash(),
            sdkFolder = null,
            getNdkVersionedFolderNames = { listOf("18.1.23456") },
            getNdkSourceProperties = { path ->
                when (path.path) {
                    "/my/ndk/folder".toSlash() -> SdkSourceProperties(
                        mapOf(
                            SDK_PKG_REVISION.key to "18.1.23456"
                        )
                    )
                    else -> throw RuntimeException(path.path)
                }
            })
        assertThat(path).isEqualTo("/my/ndk/folder".toSlashFile())
    }

    @Test
    fun sdkFolderNdkBundleExistsWithDslVersionWithVersionedNdk() {
        val path = findNdkPath(
            ndkVersionFromDsl = "18.1.23456",
            ndkDirProperty = null,
            sdkFolder = "/my/sdk/folder".toSlashFile(),
            getNdkVersionedFolderNames = { listOf("18.1.23456") },
            getNdkSourceProperties = { path ->
                when (path.path) {
                    "/my/sdk/folder/ndk/18.1.23456".toSlash() -> SdkSourceProperties(
                        mapOf(
                            SDK_PKG_REVISION.key to "18.1.23456"
                        )
                    )
                    else -> null
                }
            })
        assertThat(path).isEqualTo("/my/sdk/folder/ndk/18.1.23456".toSlashFile())
    }

    @Test
    fun multipleMatchingVersions1() {
        val path = findNdkPath(
            ndkVersionFromDsl = "18.1",
            ndkDirProperty = null,
            sdkFolder = "/my/sdk/folder".toSlashFile(),
            getNdkVersionedFolderNames = { listOf("18.1.23456", "18.1.99999") },
            getNdkSourceProperties = { path ->
                when (path.path) {
                    "/my/sdk/folder/ndk/18.1.23456".toSlash() -> SdkSourceProperties(
                        mapOf(
                            SDK_PKG_REVISION.key to "18.1.23456"
                        )
                    )
                    "/my/sdk/folder/ndk/18.1.99999".toSlash() -> SdkSourceProperties(
                        mapOf(
                            SDK_PKG_REVISION.key to "18.1.99999"
                        )
                    )
                    else -> null
                }
            })
        assertThat(path).isNull()
    }

    @Test
    fun multipleMatchingVersions2() {
        val path = findNdkPath(
            ndkVersionFromDsl = "18.1",
            ndkDirProperty = null,
            sdkFolder = "/my/sdk/folder".toSlashFile(),
            getNdkVersionedFolderNames = { listOf("18.1.00000", "18.1.23456") },
            getNdkSourceProperties = { path ->
                when (path.path) {
                    "/my/sdk/folder/ndk/18.1.23456".toSlash() -> SdkSourceProperties(
                        mapOf(
                            SDK_PKG_REVISION.key to "18.1.23456"
                        )
                    )
                    "/my/sdk/folder/ndk/18.1.00000".toSlash() -> SdkSourceProperties(
                        mapOf(
                            SDK_PKG_REVISION.key to "18.1.00000"
                        )
                    )
                    else -> null
                }
            })
        assertThat(path).isNull()
    }

    @Test
    fun ndkNotConfiguredWithWrongDslVersion() {
        val path = findNdkPath(
            ndkVersionFromDsl = "17.1.23456",
            ndkDirProperty = null,
            sdkFolder = null,
            getNdkVersionedFolderNames = { listOf() },
            getNdkSourceProperties = { null }
        )
        assertThat(path).isNull()
        assertThat(log.errors()).hasSize(0) // Only expect a warning
    }

    @Test
    fun ndkDirPropertyLocationDoesntExistWithWrongDslVersion() {
        val path = findNdkPath(
            ndkVersionFromDsl = "17.1.23456",
            ndkDirProperty = "/my/ndk/folder".toSlash(),
            sdkFolder = null,
            getNdkVersionedFolderNames = { listOf() },
            getNdkSourceProperties = { null }
        )
        assertThat(path).isNull()
    }

    @Test
    fun sdkFolderNdkBundleExistsWithWrongDslVersion() {
        val path =
            findNdkPath(
                ndkVersionFromDsl = "17.1.23456",
                ndkDirProperty = null,
                sdkFolder = "/my/sdk/folder".toSlashFile(),
                getNdkVersionedFolderNames = { listOf() },
                getNdkSourceProperties = { path ->
                    when (path.path) {
                        "/my/sdk/folder/ndk-bundle".toSlash() -> SdkSourceProperties(
                            mapOf(
                                SDK_PKG_REVISION.key to "18.1.23456"
                            )
                        )
                        else -> null
                    }
                })
        assertThat(path).isNull()
    }

    @Test
    fun ndkNotConfiguredWithWrongDslVersionWithVersionedNdk() {
        val path = findNdkPath(
            ndkVersionFromDsl = "17.1.23456",
            ndkDirProperty = null,
            sdkFolder = null,
            getNdkVersionedFolderNames = { listOf("18.1.23456") },
            getNdkSourceProperties = { null }
        )
        assertThat(path).isNull()
        assertThat(log.errors()).hasSize(0) // Only expect a warning
    }

    @Test
    fun ndkDirPropertyLocationDoesntExistWithWrongDslVersionWithVersionedNdk() {
        val path = findNdkPath(
            ndkVersionFromDsl = "17.1.23456",
            ndkDirProperty = "/my/ndk/folder".toSlash(),
            sdkFolder = null,
            getNdkVersionedFolderNames = { listOf("18.1.23456") },
            getNdkSourceProperties = { null }
        )
        assertThat(path).isNull()
    }

    @Test
    fun sdkFolderNdkBundleExistsWithWrongDslVersionWithVersionedNdk() {
        val path =
            findNdkPath(
                ndkVersionFromDsl = "17.1.23456",
                ndkDirProperty = null,
                sdkFolder = "/my/sdk/folder".toSlashFile(),
                getNdkVersionedFolderNames = { listOf("18.1.23456") },
                getNdkSourceProperties = { path ->
                    when (path.path) {
                        "/my/sdk/folder/ndk/18.1.23456".toSlash() -> SdkSourceProperties(
                            mapOf(
                                SDK_PKG_REVISION.key to "18.1.23456"
                            )
                        )
                        else -> null
                    }
                })
        assertThat(path).isNull()
    }

    @Test
    fun unparseableNdkVersionFromDsl() {
        val path =
            findNdkPath(
                ndkVersionFromDsl = "17.1.unparseable",
                ndkDirProperty = null,
                sdkFolder = "/my/sdk/folder".toSlashFile(),
                getNdkVersionedFolderNames = { listOf("18.1.23456") },
                getNdkSourceProperties = { path ->
                    when (path.path) {
                        "/my/sdk/folder/ndk/18.1.23456".toSlash() -> SdkSourceProperties(
                            mapOf(
                                SDK_PKG_REVISION.key to "18.1.23456"
                            )
                        )
                        else -> null
                    }
                })
        assertThat(path).isNull()
        assertThat(log.errors()).hasSize(1)
        // The test SingleVariantSyncIntegrationTest#testProjectSyncIssuesAreCorrectlyReported
        // checks for this exact message. If you need to change this here then you'll also
        // have to change it there
        assertThat(log.errors().single().message)
            .isEqualTo("Requested NDK version '17.1.unparseable' could not be parsed")
    }

    @Test
    fun ndkNotConfiguredWithTwoPartDslVersion() {
        val path = findNdkPath(
            ndkVersionFromDsl = "18.1",
            ndkDirProperty = null,
            sdkFolder = null,
            getNdkVersionedFolderNames = { listOf() },
            getNdkSourceProperties = { null }
        )
        assertThat(path).isNull()
    }

    @Test
    fun ndkDirPropertyLocationDoesntExistWithTwoPartDslVersion() {
        val path = findNdkPath(
            ndkVersionFromDsl = "18.1",
            ndkDirProperty = "/my/ndk/folder".toSlash(),
            sdkFolder = null,
            getNdkVersionedFolderNames = { listOf() },
            getNdkSourceProperties = { null }
        )
        assertThat(path).isNull()
    }

    @Test
    fun sdkFolderNdkBundleExistsWithTwoPartDslVersion() {
        val path = findNdkPath(
            ndkVersionFromDsl = "18.1",
            ndkDirProperty = null,
            sdkFolder = "/my/sdk/folder".toSlashFile(),
            getNdkVersionedFolderNames = { listOf() },
            getNdkSourceProperties = { path ->
                when (path.path) {
                    "/my/sdk/folder/ndk-bundle".toSlash() -> SdkSourceProperties(
                        mapOf(
                            SDK_PKG_REVISION.key to "18.1.23456"
                        )
                    )
                    else -> throw RuntimeException(path.path)
                }
            })
        assertThat(path).isNull()
    }

    @Test
    fun ndkNotConfiguredWithTwoPartDslVersionWithVersionedNdk() {
        val path = findNdkPath(
            ndkVersionFromDsl = "18.1",
            ndkDirProperty = null,
            sdkFolder = null,
            getNdkVersionedFolderNames = { listOf("18.1.23456") },
            getNdkSourceProperties = { null }
        )
        assertThat(path).isNull()
    }

    @Test
    fun ndkDirPropertyLocationDoesntExistWithTwoPartDslVersionWithVersionedNdk() {
        val path = findNdkPath(
            ndkVersionFromDsl = "18.1",
            ndkDirProperty = "/my/ndk/folder".toSlash(),
            sdkFolder = null,
            getNdkVersionedFolderNames = { listOf("18.1.23456") },
            getNdkSourceProperties = { null }
        )
        assertThat(path).isNull()
    }

    @Test
    fun sdkFolderNdkBundleExistsWithTwoPartDslVersionWithVersionedNdk() {
        val path = findNdkPath(
            ndkVersionFromDsl = "18.1",
            ndkDirProperty = null,
            sdkFolder = "/my/sdk/folder".toSlashFile(),
            getNdkVersionedFolderNames = { listOf("18.1.23456") },
            getNdkSourceProperties = { path ->
                when (path.path) {
                    "/my/sdk/folder/ndk/18.1.23456".toSlash() -> SdkSourceProperties(
                        mapOf(
                            SDK_PKG_REVISION.key to "18.1.23456"
                        )
                    )
                    else -> null
                }
            })
        assertThat(path).isEqualTo(null)
        assertThat(path).isNull()
    }

    @Test
    fun ndkNotConfiguredWithOnePartDslVersion() {
        val path = findNdkPath(
            ndkVersionFromDsl = "18",
            ndkDirProperty = null,
            sdkFolder = null,
            getNdkVersionedFolderNames = { listOf() },
            getNdkSourceProperties = { null }
        )
        assertThat(path).isNull()
    }

    @Test
    fun ndkDirPropertyLocationDoesntExistWithOnePartDslVersion() {
        val path = findNdkPath(
            ndkVersionFromDsl = "18",
            ndkDirProperty = "/my/ndk/folder".toSlash(),
            sdkFolder = null,
            getNdkVersionedFolderNames = { listOf() },
            getNdkSourceProperties = { null }
        )
        assertThat(path).isNull()
    }

    @Test
    fun sdkFolderNdkBundleExistsWithOnePartDslVersion() {
        val path = findNdkPath(
            ndkVersionFromDsl = "18",
            ndkDirProperty = null,
            sdkFolder = "/my/sdk/folder".toSlashFile(),
            getNdkVersionedFolderNames = { listOf() },
            getNdkSourceProperties = { path ->
                when (path.path) {
                    "/my/sdk/folder/ndk-bundle".toSlash() -> SdkSourceProperties(
                        mapOf(
                            SDK_PKG_REVISION.key to "18.1.23456"
                        )
                    )
                    else -> throw RuntimeException(path.path)
                }
            })
        assertThat(path).isNull()
    }

    @Test
    fun ndkNotConfiguredWithOnePartDslVersionWithVersionedNdk() {
        val path = findNdkPath(
            ndkVersionFromDsl = "18",
            ndkDirProperty = null,
            sdkFolder = null,
            getNdkVersionedFolderNames = { listOf("18.1.23456") },
            getNdkSourceProperties = { null }
        )
        assertThat(path).isNull()
    }

    @Test
    fun ndkDirPropertyLocationDoesntExistWithOnePartDslVersionWithVersionedNdk() {
        val path = findNdkPath(
            ndkVersionFromDsl = "18",
            ndkDirProperty = "/my/ndk/folder".toSlash(),
            sdkFolder = null,
            getNdkVersionedFolderNames = { listOf("18.1.23456") },
            getNdkSourceProperties = { null }
        )
        assertThat(path).isNull()
        assertThat(path).isNull()
    }

    @Test
    fun sdkFolderNdkBundleExistsWithOnePartDslVersionWithVersionedNdk() {
        val path = findNdkPath(
            ndkVersionFromDsl = "18",
            ndkDirProperty = null,
            sdkFolder = "/my/sdk/folder".toSlashFile(),
            getNdkVersionedFolderNames = { listOf("18.1.23456") },
            getNdkSourceProperties = { path ->
                when (path.path) {
                    "/my/sdk/folder/ndk/18.1.23456".toSlash() -> SdkSourceProperties(
                        mapOf(
                            SDK_PKG_REVISION.key to "18.1.23456"
                        )
                    )
                    else -> null
                }
            })
        assertThat(path).isNull()
    }

    @Test
    fun `from fuzz, blank ndkVersionFromDsl`() {
        val path = findNdkPath(
            ndkVersionFromDsl = "",
            ndkDirProperty = null,
            sdkFolder = "/my/sdk/folder".toSlashFile(),
            getNdkVersionedFolderNames = { listOf("18.1.23456") },
            getNdkSourceProperties = { path ->
                when (path.path) {
                    "/my/sdk/folder/ndk/18.1.23456".toSlash() -> SdkSourceProperties(
                        mapOf(
                            SDK_PKG_REVISION.key to "18.1.23456"
                        )
                    )
                    else -> null
                }
            })
        assertThat(path).isNull()
        assertThat(log.errors()).hasSize(0) // No NDK found is supposed to be warning
    }

    @Test
    fun `fuzz test`() {
        RandomInstanceGenerator().apply {
            PassThroughDeduplicatingLoggingEnvironment().use {
                for (i in 0..10000) {
                    val veryOldVersion = "10.1.2"
                    val properVersion = "18.1.23456"
                    val properSdkPath = "/my/sdk/folder"
                    val properNdkPath = "$properSdkPath/ndk/$properVersion"
                    val properLegacyNdkPath = "$properSdkPath/ndk-bundle"
                    fun interestingString() = oneOf(
                        { nullableString() },
                        { "16" },
                        { veryOldVersion },
                        { "17.1" },
                        { "17.1.2" },
                        { properVersion },
                        { properNdkPath },
                        { "/my/sdk/folder/ndk/17.1.2" },
                        { "/my/sdk/folder" },
                        { SDK_PKG_REVISION.key })

                    fun pathToNdk() = oneOf({ properNdkPath },
                        { properLegacyNdkPath },
                        { null },
                        { interestingString() })

                    fun pathToSdk() = oneOf({ properSdkPath }, { null }, { interestingString() })
                    fun ndkVersion() = oneOf({ properVersion },
                        { veryOldVersion },
                        { null },
                        { interestingString() })

                    fun ndkVersionList() = makeListOf { ndkVersion() }.filterNotNull()
                    fun sourcePropertyVersionKey() = oneOf({ SDK_PKG_REVISION.key },
                        { SDK_PKG_REVISION.key },
                        { null },
                        { interestingString() })

                    findNdkPath(
                        ndkVersionFromDsl = ndkVersion(),
                        ndkDirProperty = pathToNdk(),
                        sdkFolder = pathToSdk().toSlashFile(),
                        getNdkVersionedFolderNames = { ndkVersionList() },
                        getNdkSourceProperties = { path ->
                            when (path.path) {
                                pathToNdk() -> SdkSourceProperties(
                                    mapOf(
                                        (sourcePropertyVersionKey() ?: "") to (ndkVersion() ?: "")
                                    )
                                )
                                else -> null
                            }
                        })
                }
            }
        }
    }
}