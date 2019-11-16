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

#ifndef PERFD_CPU_SIMPLEPERFMANAGER_H_
#define PERFD_CPU_SIMPLEPERFMANAGER_H_

#include <map>
#include <memory>
#include <mutex>
#include <string>

#include "perfd/cpu/simpleperf.h"
#include "proto/cpu.grpc.pb.h"
#include "utils/fs/disk_file_system.h"
#include "utils/fs/file_system.h"
#include "utils/fs/memory_file_system.h"

namespace profiler {

// Entry storing all data related to an ongoing profiling.
struct OnGoingProfiling {
  // Process ID being profiled.
  int pid;
  // The name of the process/app being profiled.
  std::string process_name;
  // Simpleperf pid doing the profiling.
  int simpleperf_pid;
  // The ABI CPU architecture (e.g. arm, arm64, x86, x86_64) corresponding to
  // the simpleperf binary being used to profile.
  std::string abi_arch;
  // File path where trace will be made available.
  std::string trace_path;
  // File path of the raw trace generated by running simpleperf record, which is
  // later converted into protobuf format
  std::string raw_trace_path;
  // If something happen while simpleperf is running, store logs in this file.
  std::string log_file_path;
};

class SimpleperfManager {
 public:
  // For production.
  SimpleperfManager()
      : SimpleperfManager(std::unique_ptr<Simpleperf>(new Simpleperf()),
                          std::unique_ptr<FileSystem>(new DiskFileSystem())) {}
  // For testing.
  SimpleperfManager(std::unique_ptr<Simpleperf> simpleperf)
      : SimpleperfManager(std::move(simpleperf),
                          std::unique_ptr<FileSystem>(new MemoryFileSystem())) {
  }

  explicit SimpleperfManager(std::unique_ptr<Simpleperf> simpleperf,
                             std::unique_ptr<FileSystem> file_system)
      : simpleperf_(std::move(simpleperf)),
        file_system_(std::move(file_system)) {}

  ~SimpleperfManager();

  // Returns true if profiling of app |app_name| was started successfully.
  // |trace_path| is where the trace file will be made available once profiling
  // of this app is stopped. To call this method on an already profiled app is
  // a noop and returns false.
  // The simpleperf binary used to profile should correspond to the given
  // |abi_arch|. If |is_startup_profiling| is true, it means that the
  // application hasn't launched and pid is not available yet, so it will use
  // "--app" flag instead of "--pid".
  bool StartProfiling(const std::string &app_name, const std::string &abi_arch,
                      int sampling_interval_us, const std::string &trace_path,
                      std::string *error, bool is_startup_profiling = false);
  // Stops simpleperf process that is currently profiling |app_name|.
  // If |need_result|, copies the resulting data into the trace file.
  profiler::proto::TraceStopStatus::Status StopProfiling(
      const std::string &app_name, bool need_result, std::string *error);
  // Returns true if the app is currently being profiled by a simpleperf
  // process.
  bool IsProfiling(const std::string &app_name);

  // Stops all ongoing profiling.
  void Shutdown();

  // Visible for testing.
  Simpleperf *simpleperf() { return simpleperf_.get(); }

 private:
  std::map<std::string, OnGoingProfiling> profiled_;
  std::mutex start_stop_mutex_;  // Protects simpleperf start/stop
  std::unique_ptr<Simpleperf> simpleperf_;
  std::unique_ptr<FileSystem> file_system_;

  // Wait until simpleperf process has returned.
  bool WaitForSimpleperf(const OnGoingProfiling &ongoing_recording,
                         std::string *error) const;

  // Copies a trace file in simpleperf binary format to |trace_path|, defined in
  // |ongoing_recording|. This is used when running simpleperf on the host, as
  // in this case CPU service should include the raw trace in the response to
  // the client.
  bool CopyRawToTrace(const OnGoingProfiling &ongoing_recording,
                      std::string *error) const;

  // Delete log file and raw trace file generated by running simpleperf record.
  void CleanUp(const OnGoingProfiling &ongoing_recording) const;

  bool StopSimpleperf(const OnGoingProfiling &ongoing_recording,
                      std::string *error) const;
};
}  // namespace profiler

#endif  // PERFD_CPU_SIMPLEPERFMANAGER_H_
