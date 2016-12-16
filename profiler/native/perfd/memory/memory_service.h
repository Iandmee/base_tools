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
#ifndef PERFD_MEMORY_MEMORY_SERVICE_H_
#define PERFD_MEMORY_MEMORY_SERVICE_H_

#include <grpc++/grpc++.h>
#include <unordered_map>

#include "memory_collector.h"
#include "proto/memory.grpc.pb.h"
#include "utils/clock.h"

namespace profiler {

class MemoryServiceImpl final
    : public ::profiler::proto::MemoryService::Service {
 public:
  MemoryServiceImpl(const Clock& clock,
                    std::unordered_map<int32_t, MemoryCollector>* collectors)
      : clock_(clock), collectors_(*collectors) {}
  virtual ~MemoryServiceImpl() = default;

  ::grpc::Status StartMonitoringApp(
      ::grpc::ServerContext* context,
      const profiler::proto::MemoryStartRequest* request,
      ::profiler::proto::MemoryStartResponse* response) override;

  ::grpc::Status StopMonitoringApp(
      ::grpc::ServerContext* context,
      const profiler::proto::MemoryStopRequest* request,
      ::profiler::proto::MemoryStopResponse* response) override;

  ::grpc::Status GetData(::grpc::ServerContext* context,
                         const ::profiler::proto::MemoryRequest* request,
                         ::profiler::proto::MemoryData* response) override;

  ::grpc::Status TriggerHeapDump(
      ::grpc::ServerContext* context,
      const ::profiler::proto::TriggerHeapDumpRequest* request,
      ::profiler::proto::TriggerHeapDumpResponse* response) override;

  ::grpc::Status GetHeapDump(
      ::grpc::ServerContext* context,
      const ::profiler::proto::HeapDumpDataRequest* request,
      ::profiler::proto::DumpDataResponse* response) override;

  ::grpc::Status ListHeapDumpInfos(
      ::grpc::ServerContext* context,
      const ::profiler::proto::ListDumpInfosRequest* request,
      ::profiler::proto::ListHeapDumpInfosResponse* response) override;

  ::grpc::Status TrackAllocations(
      ::grpc::ServerContext* context,
      const ::profiler::proto::TrackAllocationsRequest* request,
      ::profiler::proto::TrackAllocationsResponse* response) override;

  ::grpc::Status GetAllocationsInfoStatus(
      ::grpc::ServerContext* context,
      const ::profiler::proto::GetAllocationsInfoStatusRequest* request,
      ::profiler::proto::GetAllocationsInfoStatusResponse* response) override;

  ::grpc::Status ListAllocationContexts(
      ::grpc::ServerContext* context,
      const ::profiler::proto::AllocationContextsRequest* request,
      ::profiler::proto::AllocationContextsResponse* response)
      override;

 private:
  MemoryCollector* GetCollector(int32_t app_id);

  const Clock& clock_;
  std::unordered_map<int32_t, MemoryCollector>&
      collectors_;  // maps pid to MemoryCollector
};
}  // namespace profiler

#endif  // PERFD_MEMORY_MEMORY_SERVICE_H_
