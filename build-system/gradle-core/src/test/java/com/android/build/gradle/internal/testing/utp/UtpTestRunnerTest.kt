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

package com.android.build.gradle.internal.testing.utp

import com.android.build.api.variant.impl.AndroidVersionImpl
import com.android.build.gradle.internal.SdkComponentsBuildService
import com.android.build.gradle.internal.fixtures.FakeConfigurableFileCollection
import com.android.build.gradle.internal.testing.StaticTestData
import com.android.builder.testing.api.DeviceConnector
import com.android.ide.common.process.JavaProcessExecutor
import com.android.ide.common.process.JavaProcessInfo
import com.android.ide.common.process.ProcessExecutor
import com.android.ide.common.process.ProcessOutputHandler
import com.android.ide.common.process.ProcessResult
import com.android.ide.common.workers.ExecutorServiceAdapter
import com.android.testutils.MockitoKt.any
import com.android.testutils.truth.PathSubject.assertThat
import com.android.tools.utp.plugins.result.listener.gradle.proto.GradleAndroidTestResultListenerProto.TestResultEvent
import com.android.utils.ILogger
import com.google.common.truth.Truth.assertThat
import com.google.protobuf.Any
import com.google.protobuf.TextFormat
import com.google.testing.platform.proto.api.config.RunnerConfigProto
import com.google.testing.platform.proto.api.core.TestSuiteResultProto
import com.google.testing.platform.proto.api.service.ServerConfigProto.ServerConfig
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.anyBoolean
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyIterable
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.junit.MockitoJUnit
import org.mockito.junit.MockitoRule
import org.mockito.quality.Strictness
import java.io.File

/**
 * Unit tests for [UtpTestRunner].
 */
class UtpTestRunnerTest {
    @get:Rule var mockitoJUnitRule: MockitoRule =
            MockitoJUnit.rule().strictness(Strictness.STRICT_STUBS)
    @get:Rule var temporaryFolderRule = TemporaryFolder()

    @Mock lateinit var mockProcessExecutor: ProcessExecutor
    @Mock lateinit var mockJavaProcessExecutor: JavaProcessExecutor
    @Mock lateinit var mockExecutorServiceAdapter: ExecutorServiceAdapter
    @Mock lateinit var mockVersionedSdkLoader: SdkComponentsBuildService.VersionedSdkLoader
    @Mock lateinit var mockTestData: StaticTestData
    @Mock lateinit var mockAppApk: File
    @Mock lateinit var mockTestApk: File
    @Mock lateinit var mockHelperApk: File
    @Mock lateinit var mockDevice: DeviceConnector
    @Mock lateinit var mockCoverageDir: File
    @Mock lateinit var mockLogger: ILogger
    @Mock lateinit var mockUtpConfigFactory: UtpConfigFactory
    @Mock lateinit var mockRetentionConfig: RetentionConfig
    @Mock lateinit var mockTestResultListener: UtpTestResultListener

    private val utpDependencies = object: UtpDependencies() {
        override val launcher = FakeConfigurableFileCollection(File("/pathToLAUNCHER.jar"))
        override val core = FakeConfigurableFileCollection(File("/pathToCORE.jar"))
        override val deviceControllerDdmlib = FakeConfigurableFileCollection(File(""))
        override val deviceProviderGradle = FakeConfigurableFileCollection(File(""))
        override val deviceProviderVirtual = FakeConfigurableFileCollection(File(""))
        override val driverInstrumentation = FakeConfigurableFileCollection(File(""))
        override val testDeviceInfoPlugin = FakeConfigurableFileCollection(File(""))
        override val testPlugin = FakeConfigurableFileCollection(File(""))
        override val testPluginHostRetention = FakeConfigurableFileCollection(File(""))
        override val testPluginResultListenerGradle = FakeConfigurableFileCollection(File(""))
    }

    private lateinit var runner: UtpTestRunner

    @Before
    fun setupMocks() {
        `when`(mockDevice.serialNumber).thenReturn("mockDeviceSerialNumber")
        `when`(mockDevice.apiLevel).thenReturn(28)
        `when`(mockDevice.name).thenReturn("mockDeviceName")
        `when`(mockTestData.minSdkVersion).thenReturn(AndroidVersionImpl(28))
        `when`(mockTestData.testedApkFinder).thenReturn { _, _ -> listOf(mockAppApk) }
        `when`(mockUtpConfigFactory.createRunnerConfigProtoForLocalDevice(
                any(DeviceConnector::class.java),
                any(StaticTestData::class.java),
                anyIterable(),
                anyIterable(),
                any(UtpDependencies::class.java),
                any(SdkComponentsBuildService.VersionedSdkLoader::class.java),
                any(File::class.java),
                any(File::class.java),
                any(RetentionConfig::class.java),
                anyBoolean(),
                anyInt(),
                any(File::class.java),
                any(File::class.java),
                any(File::class.java))).then {
            RunnerConfigProto.RunnerConfig.getDefaultInstance()
        }
        `when`(mockUtpConfigFactory.createServerConfigProto())
                .thenReturn(ServerConfig.getDefaultInstance())
        // When the java process executor is invoked, creates a result proto file
        // to the output directory which was passed into the config factory.
        `when`(mockJavaProcessExecutor.execute(
                any(JavaProcessInfo::class.java),
                any(ProcessOutputHandler::class.java))).then {
            val testSuiteResult = createStubResultProto()

            runner.onTestResultEvent(TestResultEvent.newBuilder().apply {
                testSuiteStartedBuilder.apply {
                    deviceId = "mockDeviceSerialNumber"
                    testSuiteMetadata = Any.pack(testSuiteResult.testSuiteMetaData)
                }
            }.build())
            testSuiteResult.testResultList.forEach { testResult ->
                runner.onTestResultEvent(TestResultEvent.newBuilder().apply {
                    testCaseStartedBuilder.apply {
                        deviceId = "mockDeviceSerialNumber"
                        testCase = Any.pack(testResult.testCase)
                    }
                }.build())
                runner.onTestResultEvent(TestResultEvent.newBuilder().apply {
                    testCaseFinishedBuilder.apply {
                        deviceId = "mockDeviceSerialNumber"
                        testCaseResult = Any.pack(testResult)
                    }
                }.build())
            }
            runner.onTestResultEvent(TestResultEvent.newBuilder().apply {
                testSuiteFinishedBuilder.apply {
                    deviceId = "mockDeviceSerialNumber"
                    this.testSuiteResult = Any.pack(testSuiteResult)
                }
            }.build())

            mock(ProcessResult::class.java)
        }
    }

    private fun createStubResultProto(): TestSuiteResultProto.TestSuiteResult {
        return TextFormat.parse("""
            test_suite_meta_data {
              scheduled_test_case_count: 1
            }
            test_result {
              test_case {
                test_class: "ExampleInstrumentedTest"
                test_package: "com.example.application"
                test_method: "useAppContext"
              }
              test_status: PASSED
            }
        """.trimIndent(), TestSuiteResultProto.TestSuiteResult::class.java)
    }

    @Test
    fun runTests() {
        runner = UtpTestRunner(
                null,
                mockProcessExecutor,
                mockJavaProcessExecutor,
                mockExecutorServiceAdapter,
                utpDependencies,
                mockVersionedSdkLoader,
                mockRetentionConfig,
                useOrchestrator = false,
                mockTestResultListener,
                mockUtpConfigFactory)

        val resultDir = temporaryFolderRule.newFolder("results").toPath()

        runner.runTests(
                "projectName",
                "variantName",
                mockTestData,
                setOf(mockHelperApk),
                listOf(mockDevice),
                0,
                setOf(),
                resultDir.toFile(),
                false,
                null,
                mockCoverageDir,
                mockLogger)

        val captor = ArgumentCaptor.forClass(JavaProcessInfo::class.java)
        verify(mockJavaProcessExecutor).execute(captor.capture(), any(ProcessOutputHandler::class.java))
        assertThat(captor.value.classpath).isEqualTo(utpDependencies.launcher.singleFile.absolutePath)
        assertThat(captor.value.mainClass).isEqualTo(UtpDependency.LAUNCHER.mainClass)
        assertThat(captor.value.jvmArgs).hasSize(1)
        assertThat(captor.value.jvmArgs[0]).startsWith("-Djava.util.logging.config.file=")
        assertThat(captor.value.args).hasSize(3)
        assertThat(captor.value.args[0]).isEqualTo(utpDependencies.core.singleFile.absolutePath)
        assertThat(captor.value.args[1]).startsWith("--proto_config=")
        assertThat(captor.value.args[2]).startsWith("--proto_server_config=")

        val variant = resultDir.resolve("TEST-mockDeviceName-projectName-variantName.xml")
        assertThat(variant).exists()
        assertThat(variant).containsAllOf(
                """<testsuite name="com.example.application.ExampleInstrumentedTest" tests="1" failures="0" errors="0" skipped="0"""",
                """<property name="device" value="mockDeviceName" />""",
                """<property name="flavor" value="variantName" />""",
                """<property name="project" value="projectName" />""",
                """<testcase name="useAppContext" classname="com.example.application.ExampleInstrumentedTest""""
        )
    }
}
