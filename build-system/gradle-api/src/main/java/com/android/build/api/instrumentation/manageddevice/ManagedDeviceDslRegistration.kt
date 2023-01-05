/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.build.api.instrumentation.manageddevice

import com.android.build.api.dsl.Device
import org.gradle.api.Incubating

/**
 * Class containing all information regarding the DSL for a new Custom Managed Device Type. For
 * use with the [CustomManagedDeviceRegistry].
 *
 * @param deviceApi The api interface for the Custom Managed Device, which should be visible to
 *     developers using the DSL.
 * @param deviceImpl the actual implementation of the [deviceApi] class.
 *
 * @suppress Do not use from production code. All properties in this interface are exposed for
 * prototype.
 */
@Incubating
class ManagedDeviceDslRegistration <DeviceT : Device> @Incubating constructor(
    @get:Incubating val deviceApi : Class<DeviceT>,
    @get:Incubating val deviceImpl : Class<out DeviceT>
)
