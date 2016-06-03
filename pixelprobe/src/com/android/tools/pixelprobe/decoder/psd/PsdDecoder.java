/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.tools.pixelprobe.decoder.psd;

import com.android.tools.chunkio.ChunkIO;
import com.android.tools.pixelprobe.*;
import com.android.tools.pixelprobe.Image;
import com.android.tools.pixelprobe.decoder.Decoder;
import com.android.tools.pixelprobe.effect.Shadow;
import com.android.tools.pixelprobe.color.Colors;
import com.android.tools.pixelprobe.util.Images;
import com.android.tools.pixelprobe.util.Bytes;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.color.ColorSpace;
import java.awt.color.ICC_ColorSpace;
import java.awt.color.ICC_Profile;
import java.awt.geom.AffineTransform;
import java.awt.geom.GeneralPath;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Decodes PSD (Adobe Photoshop) streams. Accepts the "psd" and "photoshop"
 * format strings. The PSB variant of the Photoshop format, used to store
 * large images (> 30,000 pixels in either dimension), is currently not
 * supported.
 */
@SuppressWarnings({"UseJBColor"})
public final class PsdDecoder extends Decoder {
    /**
     * Constant to convert centimeters to inches.
     */
    private static final float CENTIMETER_TO_INCH = 2.54f;

    /**
     * Possible text alignments found in text layers. Alignments are encoded as
     * numbers in PSD file. These numbers map to the indices of this array (0 is
     * left, 1 is right, etc.).
     */
    private static final String[] alignments = new String[] {
        "LEFT", "RIGHT", "CENTER", "JUSTIFY"
    };

    /**
     * The PsdDecoder only supports .psd files. There is no support for .psb
     * (large Photoshop documents) at the moment.
     */
    public PsdDecoder() {
        super("psd", "photoshop");
    }

    @Override
    public boolean accept(InputStream in) {
        // Read the header and make sure it is valid before we accept the stream
        return ChunkIO.read(in, Header.class) != null;
    }

    @Override
    public Image decode(InputStream in) throws IOException {
        try {
            Image.Builder image = new Image.Builder();

            // The PsdFile class represents the entire document
            PsdFile psd = ChunkIO.read(in, PsdFile.class);

            if (psd != null) {
                // Extract and decode raw PSD data
                // The data is transformed into a generic Image API
                extractHeaderData(image, psd.header);
                resolveBlocks(image, psd.resources);
                extractLayers(image, psd.layersInfo);
                decodeImageData(image, psd);
            }

            return image.build();
        } catch (Throwable t) {
            throw new IOException("Error while decoding PSD stream", t);
        }
    }

    /**
     * Extract layers information from the PSD raw data into the specified image.
     * Not all layers will be extracted.
     *  @param image The user image
     * @param layersInfo The document's layers information
     */
    private static void extractLayers(Image.Builder image, LayersInformation layersInfo) {
        LayersList layersList = layersInfo.layers;
        if (layersList == null) return;

        List<RawLayer> rawLayers = layersList.layers;
        Deque<Layer.Builder> stack = new LinkedList<>();

        int[] unnamedCounts = new int[Layer.Type.values().length];
        Arrays.fill(unnamedCounts, 1);

        // The layers count can be negative according to the Photoshop file specification
        // A negative count indicates that the first alpha channel contains the transparency
        // data for the merged (composited) result
        for (int i = Math.abs(layersList.count) - 1; i >= 0; i--) {
            RawLayer rawLayer = rawLayers.get(i);

            Map<String, LayerProperty> properties = rawLayer.extras.properties;
            LayerProperty sectionProperty = properties.get(LayerProperty.KEY_SECTION);

            // Assume we are decoding a bitmap (raster) layer by default
            Layer.Type type = Layer.Type.IMAGE;
            boolean isOpen = true;

            // If the section property is set, the layer is either the beginning
            // or the end of a group of layers
            if (sectionProperty != null) {
                LayerSection.Type groupType = ((LayerSection) sectionProperty.data).type;
                switch (groupType) {
                    case OTHER:
                        continue;
                    case GROUP_CLOSED:
                        isOpen = false;
                        // fall through
                    case GROUP_OPENED:
                        type = Layer.Type.GROUP;
                        break;
                    // A bounding layer is a hidden layer in Photoshop that marks
                    // the end of a group (the name is set to </Layer Group>
                    case BOUNDING:
                        Layer.Builder group = stack.pollFirst();
                        Layer.Builder parent = stack.peekFirst();
                        if (parent == null) {
                            image.addLayer(group.build());
                        } else {
                            parent.addLayer(group.build());
                        }
                        continue;
                }
            } else {
                type = getLayerType(rawLayer);
            }

            String name = getLayerName(rawLayer, type, unnamedCounts);

            // Create the actual layer we return to the user
            Layer.Builder layer = new Layer.Builder(name, type);
            layer.bounds(rawLayer.left, rawLayer.top,
                    rawLayer.right - rawLayer.left, rawLayer.bottom - rawLayer.top)
                .opacity(rawLayer.opacity / 255.0f)
                .blendMode(Constants.getBlendMode(rawLayer.blendMode))
                .open(isOpen)
                .visible((rawLayer.flags & RawLayer.INVISIBLE) == 0);

            // Get the current parent before we modify the stack
            Layer.Builder parent = stack.peekFirst();

            // Layer-specific decode steps
            switch (type) {
                case ADJUSTMENT:
                    break;
                case IMAGE:
                    decodeLayerImageData(image, layer, rawLayer, layersList.channels.get(i));
                    break;
                case GROUP:
                    stack.offerFirst(layer);
                    break;
                case PATH:
                    decodePathData(image, layer, properties);
                    break;
                case TEXT:
                    decodeTextData(image, layer, properties.get(LayerProperty.KEY_TEXT));
                    break;
            }

            extractLayerEffects(image, layer, rawLayer);

            // Groups are handled when they close
            if (type != Layer.Type.GROUP) {
                if (parent == null) {
                    image.addLayer(layer.build());
                } else {
                    parent.addLayer(layer.build());
                }
            }
        }
    }

    private static Layer.Type getLayerType(RawLayer rawLayer) {
        Map<String, LayerProperty> properties = rawLayer.extras.properties;
        Layer.Type type = Layer.Type.IMAGE;

        // The type of layer can only be identified by peeking at the various
        // properties set on that layer
        LayerProperty textProperty = properties.get(LayerProperty.KEY_TEXT);
        if (textProperty != null) {
            type = Layer.Type.TEXT;
        } else {
            LayerProperty pathProperty = properties.get(LayerProperty.KEY_VECTOR_MASK);
            if (pathProperty != null) {
                type = Layer.Type.PATH;
            } else {
                for (String key : Constants.getAdjustmentLayerKeys()) {
                    if (properties.containsKey(key)) {
                        type = Layer.Type.ADJUSTMENT;
                        break;
                    }
                }
            }
        }

        return type;
    }

    private static String getLayerName(RawLayer rawLayer, Layer.Type type, int[] unnamedCounts) {
        Map<String, LayerProperty> properties = rawLayer.extras.properties;
        LayerProperty nameProperty = properties.get(LayerProperty.KEY_NAME);

        // The layer's name appears twice in PSD files: first as a legacy
        // Pascal string, then as a Unicode string. We care a lot more about
        // the second one
        String name;
        if (nameProperty != null) {
            name = ((UnicodeString) nameProperty.data).value;
        } else {
            name = rawLayer.extras.name;
        }

        // Generate a name if the layer doesn't have one
        if (name.trim().isEmpty()) {
            name = "<" + getNameForType(type) + " " + unnamedCounts[type.ordinal()]++ + ">";
        }
        return name;
    }

    private static String getNameForType(Layer.Type type) {
        switch (type) {
            case ADJUSTMENT:
                return "Adjustment";
            case IMAGE:
                return "Raster";
            case GROUP:
                return "Group";
            case PATH:
                return "Shape";
            case TEXT:
                return "Text";
        }
        return "Unnamed";
    }

    private enum LayerShadow {
        INNER("IrSh", "innerShadowMulti"),
        OUTER("DrSh", "dropShadowMulti");

        private final String mMultiName;
        private final String mName;

        LayerShadow(String name, String multiName) {
            mName = name;
            mMultiName = multiName;
        }

        String getMultiName() {
            return mMultiName;
        }

        String getName() {
            return mName;
        }
    }

    private static void extractLayerEffects(Image.Builder image, Layer.Builder layer, RawLayer rawLayer) {
        Map<String, LayerProperty> properties = rawLayer.extras.properties;

        boolean isMultiEffects = true;
        LayerProperty property = properties.get(LayerProperty.KEY_MULTI_EFFECTS);
        if (property == null) {
            property = properties.get(LayerProperty.KEY_EFFECTS);
            if (property == null) return;
            isMultiEffects = false;
        }

        LayerEffects layerEffects = (LayerEffects) property.data;
        Effects.Builder effects = new Effects.Builder();

        boolean effectsEnabled = layerEffects.effects.get("masterFXSwitch");
        if (effectsEnabled) {
            extractShadowEffects(image, effects, layerEffects, isMultiEffects, LayerShadow.INNER);
            extractShadowEffects(image, effects, layerEffects, isMultiEffects, LayerShadow.OUTER);
        }

        layer.effects(effects.build());
    }

    private static void extractShadowEffects(Image.Builder image, Effects.Builder effects,
            LayerEffects layerEffects, boolean isMultiEffects, LayerShadow shadowType) {
        if (isMultiEffects) {
            DescriptorItem.ValueList list = layerEffects.effects.get(shadowType.getMultiName());
            if (list != null) {
                for (int i = 0; i < list.count; i++) {
                    Descriptor descriptor = (Descriptor) list.items.get(i).data;
                    addShadowEffect(image, effects, descriptor, shadowType);
                }
            }
        } else {
            Descriptor descriptor = layerEffects.effects.get(shadowType.getName());
            if (descriptor != null) {
                addShadowEffect(image, effects, descriptor, shadowType);
            }
        }
    }

    private static void addShadowEffect(Image.Builder image, Effects.Builder effects,
            Descriptor descriptor, LayerShadow type) {
        float scale = image.verticalResolution() / 72.0f;

        Shadow.Type shadowType = Shadow.Type.INNER;
        if (type == LayerShadow.OUTER) shadowType = Shadow.Type.OUTER;

        Shadow shadow = new Shadow.Builder(shadowType)
                .blur(descriptor.getUnitFloat("blur", scale))
                .angle(descriptor.getUnitFloat("lagl", scale))
                .distance(descriptor.getUnitFloat("Dstn", scale))
                .opacity(descriptor.getUnitFloat("Opct", scale))
                .color(getColor(descriptor))
                .blendMode(Constants.getBlendMode(descriptor.get("Md  ")))
                .build();

        effects.addShadow(shadow);
    }

    private static Color getColor(Descriptor descriptor) {
        Descriptor color = descriptor.get("Clr ");
        if (color == null) return Color.BLACK;

        String colorType = color.classId.toString();

        switch (colorType) {
            case Descriptor.CLASS_ID_COLOR_RGB:
                return colorFromRGB(color);
            case Descriptor.CLASS_ID_COLOR_HSB:
                return colorFromHSB(color);
            case Descriptor.CLASS_ID_COLOR_CMYK:
                return colorFromCMYK(color);
            case Descriptor.CLASS_ID_COLOR_LAB:
                return colorFromLAB(color);
            case Descriptor.CLASS_ID_COLOR_GRAY:
                return colorFromGray(color);
        }

        return Color.BLACK;
    }

    private static Color colorFromRGB(Descriptor color) {
        return new Color(
                color.getFloat("Rd  ") / 255.0f,
                color.getFloat("Grn ") / 255.0f,
                color.getFloat("Bl  ") / 255.0f);
    }

    private static Color colorFromHSB(Descriptor color) {
        return new Color(Color.HSBtoRGB(
                color.getUnitFloat("H   ", 0.0f) / 360.0f,
                color.getFloat("Strt") / 100.0f,
                color.getFloat("Brgh") / 100.0f));
    }

    private static Color colorFromCMYK(Descriptor color) {
        float[] rgb = Colors.linearTosRGB(Colors.CMYKtoRGB(
                color.getFloat("Cyn "),
                color.getFloat("Mgnt"),
                color.getFloat("Ylw "),
                color.getFloat("Blck")));
        return new Color(rgb[0], rgb[1], rgb[2]);
    }

    private static Color colorFromLAB(Descriptor color) {
        float[] rgb = Colors.linearTosRGB(Colors.LABtoRGB(
                color.getFloat("Lmnc"),
                color.getFloat("A   "),
                color.getFloat("B   ")));
        return new Color(rgb[0], rgb[1], rgb[2]);
    }

    private static Color colorFromGray(Descriptor color) {
        float gray = Colors.linearTosRGB(color.getFloat("Gry ") / 255.0f);
        return new Color(gray, gray, gray);
    }

    /**
     * Decodes the text data of a text layer.
     */
    private static void decodeTextData(Image.Builder image, Layer.Builder layer, LayerProperty property) {
        TypeToolObject typeTool = (TypeToolObject) property.data;

        TextInfo.Builder info = new TextInfo.Builder();

        // The text data uses \r to indicate new lines
        // Replace them with \n instead
        info.text(typeTool.text.<String>get(TypeToolObject.KEY_TEXT).replace('\r', '\n'));

        // Compute the text layer's transform
        AffineTransform transform = new AffineTransform(
                typeTool.xx, typeTool.yx,  // scaleX, shearY
                typeTool.xy, typeTool.yy,  // shearX, scaleY
                typeTool.tx, typeTool.ty); // translateX, translateY
        info.transform(transform);

        float resolutionScale = image.verticalResolution() / 72.0f;

        // Retrieves the text's bounding box. The bounding box is required
        // to properly apply alignment properties. The translation found
        // in the affine transform above gives us the origin for text
        // alignment and the bounding box gives us the actual position of
        // the text box from that origin
        DescriptorItem.UnitDouble left = typeTool.text.get("boundingBox.Left");
        DescriptorItem.UnitDouble top = typeTool.text.get("boundingBox.Top ");
        DescriptorItem.UnitDouble right = typeTool.text.get("boundingBox.Rght");
        DescriptorItem.UnitDouble bottom = typeTool.text.get("boundingBox.Btom");

        if (left != null && top != null && right != null && bottom != null) {
            info.bounds(
                    left.toPixel(resolutionScale), top.toPixel(resolutionScale),
                    right.toPixel(resolutionScale), bottom.toPixel(resolutionScale));
        }

        // Retrieves styles from the structured text data
        byte[] data = typeTool.text.get(TypeToolObject.KEY_ENGINE_DATA);

        TextEngine parser = new TextEngine();
        TextEngine.MapProperty properties = parser.parse(data);

        // Find the list of fonts
        List<TextEngine.Property<?>> fontProperties = ((TextEngine.ListProperty)
                properties.get("ResourceDict.FontSet")).getValue();
        List<String> fonts = new ArrayList<>(fontProperties.size());
        fonts.addAll(fontProperties.stream()
                .map(element -> ((TextEngine.MapProperty) element).get("Name").toString())
                .collect(Collectors.toList()));

        // By default, Photoshop creates unstyled runs that rely on the
        // default stylesheet. Look it up.
        int defaultSheetIndex = ((TextEngine.IntProperty) properties.get(
                "ResourceDict.TheNormalStyleSheet")).getValue();
        TextEngine.MapProperty defaultSheet = (TextEngine.MapProperty) properties.get(
                "ResourceDict.StyleSheetSet[" + defaultSheetIndex + "].StyleSheetData");

        // List of style runs
        int pos = 0;
        int[] runs = ((TextEngine.ListProperty) properties.get(
                "EngineDict.StyleRun.RunLengthArray")).toIntArray();
        List<TextEngine.Property<?>> styles = ((TextEngine.ListProperty) properties.get(
                "EngineDict.StyleRun.RunArray")).getValue();

        for (int i = 0; i < runs.length; i++) {
            TextEngine.MapProperty style = (TextEngine.MapProperty) styles.get(i);
            TextEngine.MapProperty sheet = (TextEngine.MapProperty) style.get("StyleSheet.StyleSheetData");

            // Get the typeface, font size and color from each style run
            // If the run does not have a style, fall back to the default stylesheet
            int index = ((TextEngine.IntProperty) getFromMap(sheet, defaultSheet, "Font")).getValue();
            float size = ((TextEngine.FloatProperty)
                    getFromMap(sheet, defaultSheet, "FontSize")).getValue() / resolutionScale;
            float[] rgb = ((TextEngine.ListProperty)
                    getFromMap(sheet, defaultSheet, "FillColor.Values")).toFloatArray();
            int tracking = ((TextEngine.IntProperty) getFromMap(sheet, defaultSheet, "Tracking")).getValue();

            TextInfo.StyleRun run = new TextInfo.StyleRun.Builder(pos, pos += runs[i])
                    .font(fonts.get(index))
                    .fontSize(size)
                    .color(new Color(rgb[1], rgb[2], rgb[3], rgb[0]))
                    .tracking(tracking / 1000.0f)
                    .build();
            info.addStyleRun(run);
        }

        // Thankfully there's always a default paragraph stylesheet
        defaultSheetIndex = ((TextEngine.IntProperty) properties.get(
                "ResourceDict.TheNormalParagraphSheet")).getValue();
        defaultSheet = (TextEngine.MapProperty) properties.get(
                "ResourceDict.ParagraphSheetSet[" + defaultSheetIndex + "].Properties");

        // List of paragraph runs
        pos = 0;
        runs = ((TextEngine.ListProperty) properties.get(
                "EngineDict.ParagraphRun.RunLengthArray")).toIntArray();
        styles = ((TextEngine.ListProperty) properties.get(
                "EngineDict.ParagraphRun.RunArray")).getValue();

        for (int i = 0; i < runs.length; i++) {
            TextEngine.MapProperty style = (TextEngine.MapProperty) styles.get(i);
            TextEngine.MapProperty sheet = (TextEngine.MapProperty) style.get("ParagraphSheet.Properties");

            int justification = ((TextEngine.IntProperty)
                    getFromMap(sheet, defaultSheet, "Justification")).getValue();

            TextInfo.ParagraphRun run = new TextInfo.ParagraphRun.Builder(pos, pos += runs[i])
                    .alignment(TextInfo.ParagraphRun.Alignment.valueOf(alignments[justification]))
                    .build();
            info.addParagraphRun(run);
        }

        layer.textInfo(info.build());
    }

    /**
     * Attempts retrieve a value from "map", then from "defaultMap"
     * if the value is null.
     */
    private static TextEngine.Property<?> getFromMap(TextEngine.MapProperty map,
            TextEngine.MapProperty defaultMap, String name) {
        TextEngine.Property<?> property = map.get(name);
        if (property == null) {
            property = defaultMap.get(name);
        }
        return property;
    }

    /**
     * Simple enum used to track the state of path records.
     * See decodePathData().
     */
    private enum Subpath {
        NONE,
        CLOSED,
        OPEN
    }

    /**
     * Decodes the path data for a given layer. The path data is encoded as a
     * series of path records that are fairly straightforward to interpret.
     */
    private static void decodePathData(Image.Builder image, Layer.Builder layer,
            Map<String, LayerProperty> properties) {
        // We are guaranteed to have this property if this method is called
        LayerProperty property = properties.get(LayerProperty.KEY_VECTOR_MASK);

        // Photoshop only uses the even/odd fill rule
        GeneralPath path = new GeneralPath(Path2D.WIND_EVEN_ODD);

        // Each Bézier knot in a PSD is made of three points:
        //   - the anchor (the know or point itself)
        //   - the control point before the anchor
        //   - the control point after the anchor
        //
        // PSD Bézier knots must be converted to moveTo/curveTo commands.
        // A curveTo() describes a cubic curve. To generate a curveTo() we
        // need three points:
        //   - the next anchor (the destination point of the curveTo())
        //   - the control point after the previous anchor
        //   - the control point before the next anchor

        Subpath currentSubpath = Subpath.NONE;
        PathRecord.BezierKnot firstKnot = null;
        PathRecord.BezierKnot lastKnot = null;

        VectorMask mask = (VectorMask) property.data;
        for (PathRecord record : mask.pathRecords) {
            switch (record.selector) {
                // A "LENGTH" record marks the beginning of a new sub-path
                // Closed subpath needs special handling at the end
                case PathRecord.CLOSED_SUBPATH_LENGTH:
                    if (currentSubpath == Subpath.CLOSED) {
                        // If the previous subpath is of the closed type, close it now
                        addToPath(path, firstKnot, lastKnot);
                    }
                    currentSubpath = Subpath.CLOSED;
                    firstKnot = lastKnot = null;
                    break;
                // New subpath
                case PathRecord.OPEN_SUBPATH_LENGTH:
                    if (currentSubpath == Subpath.CLOSED) {
                        // Close the previous subpath if needed
                        addToPath(path, firstKnot, lastKnot);
                    }
                    currentSubpath = Subpath.OPEN;
                    firstKnot = lastKnot = null;
                    break;
                // Open and closed subpath knots can be handled the same way
                // The linked/unlinked characteristic only matters to interactive
                // editors and we happily throw away that information
                case PathRecord.CLOSED_SUBPATH_KNOT_LINKED:
                case PathRecord.CLOSED_SUBPATH_KNOT_UNLINKED:
                case PathRecord.OPEN_SUBPATH_KNOT_LINKED:
                case PathRecord.OPEN_SUBPATH_KNOT_UNLINKED:
                    PathRecord.BezierKnot knot = (PathRecord.BezierKnot) record.data;
                    if (lastKnot == null) {
                        // If we just started a subpath we need to insert a moveTo()
                        // using the new anchor
                        path.moveTo(
                          Bytes.fixed8_24ToFloat(knot.anchorX),
                          Bytes.fixed8_24ToFloat(knot.anchorY));
                        firstKnot = knot;
                    } else {
                        // Otherwise let's curve to the new anchor
                        addToPath(path, knot, lastKnot);
                    }
                    lastKnot = knot;
                    break;
            }
        }

        // Close the subpath if needed
        if (currentSubpath == Subpath.CLOSED) {
            addToPath(path, firstKnot, lastKnot);
        }

        // Vector data is stored in relative coordinates in PSD files
        // For instance, a point at 0.5,0.5 is in the center of the document
        // We apply a transform to convert these relative coordinates into
        // absolute pixel coordinates
        Rectangle2D bounds = layer.bounds();

        AffineTransform transform = new AffineTransform();
        transform.translate(-bounds.getX(), -bounds.getY());
        transform.scale(image.width(), image.height());

        path.transform(transform);
        layer.path(path);

        if (properties.containsKey(LayerProperty.KEY_ADJUSTMENT_SOLID_COLOR)) {
            SolidColorAdjustment adjustment = (SolidColorAdjustment)
                    properties.get(LayerProperty.KEY_ADJUSTMENT_SOLID_COLOR).data;
            layer.pathColor(getColor(adjustment.solidColor));
        }
    }

    private static void addToPath(GeneralPath path, PathRecord.BezierKnot firstKnot,
            PathRecord.BezierKnot lastKnot) {

        path.curveTo(
                Bytes.fixed8_24ToFloat(lastKnot.controlExitX),
                Bytes.fixed8_24ToFloat(lastKnot.controlExitY),
                Bytes.fixed8_24ToFloat(firstKnot.controlEnterX),
                Bytes.fixed8_24ToFloat(firstKnot.controlEnterY),
                Bytes.fixed8_24ToFloat(firstKnot.anchorX),
                Bytes.fixed8_24ToFloat(firstKnot.anchorY));
    }

    /**
     * Decodes the image data of a specific layer. The image data is encoded
     * separately for each channel. Each channel could theoretically have its
     * own encoding so let's pretend this can happen.
     * There are 4 encoding formats: RAW, RLE, ZIP and ZIP without prediction.
     * Since we have yet to encounter the ZIP case, we only support RAW and RLE.
     */
    private static void decodeLayerImageData(Image.Builder image, Layer.Builder layer,
            RawLayer rawLayer, ChannelsContainer channelsList) {

        Rectangle2D bounds = layer.bounds();
        if (bounds.isEmpty()) return;

        int channels = rawLayer.channels;
        switch (image.colorMode()) {
            case BITMAP:
            case INDEXED:
                // Bitmap and indexed color modes do not support layers
                break;
            case GRAYSCALE:
                channels = Math.min(channels, 2);
                break;
            case RGB:
                channels = Math.min(channels, 4);
                break;
            case CMYK:
                channels = Math.min(channels, 5);
                break;
            case UNKNOWN:
            case NONE:
            case MULTI_CHANNEL:
                // Unsupported
                break;
            case DUOTONE:
                channels = Math.min(channels, 2);
                break;
            case LAB:
                channels = Math.min(channels, 4);
                break;
        }

        ColorSpace colorSpace = image.colorSpace();
        BufferedImage bitmap = Images.create((int) bounds.getWidth(), (int) bounds.getHeight(),
                image.colorMode(), channels, colorSpace, image.depth());

        for (int i = 0; i < rawLayer.channelsInfo.size(); i++) {
            ChannelInformation info = rawLayer.channelsInfo.get(i);
            if (info.id < -1) continue; // skip mask channel

            ChannelImageData imageData = channelsList.imageData.get(i);
            switch (imageData.compression) {
                case RAW:
                    // TODO: don't assume 8 bit depth
                    Images.decodeChannelRaw(imageData.data, 0, info.id, bitmap, 8);
                    break;
                case RLE:
                    int offset = (int) (bounds.getHeight() * 2);
                    Images.decodeChannelRLE(imageData.data, offset, info.id, bitmap);
                    break;
                case ZIP:
                case ZIP_NO_PREDICTION:
                    break;
            }
        }

        layer.image(fixBitmap(image, bitmap));
    }

    private static void extractHeaderData(Image.Builder image, Header header) {
        image
            .dimensions(header.width, header.height)
            .colorMode(header.colorMode)
            .depth(header.depth);
    }

    /**
     * Image resource blocks contain a lot of information in PSD files but not
     * all of it is interesting to us. Here we only look for the few blocks that
     * we actually care about.
     */
    private static void resolveBlocks(Image.Builder image, ImageResources resources) {
        extractGuides(image, resources.get(GuidesResourceBlock.ID));
        extractThumbnail(image, resources.get(ThumbnailResourceBlock.ID));
        extractResolution(image, resources.get(ResolutionInfoBlock.ID));
        extractColorProfile(image, resources.get(ColorProfileBlock.ID));
    }

    /**
     * Extracts the ICC color profile embedded in the file, if any.
     */
    private static void extractColorProfile(Image.Builder image, ColorProfileBlock colorProfileBlock) {
        if (colorProfileBlock == null) return;

        ICC_Profile iccProfile = ICC_Profile.getInstance(colorProfileBlock.icc);
        image.colorSpace(new ICC_ColorSpace(iccProfile));
    }

    /**
     * Extracts the horizontal and vertical dpi information from the PSD
     * file. This information is important to properly handle the point
     * unit used in text layers.
     */
    private static void extractResolution(Image.Builder image, ResolutionInfoBlock resolutionBlock) {
        if (resolutionBlock == null) return;

        float hRes = Bytes.fixed16_16ToFloat(resolutionBlock.horizontalResolution);
        ResolutionUnit unit = resolutionBlock.horizontalUnit;
        if (unit == ResolutionUnit.UNKNOWN) unit = ResolutionUnit.PIXEL_PER_INCH;
        if (unit == ResolutionUnit.PIXEL_PER_CM) {
            hRes *= CENTIMETER_TO_INCH;
        }

        float vRes = Bytes.fixed16_16ToFloat(resolutionBlock.verticalResolution);
        unit = resolutionBlock.verticalUnit;
        if (unit == ResolutionUnit.UNKNOWN) unit = ResolutionUnit.PIXEL_PER_INCH;
        if (unit == ResolutionUnit.PIXEL_PER_CM) {
            vRes *= CENTIMETER_TO_INCH;
        }

        image.resolution(hRes, vRes);
    }

    private static void extractThumbnail(Image.Builder image, ThumbnailResourceBlock thumbnailBlock) {
        if (thumbnailBlock == null) return;

        try {
            image.thumbnail(ImageIO.read(new ByteArrayInputStream(thumbnailBlock.thumbnail)));
        } catch (IOException e) {
            // Ignore
        }
    }

    private static void extractGuides(Image.Builder image, GuidesResourceBlock guidesBlock) {
        if (guidesBlock == null) return;

        // Guides are stored in a 27.5 fixed-point format, kind of weird
        for (GuideBlock block : guidesBlock.guides) {
            Guide guide = new Guide.Builder()
                    .orientation(Guide.Orientation.values()[block.orientation.ordinal()])
                    .position(block.location / 32.0f)
                    .build();
            image.addGuide(guide);
        }
    }

    /**
     * Decodes the flattened image data. Just like layers, the data is stored as
     * separate planes for each channel. The difference is that the encoding is
     * the same for all channels. The ZIP formats are not supported.
     */
    private static void decodeImageData(Image.Builder image, PsdFile psd) {
        int channels = psd.header.channels;
        int alphaChannel = 0;
        // When the layer count is negative, the first alpha channel is the
        // merged result's alpha mask
        if (psd.layersInfo.layers.count < 0) {
            alphaChannel = 1;
        }

        switch (image.colorMode()) {
            case BITMAP:
            case INDEXED:
                decodeIndexedImageData(image, psd);
                return;
            case GRAYSCALE:
                channels = Math.min(channels, 1 + alphaChannel);
                break;
            case RGB:
                channels = Math.min(channels, 3 + alphaChannel);
                break;
            case CMYK:
                channels = Math.min(channels, 4 + alphaChannel);
                break;
            case UNKNOWN:
            case NONE:
            case MULTI_CHANNEL:
                // Unsupported
                break;
            case DUOTONE:
                channels = Math.min(channels, 1 + alphaChannel);
                break;
            case LAB:
                channels = Math.min(channels, 3 + alphaChannel);
                break;
        }

        ColorSpace colorSpace = image.colorSpace();

        BufferedImage bitmap = null;
        switch (psd.imageData.compression) {
            case RAW:
                bitmap = Images.decodeRaw(psd.imageData.data, 0, image.width(), image.height(),
                        image.colorMode(), channels, colorSpace, psd.header.depth);
                break;
            case RLE:
                int offset = image.height() * psd.header.channels * 2;
                bitmap = Images.decodeRLE(psd.imageData.data, offset, image.width(), image.height(),
                        image.colorMode(), channels, colorSpace, psd.header.depth);
                break;
            case ZIP:
            case ZIP_NO_PREDICTION:
                break;
        }

        image.mergedImage(fixBitmap(image, bitmap));
    }

    private static void decodeIndexedImageData(Image.Builder image, PsdFile psd) {
        ColorSpace colorSpace = image.colorSpace();

        UnsignedShortBlock block;
        block = psd.resources.get(UnsignedShortBlock.ID_INDEX_TABLE_COUNT);
        int size = block == null ? 256 : block.data;

        block = psd.resources.get(UnsignedShortBlock.ID_INDEX_TRANSPARENCY);
        int transparency = block == null ? -1 : block.data;

        BufferedImage bitmap = null;
        switch (psd.imageData.compression) {
            case RAW:
                bitmap = Images.decodeIndexedRaw(psd.imageData.data, 0, image.width(), image.height(),
                        image.colorMode(), colorSpace, size, psd.colorData.data, transparency);
                break;
            case RLE:
                int offset = image.height() * psd.header.channels * 2;
                bitmap = Images.decodeIndexedRLE(psd.imageData.data, offset, image.width(), image.height(),
                        image.colorMode(), colorSpace, size, psd.colorData.data, transparency);
                break;
            case ZIP:
            case ZIP_NO_PREDICTION:
                break;
        }

        image.mergedImage(bitmap);
    }

    private static BufferedImage fixBitmap(Image.Builder image, BufferedImage bitmap) {
        // Fun fact: CMYK colors are stored reversed...
        // Cyan 100% is stored as 0x0 and Cyan 0% is stored as 0xff
        if (image.colorMode() == ColorMode.CMYK) {
            bitmap = Images.invert(bitmap);
        }
        return bitmap;
    }
}
