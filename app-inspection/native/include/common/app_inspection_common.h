#ifndef APP_INSPECTION_COMMON_H_
#define APP_INSPECTION_COMMON_H_

#include <jni.h>
#include <string>

namespace app_inspection {

extern const char* ARTIFACT_COORDINATE_CLASS;
extern const std::string ARTIFACT_COORDINATE_TYPE;

extern const char* LIBRARY_COMPATIBILITY_CLASS;
extern const std::string LIBRARY_COMPATIBILITY_TYPE;

extern jobject CreateArtifactCoordinate(JNIEnv* env, jstring group_id,
                                        jstring artifact_id, jstring version);

extern jobject CreateLibraryCompatibility(
    JNIEnv* env, jobject artifact, jobjectArray expected_library_class_names);

}  // namespace app_inspection

#endif  // APP_INSPECTION_COMMON_H_
