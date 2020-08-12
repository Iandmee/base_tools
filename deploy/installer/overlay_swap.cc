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

#include "tools/base/deploy/installer/overlay_swap.h"

#include "tools/base/deploy/common/event.h"
#include "tools/base/deploy/common/log.h"
#include "tools/base/deploy/common/utils.h"
#include "tools/base/deploy/installer/command_cmd.h"
#include "tools/base/deploy/installer/executor/runas_executor.h"

namespace deploy {

void OverlaySwapCommand::ParseParameters(int argc, char** argv) {
  deploy::MessagePipeWrapper wrapper(STDIN_FILENO);
  std::string data;
  if (!wrapper.Read(&data)) {
    return;
  }

  if (!request_.ParseFromString(data)) {
    return;
  }

  std::vector<int> pids(request_.process_ids().begin(),
                        request_.process_ids().end());
  SetSwapParameters(request_.package_name(), pids, request_.extra_agents());
  ready_to_run_ = true;
}

proto::SwapRequest OverlaySwapCommand::PrepareAndBuildRequest(
    proto::SwapResponse* response) {
  Phase p("PreSwap");
  proto::SwapRequest request;

  std::string version = workspace_.GetVersion() + "-";
  std::string code_cache = "/data/data/" + package_name_ + "/code_cache/";

  // Determine which agent we need to use.
#if defined(__aarch64__) || defined(__x86_64__)
  std::string agent =
      request_.arch() == proto::Arch::ARCH_64_BIT ? kAgent : kAgentAlt;
#else
  std::string agent = kAgent;
#endif

  std::string startup_path = code_cache + "startup_agents/";
  std::string studio_path = code_cache + ".studio/";
  std::string agent_path = startup_path + version + agent;

  std::unordered_set<std::string> missing_files;
  // TODO: Error checking
  CheckFilesExist({startup_path, studio_path, agent_path}, &missing_files);

  RunasExecutor run_as(package_name_, workspace_.GetExecutor());
  std::string error;

  if (missing_files.find(startup_path) != missing_files.end() &&
      !run_as.Run("mkdir", {startup_path}, nullptr, &error)) {
    response->set_status(proto::SwapResponse::SETUP_FAILED);
    ErrEvent("Could not create startup agent directory: " + error);
    return request;
  }

  if (missing_files.find(studio_path) != missing_files.end() &&
      !run_as.Run("mkdir", {studio_path}, nullptr, &error)) {
    response->set_status(proto::SwapResponse::SETUP_FAILED);
    ErrEvent("Could not create .studio directory: " + error);
    return request;
  }

  if (missing_files.find(agent_path) != missing_files.end() &&
      !run_as.Run("cp", {"-F", workspace_.GetTmpFolder() + agent, agent_path},
                  nullptr, &error)) {
    response->set_status(proto::SwapResponse::SETUP_FAILED);
    ErrEvent("Could not copy binaries: " + error);
    return request;
  }

  SetAgentPath(agent_path);

  for (auto& clazz : request_.new_classes()) {
    request.add_new_classes()->CopyFrom(clazz);
  }

  for (auto& clazz : request_.modified_classes()) {
    request.add_modified_classes()->CopyFrom(clazz);
  }

  request.set_package_name(package_name_);
  request.set_restart_activity(request_.restart_activity());
  request.set_structural_redefinition(request_.structural_redefinition());
  request.set_variable_reinitialization(request_.variable_reinitialization());
  request.set_overlay_swap(true);
  return request;
}

void OverlaySwapCommand::BuildOverlayUpdateRequest(
    proto::OverlayUpdateRequest* request) {
  request->set_overlay_id(request_.overlay_id());
  request->set_expected_overlay_id(request_.expected_overlay_id());

  const std::string overlay_path =
      "/data/data/" + request_.package_name() + "/code_cache";
  request->set_overlay_path(overlay_path);

  for (auto clazz : request_.new_classes()) {
    auto file = request->add_files_to_write();
    file->set_path(clazz.name() + ".dex");
    file->set_allocated_content(clazz.release_dex());
  }

  for (auto clazz : request_.modified_classes()) {
    auto file = request->add_files_to_write();
    file->set_path(clazz.name() + ".dex");
    file->set_allocated_content(clazz.release_dex());
  }

  for (auto resource : request_.resource_overlays()) {
    auto file = request->add_files_to_write();
    file->set_path(resource.path());
    file->set_allocated_content(resource.release_content());
  }
}

void OverlaySwapCommand::ProcessResponse(proto::SwapResponse* response) {
  Phase p("PostSwap");

  if (response->status() == proto::SwapResponse::OK ||
      request_.always_update_overlay()) {
    UpdateOverlay(response);
  }

  // Do this even if the deployment failed; it's retrieving data unrelated to
  // the current deployment. We might want to find a better time to do this.
  GetAgentLogs(response);

  proto::InstallServerResponse install_response;
  if (!client_->KillServerAndWait(&install_response)) {
    response->set_status(proto::SwapResponse::READ_FROM_SERVER_FAILED);
    return;
  }

  // Convert proto events to events.
  for (int i = 0; i < install_response.events_size(); i++) {
    const proto::Event& event = install_response.events(i);
    AddRawEvent(ConvertProtoEventToEvent(event));
  }
}

void OverlaySwapCommand::UpdateOverlay(proto::SwapResponse* response) {
  Phase p("UpdateOverlay");

  bool swap_failed = (response->status() != proto::SwapResponse::OK);

  proto::InstallServerRequest install_request;
  install_request.set_type(proto::InstallServerRequest::HANDLE_REQUEST);
  BuildOverlayUpdateRequest(install_request.mutable_overlay_request());

  if (!client_->Write(install_request)) {
    response->set_status(proto::SwapResponse::WRITE_TO_SERVER_FAILED);
    return;
  }

  // Wait for server overlay update response.
  proto::InstallServerResponse install_response;
  if (!client_->Read(&install_response)) {
    response->set_status(proto::SwapResponse::READ_FROM_SERVER_FAILED);
    return;
  }

  response->set_status(
      OverlayStatusToSwapStatus(install_response.overlay_response().status()));
  response->set_extra(install_response.overlay_response().error_message());

  bool should_restart = request_.restart_activity() &&
                        response->status() == proto::SwapResponse::OK;

  CmdCommand cmd(workspace_);
  std::string error;
  if (should_restart &&
      !cmd.UpdateAppInfo("all", request_.package_name(), &error)) {
    response->set_status(proto::SwapResponse::ACTIVITY_RESTART_FAILED);
  }

  if (swap_failed &&
      (response->status() == proto::SwapResponse::OK ||
       response->status() == proto::SwapResponse::ACTIVITY_RESTART_FAILED)) {
    // If we updated overlay even on swap fail or restart fail,
    // alter the response accordingly.
    response->set_status(proto::SwapResponse::SWAP_FAILED_BUT_OVERLAY_UPDATED);
  }
}

void OverlaySwapCommand::GetAgentLogs(proto::SwapResponse* response) {
  Phase p("GetAgentLogs");
  proto::InstallServerRequest install_request;
  install_request.set_type(proto::InstallServerRequest::HANDLE_REQUEST);
  install_request.mutable_log_request()->set_package_name(
      request_.package_name());

  // If this fails, we don't really care - it's a best-effort situation; don't
  // break the deployment because of it. Just log and move on.
  if (!client_->Write(install_request)) {
    Log::W("Could not write to server to retrieve agent logs.");
    return;
  }

  proto::InstallServerResponse install_response;
  if (!client_->Read(&install_response)) {
    Log::W("Could not read from server while retrieving agent logs.");
    return;
  }

  for (const auto& log : install_response.log_response().logs()) {
    auto added = response->add_agent_logs();
    *added = log;
  }
}

proto::SwapResponse::Status OverlaySwapCommand::OverlayStatusToSwapStatus(
    proto::OverlayUpdateResponse::Status status) {
  switch (status) {
    case proto::OverlayUpdateResponse::OK:
      return proto::SwapResponse::OK;
    case proto::OverlayUpdateResponse::ID_MISMATCH:
      return proto::SwapResponse::OVERLAY_ID_MISMATCH;
    default:
      return proto::SwapResponse::OVERLAY_UPDATE_FAILED;
  }
}

}  // namespace deploy
