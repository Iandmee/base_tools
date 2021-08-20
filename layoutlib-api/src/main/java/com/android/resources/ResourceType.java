/*
 * Copyright (C) 2007 The Android Open Source Project
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
package com.android.resources;

import static com.google.common.base.MoreObjects.firstNonNull;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.ide.common.rendering.api.AndroidConstants;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import java.util.Arrays;
import java.util.Collection;
import java.util.function.BiFunction;
import java.util.function.Function;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/**
 * Enum representing a type of compiled resource.
 *
 * <p>See {@code ResourceType} in aapt2/Resource.h.
 */
public enum ResourceType {
    ANIM("anim", "Animation"),
    ANIMATOR("animator", "Animator"),
    ARRAY("array", "Array", "string-array", "integer-array"),
    ATTR("attr", "Attr"),
    BOOL("bool", "Boolean"),
    COLOR("color", "Color"),
    DIMEN("dimen", "Dimension"),
    DRAWABLE("drawable", "Drawable"),
    FONT("font", "Font"),
    FRACTION("fraction", "Fraction"),
    ID("id", "ID"),
    INTEGER("integer", "Integer"),
    INTERPOLATOR("interpolator", "Interpolator"),
    LAYOUT("layout", "Layout"),
    MENU("menu", "Menu"),
    MIPMAP("mipmap", "Mip Map"),
    NAVIGATION("navigation", "Navigation"),
    PLURALS("plurals", "Plurals"),
    RAW("raw", "Raw"),
    STRING("string", "String"),
    STYLE("style", "Style"),
    STYLEABLE("styleable", "Styleable", Kind.STYLEABLE),
    TRANSITION("transition", "Transition"),
    XML("xml", "XML"),

    /**
     * This is not actually used. Only there because they get parsed and since we want to detect new
     * resource type, we need to have this one exist.
     */
    PUBLIC("public", "Public visibility modifier", Kind.SYNTHETIC),

    /**
     * This type is used for elements dynamically generated by the parsing of aapt:attr nodes. The
     * "aapt:attr" allow to inline resources as part of a different resource, for example, a
     * drawable as part of a layout. When the parser, encounters one of this nodes, it will generate
     * a synthetic _aapt attr reference.
     */
    AAPT("_aapt", "Aapt Attribute", Kind.SYNTHETIC),

    /**
     * This tag is used for marking a resource overlayable, i.e. that it can be overlaid at runtime
     * by RROs (Runtime Resource Overlays). This is a new feature supported starting Android 10.
     * This tag (and the content following it in that node) does not define a resource.
     */
    OVERLAYABLE("overlayable", "Overlayable tag", Kind.SYNTHETIC),

    /** Represents item tags inside a style definition. */
    STYLE_ITEM("item", "Style Item", Kind.SYNTHETIC),

    /**
     * Not an actual resource type from AAPT. Used to provide sample data values in the tools
     * namespace
     */
    SAMPLE_DATA("sample", "Sample data", Kind.SYNTHETIC),

    /**
     * Not a real resource, but a way of defining a resource reference that will be replaced with
     * its actual value during linking. Does not exist at runtime, nor does it appear in the R
     * class. Only present in raw and flat resources.
     */
    MACRO("macro", "Macro resource replacement", Kind.SYNTHETIC),
    ;

    private enum Kind {
        /** These types are used both in the R and as XML tag names. */
        REAL,

        /**
         * Styleables are handled by aapt but don't end up in the resource table. They have an R
         * inner class (called {@code styleable}), are declared in XML (using {@code
         * declare-styleable}) but cannot be referenced from XML.
         */
        STYLEABLE,

        /**
         * Other types that are not known to aapt, but are used by tools to represent some
         * information in the resources system.
         */
        SYNTHETIC,
        ;
    }

    @NonNull private final String mName;
    @NonNull private final Kind mKind;
    @NonNull private final String mDisplayName;
    @NonNull private final String[] mAlternateXmlNames;

    ResourceType(
            @NonNull String name,
            @NonNull String displayName,
            @NonNull String... alternateXmlNames) {
        mName = name;
        mKind = Kind.REAL;
        mDisplayName = displayName;
        mAlternateXmlNames = alternateXmlNames;
    }

    ResourceType(@NonNull String name, @NonNull String displayName, @NonNull Kind kind) {
        mName = name;
        mKind = kind;
        mDisplayName = displayName;
        mAlternateXmlNames = new String[0];
    }

    /** The set of all types of resources that can be referenced by other resources. */
    public static final ImmutableSet<ResourceType> REFERENCEABLE_TYPES;

    private static final ImmutableMap<String, ResourceType> TAG_NAMES;
    private static final ImmutableMap<String, ResourceType> CLASS_NAMES;

    static {
        ImmutableMap.Builder<String, ResourceType> tagNames = ImmutableMap.builder();
        tagNames.put(AndroidConstants.TAG_DECLARE_STYLEABLE, STYLEABLE);
        tagNames.put(AndroidConstants.TAG_PUBLIC, PUBLIC);
        tagNames.put(OVERLAYABLE.getName(), OVERLAYABLE);
        tagNames.put(MACRO.getName(), MACRO);

        ImmutableMap.Builder<String, ResourceType> classNames = ImmutableMap.builder();
        classNames.put(STYLEABLE.mName, STYLEABLE);

        for (ResourceType type : ResourceType.values()) {
            if (type.mKind != Kind.REAL || type == STYLEABLE) {
                continue;
            }
            classNames.put(type.getName(), type);
            tagNames.put(type.getName(), type);
            for (String alternateName : type.mAlternateXmlNames) {
                tagNames.put(alternateName, type);
            }
        }

        TAG_NAMES = tagNames.build();
        CLASS_NAMES = classNames.build();
        REFERENCEABLE_TYPES =
                Arrays.stream(values())
                        .filter(ResourceType::getCanBeReferenced)
                        .collect(Sets.toImmutableEnumSet());
    }

    /**
     * Returns the resource type name, as used by XML files.
     */
    @NonNull
    public String getName() {
        return mName;
    }

    /**
     * Returns a translated display name for the resource type.
     */
    @NonNull
    public String getDisplayName() {
        return mDisplayName;
    }

    /**
     * Returns the enum by its name as it appears in the R class.
     *
     * @param className name of the inner class of the R class, e.g. "string" or "styleable".
     */
    @Nullable
    public static ResourceType fromClassName(@NonNull String className) {
        return CLASS_NAMES.get(className);
    }

    /**
     * Returns the enum by its name as it appears as a folder name under {@code res/}.
     *
     * @param folderName name of the inner class of the R class, e.g. "drawable" or "color".
     */
    @Nullable
    public static ResourceType fromFolderName(@NonNull String folderName) {
        return CLASS_NAMES.get(folderName);
    }

    /**
     * Returns the enum by its name as it appears in XML as a tag name.
     *
     * @param tagName name of the XML tag, e.g. "string" or "declare-styleable".
     */
    @Nullable
    public static ResourceType fromXmlTagName(@NonNull String tagName) {
        return TAG_NAMES.get(tagName);
    }

    /**
     * Returns the enum by its name as it appears in a {@link ResourceUrl} string.
     *
     * @param xmlValue value of the type attribute or the prefix of a {@link ResourceUrl}, e.g.
     *     "string" or "array".
     */
    @Nullable
    public static ResourceType fromXmlValue(@NonNull String xmlValue) {
        if (xmlValue.equals(AndroidConstants.TAG_DECLARE_STYLEABLE)
                || xmlValue.equals(STYLEABLE.mName)) {
            return null;
        }

        if (xmlValue.equals(SAMPLE_DATA.mName)) {
            return SAMPLE_DATA;
        }

        if (xmlValue.equals(AAPT.mName)) {
            return AAPT;
        }

        if (xmlValue.equals(OVERLAYABLE.mName)) {
            return OVERLAYABLE;
        }

        return CLASS_NAMES.get(xmlValue);
    }

    @Nullable
    public static <T> ResourceType fromXmlTag(
            @NonNull T tag,
            @NonNull Function<T, String> nameFunction,
            @NonNull BiFunction<? super T, ? super String, String> attributeFunction) {
        String tagName = nameFunction.apply(tag);
        switch (tagName) {
            case AndroidConstants.TAG_EAT_COMMENT:
                return null;
            case AndroidConstants.TAG_ITEM:
                String typeAttribute = attributeFunction.apply(tag, AndroidConstants.ATTR_TYPE);
                if (!Strings.isNullOrEmpty(typeAttribute)) {
                    return fromClassName(typeAttribute);
                } else {
                    return null;
                }
            default:
                return fromXmlTagName(tagName);
        }
    }

    @Nullable
    public static ResourceType fromXmlTag(@NonNull Node domNode) {
        if (!(domNode instanceof Element)) {
            return null;
        }

        Element tag = (Element) domNode;
        return fromXmlTag(
                tag,
                element -> firstNonNull(element.getLocalName(), element.getTagName()),
                Element::getAttribute);
    }

    public static Collection<String> getClassNames() {
        return CLASS_NAMES.keySet();
    }

    /**
     * Returns true if the generated R class contains an inner class for this {@link ResourceType}.
     */
    public boolean getHasInnerClass() {
        return mKind != Kind.SYNTHETIC;
    }

    /**
     * Returns true if this {@link ResourceType} can be referenced using the {@link ResourceUrl}
     * syntax: {@code @typeName/resourceName}.
     */
    public boolean getCanBeReferenced() {
        return mKind == Kind.REAL && this != ATTR;
    }

    /**
     * Returns true if this type is a synthetic type, such as {@link #PUBLIC}
     */
    public boolean isSynthetic() {
        return mKind == Kind.SYNTHETIC;
    }

    @Override
    @NonNull
    public String toString() {
        // Unfortunately we still have code that relies on toString() returning the aapt name.
        return getName();
    }
}
