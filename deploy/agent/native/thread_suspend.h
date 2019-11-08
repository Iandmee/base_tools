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
 *
 */
#ifndef THREAD_SUSPEND_H
#define THREAD_SUSPEND_H

#include <string>
#include <vector>

namespace deploy {

class ThreadSuspend {
 public:
  ThreadSuspend(jvmtiEnv* jvmti) : jvmti_(jvmti) {}

  std::string SuspendUserThreads();

  std::string ResumeSuspendedThreads();

 private:
  jvmtiEnv* jvmti_;
  std::vector<jthread> suspended_thread_;
};

}  // namespace deploy
#endif
