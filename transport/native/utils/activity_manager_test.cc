/*
 * Copyright (C) 2016 The Android Open Source Project
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
#include "utils/activity_manager.h"

#include <gmock/gmock.h>
#include <gtest/gtest.h>

using std::string;
using testing::DoAll;
using testing::HasSubstr;
using testing::Return;
using testing::SaveArg;
using testing::SetArgPointee;
using testing::StartsWith;
using testing::StrEq;
using testing::StrNe;

namespace {
const char* const kAmExecutable = "/aaaaa/system/bin/am";
const char* const kProfileStart = "profile start";
const char* const kTestPackageName = "TestPackageName";
const char* const kMockOutputString = "MockOutputString";
}  // namespace

namespace profiler {

// A subclass of ActivityManager that we want to test. The only difference is it
// has a public constructor.
class TestActivityManager final : public ActivityManager {
 public:
  explicit TestActivityManager(std::unique_ptr<BashCommandRunner> bash)
      : ActivityManager(std::move(bash)) {}
};

// A mock BashCommandRunner that mocks the execution of command.
// We need the mock to run tests across platforms to examine the commands
// generated by ActivityManager.
class MockBashCommandRunner final : public BashCommandRunner {
 public:
  explicit MockBashCommandRunner(const std::string& executable_path)
      : BashCommandRunner(executable_path) {}
  MOCK_CONST_METHOD2(RunAndReadOutput,
                     bool(const std::string& cmd, std::string* output));
};

TEST(ActivityManagerTest, SamplingStart) {
  string trace_path;
  string output_string;
  string cmd;
  std::unique_ptr<BashCommandRunner> bash{
      new MockBashCommandRunner(kAmExecutable)};
  EXPECT_CALL(*(static_cast<MockBashCommandRunner*>(bash.get())),
              RunAndReadOutput(testing::A<const string&>(), &output_string))
      .WillOnce(DoAll(SaveArg<0>(&cmd), SetArgPointee<1>(kMockOutputString),
                      Return(true)));
  TestActivityManager manager{std::move(bash)};
  manager.StartProfiling(ActivityManager::ProfilingMode::SAMPLING,
                         kTestPackageName, 1000, trace_path, &output_string);
  EXPECT_THAT(cmd, StartsWith(kAmExecutable));
  EXPECT_THAT(cmd, HasSubstr(kProfileStart));
  EXPECT_THAT(cmd, HasSubstr(kTestPackageName));
  EXPECT_THAT(cmd, HasSubstr("--sampling 1000 "));
  // '--sampling 0' is effectively instrumentation mode.
  EXPECT_THAT(cmd, Not(HasSubstr("--sampling 0 ")));
  EXPECT_THAT(output_string, StrEq(kMockOutputString));
}

TEST(ActivityManagerTest, InstrumentStart) {
  string trace_path;
  string output_string;
  string cmd;
  std::unique_ptr<BashCommandRunner> bash{
      new MockBashCommandRunner(kAmExecutable)};
  EXPECT_CALL(*(static_cast<MockBashCommandRunner*>(bash.get())),
              RunAndReadOutput(testing::A<const string&>(), &output_string))
      .WillOnce(DoAll(SaveArg<0>(&cmd), SetArgPointee<1>(kMockOutputString),
                      Return(true)));
  TestActivityManager manager{std::move(bash)};
  manager.StartProfiling(ActivityManager::ProfilingMode::INSTRUMENTED,
                         kTestPackageName, 1000, trace_path, &output_string);
  EXPECT_THAT(cmd, StartsWith(kAmExecutable));
  EXPECT_THAT(cmd, HasSubstr(kProfileStart));
  EXPECT_THAT(cmd, HasSubstr(kTestPackageName));
  EXPECT_THAT(cmd, Not(HasSubstr("--sampling")));
  EXPECT_THAT(output_string, StrEq(kMockOutputString));
}

TEST(ActivityManagerTest, InstrumentSystemServerStart) {
  string trace_path;
  string output_string;
  string cmd;
  std::unique_ptr<BashCommandRunner> bash{
      new MockBashCommandRunner(kAmExecutable)};
  EXPECT_CALL(*(static_cast<MockBashCommandRunner*>(bash.get())),
              RunAndReadOutput(testing::A<const string&>(), &output_string))
      .WillOnce(DoAll(SaveArg<0>(&cmd), SetArgPointee<1>(kMockOutputString),
                      Return(true)));
  TestActivityManager manager{std::move(bash)};
  manager.StartProfiling(ActivityManager::ProfilingMode::INSTRUMENTED,
                         "system_process", 1000, trace_path, &output_string);
  EXPECT_THAT(cmd, StartsWith(kAmExecutable));
  EXPECT_THAT(cmd, HasSubstr(kProfileStart));
  EXPECT_THAT(cmd, HasSubstr(" system "));
  EXPECT_THAT(cmd, Not(HasSubstr(" system_process ")));
  EXPECT_THAT(output_string, StrEq(kMockOutputString));
}

}  // namespace profiler
