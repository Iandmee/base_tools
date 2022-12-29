/*
 * Copyright (C) 2019 The Android Open Source Project
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
#include "start_native_sample.h"

#include "perfd/common/utils/trace_command_utils.h"
#include "proto/memory_data.pb.h"
#include "proto/trace.pb.h"

using grpc::Status;
using profiler::proto::Event;
using profiler::proto::ProfilerType;
using profiler::proto::TraceStartStatus;
using std::string;

namespace profiler {

Status StartNativeSample::ExecuteOn(Daemon* daemon) {
  auto& start_command = command().start_native_sample();
  // Used as the group id for this heap dump's events.
  // The raw bytes will be available in the file cache via this id.
  int64_t start_timestamp;
  if (start_command.has_api_start_metadata()) {
    start_timestamp = start_command.api_start_metadata().start_timestamp();
  } else {
    start_timestamp = daemon->clock()->GetCurrentTime();
  }

  TraceStartStatus start_status;
  auto* capture = trace_manager_->StartCapture(
      start_timestamp, start_command.configuration(), &start_status);

  Event status_event;
  status_event.set_pid(command().pid());
  status_event.set_kind(Event::TRACE_STATUS);
  status_event.set_command_id(command().command_id());
  start_status.set_start_time_ns(start_timestamp);
  status_event.mutable_trace_status()->mutable_trace_start_status()->CopyFrom(
      start_status);

  std::vector<Event> events_to_send;
  if (capture != nullptr) {
    Event event =
        PopulateTraceEvent(*capture, command(), ProfilerType::MEMORY, false);
    status_event.set_group_id(capture->start_timestamp);

    events_to_send.push_back(status_event);
    events_to_send.push_back(event);
  } else {
    events_to_send.push_back(status_event);
  }
  // For the case of startup tracing, the command could be sent before 
  // the session is created. Either send the events if the session
  // is already alive or queue the events to be sent when the session is
  // created.
  sessions_manager_->SendOrQueueEventsForSession(
      daemon, start_command.configuration().app_name(), events_to_send);

  return Status::OK;
}

}  // namespace profiler
