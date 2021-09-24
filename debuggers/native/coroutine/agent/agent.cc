#include "tools/base/debuggers/native/coroutine/agent/DebugProbesKt.h"
#include "tools/base/debuggers/native/coroutine/agent/jni_utils.h"
#include "tools/base/transport/native/jvmti/jvmti_helper.h"
#include "tools/base/transport/native/utils/log.h"

#include "slicer/dex_ir.h"
#include "slicer/instrumentation.h"
#include "slicer/reader.h"
#include "slicer/writer.h"

// TODO(b/182023904): it is against best practices to use the same namespace as
// a dependency (profilerlib)
using namespace profiler;

/**
 * This agent works as follow:
 * 1. Register a ClassFileLoadHook.
 * 2. Monitor to find kotlin/coroutines/jvm/internal/DebugProbesKt.
 * 3. On found,
 *   3.1 Check if kotlinx/coroutines/debug/internal/DebugProbesKt is loaded or
 *     loadable (it only exists in newer versions of coroutine lib).
 *     3.1.1 If yes, instrument methods in
 *       kotlin/coroutines/jvm/internal/DebugProbesK to call methods in
 *       kotlinx/coroutines/debug/internal/DebugProbesKt.
 *     3.1.2 If not, check that DebugProbesImpl is loaded or loadable.
 *       3.1.2.1 Replace DebugProbesKt class data with
 *         DebugProbesKt from DebugProbesKt.h. DebugProbesKt.h is
 *         created by a genrule, using resource/DebugProbesKt.bindex.
 *         resource/DebugProbesKt.bindex is a dexed version
 *         of DebugProbesKt.bin from kotlinx-coroutines-core.
 *   3.2 Set AgentInstallationType#isInstalledStatically or
 *       AgentPremain#isInstalledStatically to true. This tells
 *       the coroutine lib that DebugProbesKt should not be replaced
 *       lazily when DebugProbesImpl#install is called.
 *       The lazy replacement uses ByteBuddy and Java instrumentation
 *       apis, that are not supported on Android.
 *   3.3 Call `install` on DebugProbesImpl.
 *   3.4 Unregister ClassFileLoadHook.
 */

// TODO(b/182023904): remove all calls to LOG:D

const std::string debug_debugProbesKt =
    "Lkotlinx/coroutines/debug/internal/DebugProbesKt;";
const std::string stdlib_debugProbesKt =
    "Lkotlin/coroutines/jvm/internal/DebugProbesKt;";

class JvmtiAllocator : public dex::Writer::Allocator {
 public:
  JvmtiAllocator(jvmtiEnv* jvmti_env) : jvmti_env_(jvmti_env) {}

  virtual void* Allocate(size_t size) {
    unsigned char* alloc = nullptr;
    jvmtiError err_num = jvmti_env_->Allocate(size, &alloc);

    if (err_num != JVMTI_ERROR_NONE) {
      Log::E(Log::Tag::COROUTINE_DEBUGGER, "JVMTI error: %d", err_num);
    }

    return (void*)alloc;
  }

  virtual void Free(void* ptr) {
    if (ptr == nullptr) {
      return;
    }

    jvmtiError err_num = jvmti_env_->Deallocate((unsigned char*)ptr);

    if (err_num != JVMTI_ERROR_NONE) {
      Log::E(Log::Tag::COROUTINE_DEBUGGER, "JVMTI error: %d", err_num);
    }
  }

 private:
  jvmtiEnv* jvmti_env_;
};

struct InstrumentedClass {
  unsigned char* new_class_data;
  int new_class_data_len;
  bool success;
};

/**
 * Get the exceptions stacktrace and log it.
 */
void printStackTrace(JNIEnv* jni) {
  std::unique_ptr<jniutils::StackTrace> stackTrace =
      jniutils::getExceptionStackTrace(jni);
  if (stackTrace == nullptr) {
    return;
  }
  std::string stringStackTrace = jniutils::stackTraceToString(move(stackTrace));
  Log::D(Log::Tag::COROUTINE_DEBUGGER, "%s", stringStackTrace.c_str());
}

// check if DebugProbesImpl exists, then call
// DebugProbesImpl#install
bool installDebugProbes(JNIEnv* jni) {
  jclass klass =
      jni->FindClass("kotlinx/coroutines/debug/internal/DebugProbesImpl");
  if (klass == nullptr) {
    Log::D(Log::Tag::COROUTINE_DEBUGGER, "DebugProbesImpl not found");
    return false;
  }

  Log::D(Log::Tag::COROUTINE_DEBUGGER, "DebugProbesImpl found");

  // get DebugProbesImpl constructor
  jmethodID constructor = jni->GetMethodID(klass, "<init>", "()V");
  if (constructor == nullptr) {
    Log::D(Log::Tag::COROUTINE_DEBUGGER,
           "DebugProbesImpl constructor not found");
    return false;
  }

  // create DebugProbesImpl by calling constructor
  jobject debug_probes_impl_obj = jni->NewObject(klass, constructor);
  if (jni->ExceptionOccurred()) {
    Log::D(Log::Tag::COROUTINE_DEBUGGER,
           "DebugProbesImpl constructor threw an exception.");
    printStackTrace(jni);
    return false;
  }

  // get install method id
  jmethodID install = jni->GetMethodID(klass, "install", "()V");
  if (install == nullptr) {
    Log::D(Log::Tag::COROUTINE_DEBUGGER, "DebugProbesImpl#install not found");
    return false;
  }

  // invoke install method
  jni->CallVoidMethod(debug_probes_impl_obj, install);

  if (jni->ExceptionOccurred()) {
    Log::D(Log::Tag::COROUTINE_DEBUGGER,
           "DebugProbesImpl#install threw an exception.");
    printStackTrace(jni);
    return false;
  }

  Log::D(Log::Tag::COROUTINE_DEBUGGER, "DebugProbesImpl#install called.");
  return true;
}

/**
 * Instrument DebugProbesKt from kotlin stdlib, to call respective methods in
 * DebugProbesKt from kotlinx-coroutines-core
 */
InstrumentedClass instrumentClass(jvmtiEnv* jvmti, std::string class_name,
                                  const unsigned char* class_data,
                                  int class_data_len) {
  InstrumentedClass instrumentedClass{};

  dex::Reader reader(class_data, class_data_len);
  auto class_index = reader.FindClassIndex(class_name.c_str());
  if (class_index == dex::kNoIndex) {
    Log::D(Log::Tag::COROUTINE_DEBUGGER, "Could not find class index for %s",
           class_name.c_str());
    instrumentedClass.success = false;
    return instrumentedClass;
  }

  reader.CreateClassIr(class_index);
  auto dex_ir = reader.GetIr();

  // TODO(b/182023904): instead of hard coding the methods we should iterate
  // over all the methods of kotlinx/coroutines/debug/internal/DebugProbesKt and
  // match them with methods in kotlinx/coroutines/debug/internal/DebugProbesKt

  // probeCoroutineCreated
  slicer::MethodInstrumenter miCreated(dex_ir);
  miCreated.AddTransformation<slicer::ExitHook>(
      ir::MethodId(debug_debugProbesKt.c_str(), "probeCoroutineCreated"));

  if (!miCreated.InstrumentMethod(
          ir::MethodId(stdlib_debugProbesKt.c_str(), "probeCoroutineCreated",
                       "(Lkotlin/coroutines/Continuation;)Lkotlin/coroutines/"
                       "Continuation;"))) {
    Log::D(Log::Tag::COROUTINE_DEBUGGER,
           "Error instrumenting DebugProbesKt.probeCoroutineCreated");
    instrumentedClass.success = false;
    return instrumentedClass;
  }

  // probeCoroutineResumed
  slicer::MethodInstrumenter miResumed(dex_ir);
  miResumed.AddTransformation<slicer::EntryHook>(
      ir::MethodId(debug_debugProbesKt.c_str(), "probeCoroutineResumed"));

  if (!miResumed.InstrumentMethod(
          ir::MethodId(stdlib_debugProbesKt.c_str(), "probeCoroutineResumed",
                       "(Lkotlin/coroutines/Continuation;)V"))) {
    Log::D(Log::Tag::COROUTINE_DEBUGGER,
           "Error instrumenting DebugProbesKt.probeCoroutineResumed");
    instrumentedClass.success = false;
    return instrumentedClass;
  }

  // probeCoroutineSuspended
  slicer::MethodInstrumenter miSuspended(dex_ir);
  miSuspended.AddTransformation<slicer::EntryHook>(
      ir::MethodId(debug_debugProbesKt.c_str(), "probeCoroutineSuspended"));

  if (!miSuspended.InstrumentMethod(
          ir::MethodId(stdlib_debugProbesKt.c_str(), "probeCoroutineSuspended",
                       "(Lkotlin/coroutines/Continuation;)V"))) {
    Log::D(Log::Tag::COROUTINE_DEBUGGER,
           "Error instrumenting DebugProbesKt.probeCoroutineSuspended");
    instrumentedClass.success = false;
    return instrumentedClass;
  }

  Log::D(Log::Tag::COROUTINE_DEBUGGER, "instrumentation done");

  size_t new_image_size = 0;
  dex::u1* new_image = nullptr;
  dex::Writer writer(dex_ir);

  JvmtiAllocator allocator(jvmti);
  new_image = writer.CreateImage(&allocator, &new_image_size);

  if (new_image == nullptr) {
    instrumentedClass.success = false;
    Log::D(Log::Tag::COROUTINE_DEBUGGER,
           "Failed to create new image for class %s", class_name.c_str());
    return instrumentedClass;
  }

  instrumentedClass.new_class_data = new_image;
  instrumentedClass.new_class_data_len = new_image_size;
  instrumentedClass.success = true;

  return instrumentedClass;
}

// TODO(b/182023182) make sure `setInstalledStatically$kotlinx_coroutines_core`
// will be the final name, when they release kotlinx-coroutines-core
// 1.6 in October
/**
 * Try to set
 * kotlinx.coroutines.debug.AgentInstallationType#setInstalledStatically$kotlinx_coroutines_core
 * to true.
 */
bool setAgentInstallationType(JNIEnv* jni) {
  const std::string class_name = "AgentInstallationType";
  const std::string class_full_name =
      "kotlinx/coroutines/debug/internal/" + class_name;
  const std::string method_name =
      "setInstalledStatically$kotlinx_coroutines_core";

  jclass klass_agentInstallationType = jni->FindClass(class_full_name.c_str());
  if (klass_agentInstallationType == nullptr) {
    Log::D(Log::Tag::COROUTINE_DEBUGGER, "%s not found.",
           class_full_name.c_str());
    return false;
  }

  const std::string sig = "L" + class_full_name + ";";
  jfieldID instance_filedId = jni->GetStaticFieldID(klass_agentInstallationType,
                                                    "INSTANCE", sig.c_str());

  if (instance_filedId == nullptr) {
    Log::D(Log::Tag::COROUTINE_DEBUGGER, "%s#INSTANCE not found.",
           class_full_name.c_str());
    return false;
  }

  jobject obj_agentInstallationType =
      jni->GetStaticObjectField(klass_agentInstallationType, instance_filedId);
  if (obj_agentInstallationType == nullptr) {
    Log::D(Log::Tag::COROUTINE_DEBUGGER, "Failed to retrieve %s#INSTANCE.",
           class_full_name.c_str());
    return false;
  }

  jmethodID mid_setIsInstalledStatically = jni->GetMethodID(
      klass_agentInstallationType, method_name.c_str(), "(Z)V");
  if (mid_setIsInstalledStatically == nullptr) {
    Log::D(Log::Tag::COROUTINE_DEBUGGER, "%s#%s(Z)V not found.",
           class_name.c_str(), method_name.c_str());
    return false;
  }

  jni->CallVoidMethod(obj_agentInstallationType, mid_setIsInstalledStatically,
                      true);

  if (jni->ExceptionOccurred()) {
    Log::D(Log::Tag::COROUTINE_DEBUGGER, "%s#%s(Z)V threw an exception.",
           class_name.c_str(), method_name.c_str());
    printStackTrace(jni);
    return false;
  }

  Log::D(Log::Tag::COROUTINE_DEBUGGER, "%s#%s set to true.", class_name.c_str(),
         method_name.c_str());
  return true;
}

/**
 * Try to set kotlinx.coroutines.debug.AgentPremain#isInstalled statically to
 * true.
 */
bool setAgentPremainInstalledStatically(JNIEnv* jni) {
  jclass klass_agentPremain =
      jni->FindClass("kotlinx/coroutines/debug/AgentPremain");
  if (klass_agentPremain == nullptr) {
    Log::D(Log::Tag::COROUTINE_DEBUGGER, "AgentPremain not found.");
    return false;
  }

  jfieldID instance_filedId =
      jni->GetStaticFieldID(klass_agentPremain, "INSTANCE",
                            "Lkotlinx/coroutines/debug/AgentPremain;");
  if (instance_filedId == nullptr) {
    Log::D(Log::Tag::COROUTINE_DEBUGGER, "AgentPremain#INSTANCE not found.");
    return false;
  }

  jobject obj_agentPremain =
      jni->GetStaticObjectField(klass_agentPremain, instance_filedId);
  if (obj_agentPremain == nullptr) {
    Log::D(Log::Tag::COROUTINE_DEBUGGER,
           "Failed to retrieve AgentPremain#INSTANCE.");
    return false;
  }

  jmethodID mid_setIsInstalledStatically =
      jni->GetMethodID(klass_agentPremain, "setInstalledStatically", "(Z)V");
  if (mid_setIsInstalledStatically == nullptr) {
    Log::D(Log::Tag::COROUTINE_DEBUGGER,
           "AgentPremain#setInstalledStatically(Z)V not found.");
    return false;
  }

  jni->CallVoidMethod(obj_agentPremain, mid_setIsInstalledStatically, true);

  if (jni->ExceptionOccurred()) {
    Log::D(Log::Tag::COROUTINE_DEBUGGER,
           "AgentPremain#setInstalledStatically(Z)V threw an exception.");
    printStackTrace(jni);
    return false;
  }

  Log::D(Log::Tag::COROUTINE_DEBUGGER,
         "AgentPremain#isInstalledStatically set to true.");
  return true;
}

static void JNICALL
ClassFileLoadHook(jvmtiEnv* jvmti, JNIEnv* jni, jclass class_being_redefined,
                  jobject loader, const char* name, jobject protection_domain,
                  jint class_data_len, const unsigned char* class_data,
                  jint* new_class_data_len, unsigned char** new_class_data) {
  // transform DebugProbesKt
  std::string class_name(name);
  if (class_name != "kotlin/coroutines/jvm/internal/DebugProbesKt") {
    return;
  }

  // set AgentInstallationType#isInstalledStatically to true
  bool setAgentInstallationTypeSuccessful = setAgentInstallationType(jni);

  if (!setAgentInstallationTypeSuccessful) {
    if (jni->ExceptionCheck()) {
      jni->ExceptionClear();
    }
    // AgentInstallationType#isInstalledStatically was introduced on newer
    // versions of coroutines
    // see for more info: https://github.com/Kotlin/kotlinx.coroutines/pull/2912
    // we should try to set that first, if it fails we can fall back to the
    // older way, through AgentPremain.
    bool setSuccessful = setAgentPremainInstalledStatically(jni);

    if (!setSuccessful) {
      if (jni->ExceptionCheck()) {
        jni->ExceptionClear();
      }
      SetEventNotification(jvmti, JVMTI_DISABLE,
                           JVMTI_EVENT_CLASS_FILE_LOAD_HOOK);
      return;
    }
  }

  // call DebugProbesImpl#install
  bool installed = installDebugProbes(jni);

  if (!installed) {
    if (jni->ExceptionCheck()) {
      jni->ExceptionClear();
    }
    SetEventNotification(jvmti, JVMTI_DISABLE,
                         JVMTI_EVENT_CLASS_FILE_LOAD_HOOK);
    return;
  }

  // check if kotlinx/coroutines/debug/internal/DebugProbesKt is loadable.
  jclass klass =
      jni->FindClass("kotlinx/coroutines/debug/internal/DebugProbesKt");
  if (klass == nullptr) {
    // clear exception thrown by failed FindClass
    if (jni->ExceptionCheck()) {
      jni->ExceptionClear();
    }

    // backward compatible - replace
    // kotlin/coroutines/jvm/internal/DebugProbesKt with the one from the .bin
    // bundled with the agent.
    Log::D(Log::Tag::COROUTINE_DEBUGGER, "Transforming %s", name);

    *new_class_data_len = kDebugProbesKt_len;
    *new_class_data = kDebugProbesKt;

    Log::D(Log::Tag::COROUTINE_DEBUGGER, "Successfully transformed %s", name);
  } else {
    // forward compatible - instrument
    // kotlin/coroutines/jvm/internal/DebugProbesKt to call methods in
    // kotlinx/coroutines/debug/internal/DebugProbesKt
    Log::D(Log::Tag::COROUTINE_DEBUGGER, "Instrumenting %s", name);

    std::string class_name = "L" + std::string(name) + ";";
    InstrumentedClass instrumentedClass =
        instrumentClass(jvmti, class_name, class_data, class_data_len);
    if (!instrumentedClass.success) {
      Log::D(Log::Tag::COROUTINE_DEBUGGER, "Instrumentation of %s failed",
             name);

      if (jni->ExceptionCheck()) {
        jni->ExceptionClear();
      }
      return;
    }

    *new_class_data_len = instrumentedClass.new_class_data_len;
    *new_class_data = instrumentedClass.new_class_data;

    Log::D(Log::Tag::COROUTINE_DEBUGGER, "Successfully instrumented %s", name);
  }

  // DebugProbesKt is the only class we need to transform, so we can disable
  // events
  SetEventNotification(jvmti, JVMTI_DISABLE, JVMTI_EVENT_CLASS_FILE_LOAD_HOOK);
}

extern "C" JNIEXPORT jint JNICALL Agent_OnAttach(JavaVM* vm, char* options,
                                                 void* reserved) {
  // This will attach the current thread to the vm, otherwise CreateJvmtiEnv(vm)
  // below will return JNI_EDETACHED error code.
  GetThreadLocalJNI(vm);

  jvmtiEnv* jvmti = CreateJvmtiEnv(vm);
  if (jvmti == nullptr) {
    Log::E(Log::Tag::COROUTINE_DEBUGGER, "Failed to initialize JVMTI env.");
    return -1;
  }

  // set JVMTI capabilities
  jvmtiCapabilities capa;
  bool hasError =
      CheckJvmtiError(jvmti, jvmti->GetPotentialCapabilities(&capa));
  if (hasError) {
    Log::E(Log::Tag::COROUTINE_DEBUGGER,
           "JVMTI GetPotentialCapabilities error.");
    return -1;
  }
  SetAllCapabilities(jvmti);
  Log::D(Log::Tag::COROUTINE_DEBUGGER, "JVMTI SetAllCapabilities done.");

  // set JVMTI callbacks
  jvmtiEventCallbacks callbacks;
  callbacks.ClassFileLoadHook = &ClassFileLoadHook;

  hasError = CheckJvmtiError(
      jvmti, jvmti->SetEventCallbacks(&callbacks, (jint)sizeof(callbacks)));
  if (hasError) {
    Log::E(Log::Tag::COROUTINE_DEBUGGER, "JVMTI SetEventCallbacks error");
    return -1;
  }
  Log::D(Log::Tag::COROUTINE_DEBUGGER, "JVMTI SetEventCallbacks done.");

  // enable events notification
  // TODO(b/182023904): see b/152421535, make sure that this doesn't crash on
  // pre API 29
  SetEventNotification(jvmti, JVMTI_ENABLE, JVMTI_EVENT_CLASS_FILE_LOAD_HOOK);

  return JNI_OK;
}
