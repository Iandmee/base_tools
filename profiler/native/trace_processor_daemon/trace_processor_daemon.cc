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

#include <grpc++/grpc++.h>
#include <chrono>
#include <thread>

#include "absl/flags/flag.h"
#include "absl/flags/parse.h"
#include "absl/time/time.h"
#include "trace_processor_service.h"

using grpc::ServerBuilder;
using profiler::perfetto::TraceProcessorServiceImpl;

ABSL_FLAG(uint16_t, port, 20204, "Port to open the gRPC server");
ABSL_FLAG(absl::Duration, server_timeout, absl::Hours(1),
          "How long to keep the server alive when inactive");

class GRPC_GlobalCallback : public grpc_impl::Server::GlobalCallbacks {
 public:
  GRPC_GlobalCallback(std::chrono::steady_clock::time_point* last_activity)
      : last_activity_(last_activity) {}
  ~GRPC_GlobalCallback() {}

  // Called before server is started.
  void PreServerStart(grpc::Server* server) override { UpdateLastActivity(); }
  // Called before application callback for each synchronous server request
  void PreSynchronousRequest(grpc_impl::ServerContext* context) override {
    UpdateLastActivity();
  }
  // Called after application callback for each synchronous server request
  // We do nothing here, we just need to override it 'cause it's pure-virtual.
  void PostSynchronousRequest(grpc_impl::ServerContext* context) override {}

 private:
  std::chrono::steady_clock::time_point* last_activity_;

  void UpdateLastActivity() {
    *last_activity_ = std::chrono::steady_clock::now();
  }
};

// keep checking for server activity, if it's not detected for more than
// FLAGS_server_timeout seconds, shutdown the server (which will shutdown the
// daemon too)
void check_last_activity(grpc::Server* server,
                         std::chrono::steady_clock::time_point* last_activity) {
  auto timeout_flag_seconds =
      absl::ToInt64Seconds(absl::GetFlag(FLAGS_server_timeout));
  std::chrono::steady_clock::duration timeout =
      std::chrono::seconds(timeout_flag_seconds);
  while (1) {
    auto dur_since_last = std::chrono::steady_clock::now() - *last_activity;
    if (dur_since_last > timeout) {
      std::cout << "Shuting down daemon by RPC inactivity." << std::endl;
      server->Shutdown();
      break;
    }
    std::this_thread::sleep_until(*last_activity + timeout);
  }
}

void RunServer() {
  auto time_point = std::chrono::steady_clock::now();
  GRPC_GlobalCallback callback(&time_point);
  grpc_impl::Server::SetGlobalCallbacks(&callback);

  ServerBuilder builder;

  // Register the handler for TraceProcessorService.
  TraceProcessorServiceImpl service;
  builder.RegisterService(&service);

  auto port_flag = absl::GetFlag(FLAGS_port);
  // Bind to to loopback only, as we will only communicate with localhost.
  std::string server_address("127.0.0.1:" + std::to_string(port_flag));

  // BuildAndStart() will modify this with the picked port.
  int port = 0;
  builder.AddListeningPort(server_address, grpc::InsecureServerCredentials(),
                           &port);
  std::unique_ptr<grpc::Server> server(builder.BuildAndStart());
  if (port == 0) {
    // The port wasn't successfully bound to the server by BuildAndStart().
    std::cout << "Server failed to start. A port number wasn't bound."
              << std::endl;
    exit(EXIT_FAILURE);
  }

  std::cout << "Server listening on " << server_address << std::endl;

  std::thread activity_checker(&check_last_activity, server.get(), &time_point);
  server->Wait();
  activity_checker.join();
}

int main(int argc, char** argv) {
  GOOGLE_PROTOBUF_VERIFY_VERSION;
  absl::ParseCommandLine(argc, argv);

  RunServer();
  return 0;
}
