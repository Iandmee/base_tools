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

package com.android.tools.mlkit;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.google.common.base.Strings;
import java.nio.FloatBuffer;
import org.tensorflow.lite.support.metadata.schema.AssociatedFile;
import org.tensorflow.lite.support.metadata.schema.Content;
import org.tensorflow.lite.support.metadata.schema.ContentProperties;
import org.tensorflow.lite.support.metadata.schema.ModelMetadata;
import org.tensorflow.lite.support.metadata.schema.NormalizationOptions;
import org.tensorflow.lite.support.metadata.schema.ProcessUnit;
import org.tensorflow.lite.support.metadata.schema.ProcessUnitOptions;
import org.tensorflow.lite.support.metadata.schema.TensorMetadata;

/**
 * Stores necessary data for each single input or output. For tflite model, this class stores
 * necessary data for input or output tensor.
 */
public class TensorInfo {
    private static final String DEFAULT_INPUT_NAME = "inputFeature";
    private static final String DEFAULT_OUTPUT_NAME = "outputFeature";

    public enum DataType {
        UNKNOWN((byte) -1),
        FLOAT32((byte) 0),
        INT32((byte) 2),
        UINT8((byte) 3),
        INT64((byte) 4);

        private final byte id;

        DataType(byte id) {
            this.id = id;
        }

        public static DataType fromByte(byte id) {
            for (DataType type : values()) {
                if (type.id == id) {
                    return type;
                }
            }
            return DataType.UNKNOWN;
        }
    }

    public enum Source {
        UNKNOWN((byte) 0),
        INPUT((byte) 1),
        OUTPUT((byte) 2);

        private final byte id;

        Source(byte id) {
            this.id = id;
        }

        public static Source fromByte(byte id) {
            for (Source source : values()) {
                if (source.id == id) {
                    return source;
                }
            }
            return UNKNOWN;
        }
    }

    public enum FileType {
        UNKNOWN((byte) 0),
        DESCRIPTIONS((byte) 1),
        TENSOR_AXIS_LABELS((byte) 2);

        private final byte id;

        FileType(byte id) {
            this.id = id;
        }

        public static FileType fromByte(byte id) {
            for (FileType type : values()) {
                if (type.id == id) {
                    return type;
                }
            }
            return UNKNOWN;
        }
    }

    public enum ContentType {
        UNKNOWN((byte) 0),
        FEATURE((byte) 1),
        IMAGE((byte) 2);

        private final byte id;

        ContentType(byte id) {
            this.id = id;
        }

        public static ContentType fromByte(byte id) {
            for (ContentType type : values()) {
                if (type.id == id) {
                    return type;
                }
            }
            return UNKNOWN;
        }
    }

    private final boolean metadataExisted;
    private final Source source;
    private final int index;
    private final DataType dataType;
    private final int[] shape;
    private final MetadataExtractor.QuantizationParams quantizationParams;
    private final String name;
    private final String identifierName;
    private final String description;
    private final ContentType contentType;
    private final String fileName;
    private final FileType fileType;
    private final MetadataExtractor.NormalizationParams normalizationParams;
    @Nullable private final ImageProperties imageProperties;

    public TensorInfo(
            boolean metadataExisted,
            Source source,
            int index,
            DataType dataType,
            int[] shape,
            MetadataExtractor.QuantizationParams quantizationParams,
            String name,
            String description,
            ContentType contentType,
            String fileName,
            FileType fileType,
            MetadataExtractor.NormalizationParams normalizationParams,
            @Nullable ImageProperties imageProperties) {
        this.metadataExisted = metadataExisted;
        this.source = source;
        this.index = index;
        this.dataType = dataType;
        this.shape = shape;
        this.quantizationParams = quantizationParams;
        this.name = name;
        this.description = description;
        this.contentType = contentType;
        this.fileName = fileName;
        this.fileType = fileType;
        this.normalizationParams = normalizationParams;
        this.imageProperties = imageProperties;

        this.identifierName = MlkitNames.computeIdentifierName(name, getDefaultName(source, index));
    }

    public boolean isMetadataExisted() {
        return metadataExisted;
    }

    @NonNull
    public Source getSource() {
        return source;
    }

    @NonNull
    public DataType getDataType() {
        return dataType;
    }

    @NonNull
    public int[] getShape() {
        return shape;
    }

    @NonNull
    public MetadataExtractor.QuantizationParams getQuantizationParams() {
        return quantizationParams;
    }

    @NonNull
    public String getName() {
        return name;
    }

    @NonNull
    public String getIdentifierName() {
        return identifierName;
    }

    @NonNull
    public String getDescription() {
        return description;
    }

    @NonNull
    public ContentType getContentType() {
        return contentType;
    }

    @NonNull
    public String getFileName() {
        return fileName;
    }

    @NonNull
    public FileType getFileType() {
        return fileType;
    }

    @NonNull
    public MetadataExtractor.NormalizationParams getNormalizationParams() {
        return normalizationParams;
    }

    @Nullable
    public ImageProperties getImageProperties() {
        return imageProperties;
    }

    public boolean isRGBImage() {
        return imageProperties != null
                && imageProperties.colorSpaceType == ImageProperties.ColorSpaceType.RGB;
    }

    private static class Builder {
        private boolean metadataExisted;
        private Source source = Source.UNKNOWN;
        private int index;
        private DataType dataType = DataType.UNKNOWN;
        private int[] shape;
        private MetadataExtractor.QuantizationParams quantizationParams;
        private String name = "";
        private String description = "";
        private ContentType contentType = ContentType.UNKNOWN;
        private String fileName = "";
        private FileType fileType = FileType.UNKNOWN;
        @Nullable private MetadataExtractor.NormalizationParams normalizationParams;
        @Nullable private ImageProperties imageProperties;

        private Builder setMetadataExisted(boolean metadataExisted) {
            this.metadataExisted = metadataExisted;
            return this;
        }

        private Builder setSource(Source source) {
            this.source = source;
            return this;
        }

        private Builder setIndex(int index) {
            this.index = index;
            return this;
        }

        private Builder setDataType(DataType dataType) {
            this.dataType = dataType;
            return this;
        }

        private Builder setShape(int[] shape) {
            this.shape = shape;
            return this;
        }

        private Builder setQuantizationParams(
                MetadataExtractor.QuantizationParams quantizationParams) {
            this.quantizationParams = quantizationParams;
            return this;
        }

        private Builder setName(String name) {
            this.name = name;
            return this;
        }

        private Builder setDescription(String description) {
            this.description = description;
            return this;
        }

        private Builder setContentType(ContentType contentType) {
            this.contentType = contentType;
            return this;
        }

        private Builder setFileName(String fileName) {
            this.fileName = fileName;
            return this;
        }

        private Builder setFileType(FileType fileType) {
            this.fileType = fileType;
            return this;
        }

        private Builder setNormalizationParams(
                MetadataExtractor.NormalizationParams normalizationParams) {
            this.normalizationParams = normalizationParams;
            return this;
        }

        public Builder setImageProperties(ImageProperties imageProperties) {
            this.imageProperties = imageProperties;
            return this;
        }

        private TensorInfo build() {
            return new TensorInfo(
                    metadataExisted,
                    source,
                    index,
                    dataType,
                    shape,
                    quantizationParams,
                    name,
                    description,
                    contentType,
                    Strings.nullToEmpty(fileName),
                    fileType,
                    normalizationParams != null
                            ? normalizationParams
                            : new MetadataExtractor.NormalizationParams(
                                    toFloatBuffer(0),
                                    toFloatBuffer(1),
                                    toFloatBuffer(Float.MIN_VALUE),
                                    toFloatBuffer(Float.MAX_VALUE)),
                    imageProperties);
        }
    }

    public static TensorInfo parseFrom(MetadataExtractor extractor, Source source, int index) {
        TensorInfo.Builder builder = new TensorInfo.Builder();
        builder.setSource(source).setIndex(index);

        // Deal with data from original model
        if (source == Source.INPUT) {
            builder.setDataType(DataType.fromByte(extractor.getInputTensorType(index)))
                    .setShape(extractor.getInputTensorShape(index))
                    .setQuantizationParams(
                            MetadataExtractor.getQuantizationParams(
                                    extractor.getInputTensor(index)));
        } else {
            builder.setDataType(DataType.fromByte(extractor.getOutputTensorType(index)))
                    .setShape(extractor.getOutputTensorShape(index))
                    .setQuantizationParams(
                            MetadataExtractor.getQuantizationParams(
                                    extractor.getOutputTensor(index)));
        }

        // Deal with data from extra metadata
        ModelMetadata metadata = extractor.getModelMetaData();
        if (metadata == null) {
            builder.setMetadataExisted(false);
        } else {
            builder.setMetadataExisted(true);

            TensorMetadata tensorMetadata =
                    source == Source.INPUT
                            ? metadata.subgraphMetadata(0).inputTensorMetadata(index)
                            : metadata.subgraphMetadata(0).outputTensorMetadata(index);

            builder.setName(Strings.nullToEmpty(tensorMetadata.name()))
                    .setDescription(Strings.nullToEmpty(tensorMetadata.description()))
                    .setContentType(extractContentType(tensorMetadata));

            AssociatedFile file = tensorMetadata.associatedFiles(0);
            if (file != null) {
                builder.setFileName(file.name()).setFileType(FileType.fromByte(file.type()));
            }

            if (builder.contentType == ContentType.IMAGE) {
                org.tensorflow.lite.support.metadata.schema.ImageProperties properties =
                        (org.tensorflow.lite.support.metadata.schema.ImageProperties)
                                tensorMetadata
                                        .content()
                                        .contentProperties(
                                                new org.tensorflow.lite.support.metadata.schema
                                                        .ImageProperties());
                builder.setImageProperties(
                        new ImageProperties(
                                ImageProperties.ColorSpaceType.fromByte(properties.colorSpace())));
            }

            NormalizationOptions normalizationOptions = extractNormalizationOptions(tensorMetadata);
            FloatBuffer mean =
                    normalizationOptions != null
                            ? normalizationOptions.meanAsByteBuffer().asFloatBuffer()
                            : toFloatBuffer(0);
            FloatBuffer std =
                    normalizationOptions != null
                            ? normalizationOptions.stdAsByteBuffer().asFloatBuffer()
                            : toFloatBuffer(1);
            FloatBuffer min =
                    tensorMetadata.stats() != null
                            ? tensorMetadata.stats().minAsByteBuffer().asFloatBuffer()
                            : toFloatBuffer(Float.MIN_VALUE);
            FloatBuffer max =
                    tensorMetadata.stats() != null
                            ? tensorMetadata.stats().maxAsByteBuffer().asFloatBuffer()
                            : toFloatBuffer(Float.MAX_VALUE);
            builder.setNormalizationParams(
                    new MetadataExtractor.NormalizationParams(mean, std, min, max));
        }

        return builder.build();
    }

    private static FloatBuffer toFloatBuffer(float value) {
        return FloatBuffer.wrap(new float[] {value});
    }

    private static String getDefaultName(Source source, int index) {
        return (source == Source.INPUT ? DEFAULT_INPUT_NAME : DEFAULT_OUTPUT_NAME) + index;
    }

    public static TensorInfo.ContentType extractContentType(TensorMetadata tensorMetadata) {
        Content content = tensorMetadata.content();
        if (content == null) {
            return ContentType.UNKNOWN;
        }
        byte type = content.contentPropertiesType();
        if (type == ContentProperties.ImageProperties) {
            return ContentType.IMAGE;
        } else if (type == ContentProperties.FeatureProperties) {
            return ContentType.FEATURE;
        }
        return ContentType.UNKNOWN;
    }

    private static NormalizationOptions extractNormalizationOptions(TensorMetadata tensorMetadata) {
        for (int i = 0; i < tensorMetadata.processUnitsLength(); i++) {
            ProcessUnit unit = tensorMetadata.processUnits(i);
            if (unit.optionsType() == ProcessUnitOptions.NormalizationOptions) {
                return (NormalizationOptions) unit.options(new NormalizationOptions());
            }
        }

        return null;
    }

    public static class ImageProperties {
        public enum ColorSpaceType {
            UNKNOWN(0),
            RGB(1),
            GRAYSCALE(2);

            private final int id;

            ColorSpaceType(int id) {
                this.id = id;
            }

            public static ColorSpaceType fromByte(byte id) {
                for (ColorSpaceType type : values()) {
                    if (type.id == id) {
                        return type;
                    }
                }
                return UNKNOWN;
            }
        }

        public final ColorSpaceType colorSpaceType;

        public ImageProperties(ColorSpaceType colorSpaceType) {
            this.colorSpaceType = colorSpaceType;
        }
    }
}
