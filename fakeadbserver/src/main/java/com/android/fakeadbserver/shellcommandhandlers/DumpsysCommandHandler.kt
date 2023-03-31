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
 */
package com.android.fakeadbserver.shellcommandhandlers

import com.android.fakeadbserver.DeviceState
import com.android.fakeadbserver.FakeAdbServer
import com.android.fakeadbserver.ShellProtocolType
import com.android.fakeadbserver.services.ShellCommandOutput
import java.io.IOException

class DumpsysCommandHandler(shellProtocolType: ShellProtocolType) : SimpleShellHandler(
    shellProtocolType,"dumpsys") {

    override fun execute(
      fakeAdbServer: FakeAdbServer,
      statusWriter: StatusWriter,
      shellCommandOutput: ShellCommandOutput,
      device: DeviceState,
      shellCommand: String,
      shellCommandArgs: String?
    ) {
        try {
            if (shellCommandArgs == null) {
                statusWriter.writeFail()
                return
            }

            statusWriter.writeOk()

            val response: String = when {
                shellCommandArgs.startsWith("package") -> packageCommandHandler()
                else -> ""
            }

            shellCommandOutput.writeStdout(response)
        } catch (ignored: IOException) {
        }
    }

  private fun packageCommandHandler(): String {
    // Treat all packages as not installed:
    return """
Dexopt state:
  Unable to find package: google.simpleapplication"""
  }
}
