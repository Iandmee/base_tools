# Copyright (C) 2016 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

add_custom_target(lint)

file(GLOB_RECURSE ignore_lint_files ".idea/*")

if(CLANG_TIDY_PATH AND CMAKE_EXPORT_COMPILE_COMMANDS)
  add_custom_target(clang-tidy)
  add_custom_target(clang-tidy-fix)

  file(GLOB_RECURSE clang_tidy_files "*.cc")
  foreach(file ${ignore_lint_files})
    list(REMOVE_ITEM clang_tidy_files ${file})
  endforeach()

  foreach(file ${clang_tidy_files})
    string(REPLACE "/" "-" target_name "${file}.tidy")
    add_custom_target(${target_name}
                      ${CLANG_TIDY_PATH} -p ${CMAKE_BINARY_DIR} ${file} -warnings-as-errors='*' 2> /dev/null)
    add_custom_target(${target_name}-fix
                      ${CLANG_TIDY_PATH} -p ${CMAKE_BINARY_DIR} ${file} -fix 2> /dev/null)

    add_dependencies(${target_name} generate-protobuf)
    add_dependencies(${target_name}-fix generate-protobuf)
    add_dependencies(clang-tidy ${target_name})
    add_dependencies(clang-tidy-fix ${target_name}-fix)
  endforeach()

  add_dependencies(lint clang-tidy)
endif()

if(CLANG_FORMAT_PATH)
  add_custom_target(clang-format)
  add_custom_target(clang-format-fix)

  file(GLOB_RECURSE clang_format_files "*.cc" "*.h")
  foreach(file ${ignore_lint_files})
    list(REMOVE_ITEM clang_format_files ${file})
  endforeach()

  foreach(file ${clang_format_files})
    string(REPLACE "/" "-" target_name "${file}.format")
    add_custom_target(${target_name}
                      ${CLANG_FORMAT_PATH} ${file} | diff ${file} -)
    add_custom_target(${target_name}-fix
                      ${CLANG_FORMAT_PATH} ${file} -i)

    add_dependencies(clang-format ${target_name})
    add_dependencies(clang-format-fix ${target_name}-fix)
  endforeach()

  add_dependencies(lint clang-format)
endif()
