/*
 * Copyright (C) 2018 The Android Open Source Project
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

#ifndef INSTRUMENTER_H
#define INSTRUMENTER_H

#include <jvmti.h>

#include <memory>
#include <unordered_map>

#include "slicer/dex_ir.h"
#include "slicer/instrumentation.h"
#include "slicer/reader.h"
#include "slicer/writer.h"
#include "tools/base/deploy/common/log.h"

using std::shared_ptr;
using std::string;
using std::unordered_map;

namespace deploy {

bool InstrumentApplication(jvmtiEnv* jvmti, JNIEnv* jni,
                           const std::string& package_name);

// Probably should be in a utility header, but also only used here.
class JvmtiAllocator : public dex::Writer::Allocator {
 public:
  JvmtiAllocator(jvmtiEnv* jvmti) : jvmti_(jvmti) {}

  virtual void* Allocate(size_t size) {
    unsigned char* alloc = nullptr;
    jvmti_->Allocate(size, &alloc);
    return (void*)alloc;
  }

  virtual void Free(void* ptr) {
    if (ptr == nullptr) {
      return;
    }

    jvmti_->Deallocate((unsigned char*)ptr);
  }

 private:
  jvmtiEnv* jvmti_;
};

struct MethodHooks {
  const std::string method_name;
  const std::string method_signature;
  const std::string entry_hook;
  const std::string exit_hook;

  static const std::string kNoHook;

  MethodHooks(const std::string& method_name,
              const std::string& method_signature,
              const std::string& entry_hook, const std::string& exit_hook)
      : method_name(method_name),
        method_signature(method_signature),
        entry_hook(entry_hook),
        exit_hook(exit_hook) {}
};

class Transform {
 public:
  Transform(const std::string& class_name, const std::string& method_name,
            const std::string& method_signature, const std::string& entry_hook,
            const std::string& exit_hook)
      : class_name_(class_name) {
    hooks_.emplace_back(method_name, method_signature, entry_hook, exit_hook);
  }

  Transform(const std::string& class_name,
            const std::vector<MethodHooks>& hooks)
      : class_name_(class_name), hooks_(hooks) {}

  std::string GetClassName() const { return class_name_; }

  void Apply(std::shared_ptr<ir::DexFile> dex_ir) const {
    slicer::MethodInstrumenter mi(dex_ir);
    for (const MethodHooks& hook : hooks_) {
      if (hook.entry_hook != MethodHooks::kNoHook) {
        const ir::MethodId entry_hook(kHookClassName, hook.entry_hook.c_str());
        mi.AddTransformation<slicer::EntryHook>(
            entry_hook, slicer::EntryHook::Tweak::ThisAsObject);
      }
      if (hook.exit_hook != MethodHooks::kNoHook) {
        const ir::MethodId exit_hook(kHookClassName, hook.exit_hook.c_str());
        mi.AddTransformation<slicer::ExitHook>(exit_hook);
      }
      const std::string class_name = "L" + class_name_ + ";";
      const ir::MethodId target_method(class_name.c_str(),
                                       hook.method_name.c_str(),
                                       hook.method_signature.c_str());
      if (!mi.InstrumentMethod(target_method)) {
        Log::E("Failed to instrument: %s", class_name_.c_str());
      }
    }
  }

 private:
  const char* kHookClassName =
      "Lcom/android/tools/deploy/instrument/InstrumentationHooks;";

  std::string class_name_;
  std::vector<MethodHooks> hooks_;
};

}  // namespace deploy

#endif
