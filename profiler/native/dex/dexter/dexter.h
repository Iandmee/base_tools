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

#pragma once

///////////////////////////////////////////////////////////////////////////////
//
// Encapsulates the state (command line switches, stats, ...) and
// the interface of the command line .dex manipulation tool.
//
class Dexter {
  static constexpr const char* VERSION = "v1.0";

 public:
  Dexter(int argc, char* argv[]) : argc_(argc), argv_(argv) {}

  Dexter(const Dexter&) = delete;
  Dexter& operator=(const Dexter&) = delete;

  // main entry point, returns an appropriate proces exit code
  int Run();

 private:
  int ProcessDex();
  void PrintHelp();

 private:
  // command line
  int argc_ = 0;
  char** argv_ = nullptr;

  // parsed options
  bool verbose_ = false;
  const char* out_dex_filename_ = nullptr;
  const char* dex_filename_ = nullptr;
};
