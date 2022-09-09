/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.build.gradle.internal.tasks.featuresplit

import com.android.sdklib.AndroidVersion
import com.google.common.annotations.VisibleForTesting
import com.google.common.base.Charsets
import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableMap
import com.google.common.collect.ImmutableSet
import com.google.common.io.Files
import com.google.gson.GsonBuilder
import com.google.gson.TypeAdapter
import com.google.gson.reflect.TypeToken
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter
import java.io.File
import java.io.FileReader
import java.io.IOException

/** Container for all the feature split metadata.  */
class FeatureSetMetadata private constructor(
        val sourceFile: File?,
        private val featureSplits: MutableSet<FeatureInfo>,
        private val maxNumberOfSplitsBeforeO: Int,
) {

    constructor(maxNumberOfSplitsBeforeO: Int) : this(
            sourceFile = null,
            featureSplits = HashSet(),
            maxNumberOfSplitsBeforeO = maxNumberOfSplitsBeforeO,
    )

    private constructor(featureSplits: Set<FeatureInfo>, sourceFile: File?) : this(
            maxNumberOfSplitsBeforeO = Integer.max(MAX_NUMBER_OF_SPLITS_BEFORE_O,
                    featureSplits.size),
            featureSplits = ImmutableSet.copyOf(featureSplits),
            sourceFile = sourceFile,
    )

    fun addFeatureSplit(
            minSdkVersion: Int,
            modulePath: String,
            featureName: String,
            packageName: String) {
        val id: Int = if (minSdkVersion < AndroidVersion.VersionCodes.O) {
            if (featureSplits.size >= maxNumberOfSplitsBeforeO) {
                throw RuntimeException("You have reached the maximum number of feature splits : "
                        + maxNumberOfSplitsBeforeO)
            }
            // allocate split ID backwards excluding BASE_ID.
            BASE_ID - 1 - featureSplits.size
        } else {
            if (featureSplits.size >= MAX_NUMBER_OF_SPLITS_STARTING_IN_O) {
                throw RuntimeException("You have reached the maximum number of feature splits : "
                        + MAX_NUMBER_OF_SPLITS_STARTING_IN_O)
            }
            // allocated forward excluding BASE_ID
            BASE_ID + 1 + featureSplits.size
        }
        featureSplits.add(FeatureInfo(modulePath, featureName, id, packageName))
    }

    fun getResOffsetFor(modulePath: String): Int? {
        return featureSplits.firstOrNull { it.modulePath == modulePath }?.resOffset
    }

    fun getFeatureNameFor(modulePath: String): String? {
        return featureSplits.firstOrNull { it.modulePath == modulePath }?.featureName
    }

    val featureNameToNamespaceMap: Map<String, String>
        get() = ImmutableMap.copyOf(featureSplits.associateBy({ it.featureName }) { it.namespace })

    @Throws(IOException::class)
    fun save(outputFile: File) {
        JsonWriter(outputFile.bufferedWriter()).use {
            FeatureSetMetadataTypeAdapter(outputFile).write(it, this)
        }
    }

    private class FeatureInfo(
            val modulePath: String,
            val featureName: String,
            val resOffset: Int,
            val namespace: String,
    )

    companion object {

        const val MAX_NUMBER_OF_SPLITS_BEFORE_O = 50
        const val MAX_NUMBER_OF_SPLITS_STARTING_IN_O = 127

        @VisibleForTesting
        const val OUTPUT_FILE_NAME = "feature-metadata.json"

        /** Base module or application module resource ID  */
        @VisibleForTesting
        const val BASE_ID = 0x7F

        /**
         * Loads the feature set metadata file
         *
         * @param input the location of the file, or the folder that contains it.
         * @return the FeatureSetMetadata instance that contains all the data from the file
         * @throws IOException if the loading failed.
         */
        @JvmStatic
        @Throws(IOException::class)
        fun load(input: File): FeatureSetMetadata {
            val inputFile = if (input.isDirectory) File(input, OUTPUT_FILE_NAME) else input
            JsonReader(inputFile.bufferedReader()).use {
                return FeatureSetMetadataTypeAdapter(inputFile).read(it)
            }
        }


    }

    private class FeatureSetMetadataTypeAdapter(private val sourceFile: File?) : TypeAdapter<FeatureSetMetadata>() {

        override fun write(writer: JsonWriter, metadata: FeatureSetMetadata) {
            with(writer) {
                beginArray()
                for (split in metadata.featureSplits) {
                    FeatureInfoTypeAdapter.write(writer, split)
                }
                writer.endArray()
            }
        }

        override fun read(reader: JsonReader): FeatureSetMetadata {
            val splits = ImmutableSet.builder<FeatureInfo>()
            with(reader) {
                beginArray()
                while (hasNext()) {
                    splits.add(FeatureInfoTypeAdapter.read(reader))
                }
                endArray()
            }
            return FeatureSetMetadata(splits.build(), sourceFile)
        }
    }

    private object FeatureInfoTypeAdapter : TypeAdapter<FeatureInfo>() {

        override fun write(writer: JsonWriter, featureInfo: FeatureInfo) {
            with(writer) {
                beginObject()
                name("modulePath").value(featureInfo.modulePath)
                name("featureName").value(featureInfo.featureName)
                name("resOffset").value(featureInfo.resOffset)
                name("namespace").value(featureInfo.namespace)
                endObject()
            }
        }

        override fun read(reader: JsonReader): FeatureInfo {
            with(reader) {
                beginObject()
                var modulePath: String? = null
                var featureName: String? = null
                var resOffset: Int? = null
                var namespace: String? = null
                while (hasNext()) {
                    when (nextName()) {
                        "modulePath" -> modulePath = nextString()
                        "featureName" -> featureName = nextString()
                        "resOffset" -> resOffset = nextInt()
                        "namespace" -> namespace = nextString()
                        else -> skipValue()
                    }
                }
                val featureInfo = FeatureInfo(modulePath!!, featureName!!, resOffset!!, namespace!!)
                endObject()
                return featureInfo
            }
        }
    }
}
