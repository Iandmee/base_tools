/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.jdwppacket

data class LineTableCmd(val refType: Long, val methodID: Long) : Cmd(Method.LineTable), Keyable {

  override fun writePayload(writer: Writer) {
    writer.putReferenceTypeID(refType)
    writer.putMethodID(methodID)
  }

  override fun paramsKey(): String {
    return "$refType-$methodID"
  }

  companion object {
    @JvmStatic
    fun parse(reader: MessageReader): LineTableCmd {
      return LineTableCmd(reader.getReferenceTypeID(), reader.getMethodID())
    }
  }
}