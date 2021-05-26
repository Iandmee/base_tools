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

#include <string>
#include <vector>

#include "slicer/writer.h"
#include "tools/base/deploy/agent/native/transform/transforms.h"

namespace deploy {

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

bool InstrumentApplication(jvmtiEnv* jvmti, JNIEnv* jni,
                           const std::string& package_name, bool overlay_swap);

class Instrumenter {
 public:
  Instrumenter(jvmtiEnv* jvmti, JNIEnv* jni, const TransformCache& cache)
      : jvmti_(jvmti), jni_(jni), cache_(cache), caching_enabled_(true) {}

  bool Instrument(const Transform& transform) const;
  bool Instrument(const std::vector<const Transform*>& transforms) const;

  void SetCachingEnabled(bool enabled);

 private:
  jvmtiEnv* jvmti_;
  JNIEnv* jni_;

  TransformCache cache_;
  bool caching_enabled_;

  bool ApplyCachedTransforms(
      const std::vector<jclass> classes,
      const std::vector<const Transform*>& transforms) const;

  bool ApplyTransforms(const std::vector<const Transform*>& transforms) const;
};

}  // namespace deploy

#endif
