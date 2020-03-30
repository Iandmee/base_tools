/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.build.gradle.internal.tasks.mlkit.codegen.codeinjector

import com.android.build.gradle.internal.tasks.mlkit.codegen.ClassNames
import com.android.build.gradle.internal.tasks.mlkit.codegen.CodeUtils
import com.android.tools.mlkit.TensorInfo
import com.squareup.javapoet.FieldSpec
import com.squareup.javapoet.TypeSpec
import javax.lang.model.element.Modifier

/** Inject fields based on [TensorInfo].  */
class FieldInjector : CodeInjector<TypeSpec.Builder, TensorInfo> {
    override fun inject(classBuilder: TypeSpec.Builder, tensorInfo: TensorInfo) {
        if (!tensorInfo.isMetadataExisted) {
            return
        }

        if (tensorInfo.fileName != null) {
            val fieldName = FieldSpec.builder(
                ClassNames.LIST_OF_STRING,
                CodeUtils.getFileName(tensorInfo.fileName)
            )
                .addModifiers(Modifier.PRIVATE, Modifier.FINAL)
                .addAnnotation(ClassNames.NON_NULL)
                .build()
            classBuilder.addField(fieldName)
        }

        // Add preprocessor and postprocessor fields.
        if (tensorInfo.contentType == TensorInfo.ContentType.IMAGE) {
            val fieldName = FieldSpec.builder(
                ClassNames.IMAGE_PROCESSOR,
                CodeUtils.getProcessorName(tensorInfo)
            )
                .addModifiers(Modifier.PRIVATE, Modifier.FINAL)
                .addAnnotation(ClassNames.NON_NULL)
                .build()
            classBuilder.addField(fieldName)
        } else {
            val fieldName = FieldSpec.builder(
                ClassNames.TENSOR_PROCESSOR,
                CodeUtils.getProcessorName(tensorInfo)
            )
                .addModifiers(Modifier.PRIVATE, Modifier.FINAL)
                .addAnnotation(ClassNames.NON_NULL)
                .build()
            classBuilder.addField(fieldName)
        }
    }
}