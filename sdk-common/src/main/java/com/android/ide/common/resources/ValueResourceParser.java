/*
 * Copyright (C) 2008 The Android Open Source Project
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

package com.android.ide.common.resources;

import com.android.ide.common.rendering.api.AttrResourceValue;
import com.android.ide.common.rendering.api.DeclareStyleableResourceValue;
import com.android.ide.common.rendering.api.ItemResourceValue;
import com.android.ide.common.rendering.api.ResourceValue;
import com.android.ide.common.rendering.api.StyleResourceValue;
import com.android.ide.common.res2.ValueXmlHelper;
import com.android.resources.ResourceType;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/**
 * SAX handler to parse value resource files.
 */
public final class ValueResourceParser extends DefaultHandler {

    // TODO: reuse definitions from somewhere else.
    private static final String NODE_RESOURCES = "resources";
    private static final String NODE_ITEM = "item";
    private static final String ATTR_NAME = "name";
    private static final String ATTR_TYPE = "type";
    private static final String ATTR_PARENT = "parent";
    private static final String ATTR_VALUE = "value";

    private static final String DEFAULT_NS_PREFIX = "android:";
    private static final int DEFAULT_NS_PREFIX_LEN = DEFAULT_NS_PREFIX.length();

    public interface IValueResourceRepository {
        void addResourceValue(ResourceValue value);
        boolean hasResourceValue(ResourceType type, String name);
    }

    private boolean inResources = false;
    private int mDepth = 0;
    private ResourceValue mCurrentValue = null;
    private StyleResourceValue mCurrentStyle = null;
    private DeclareStyleableResourceValue mCurrentDeclareStyleable = null;
    private AttrResourceValue mCurrentAttr;
    private IValueResourceRepository mRepository;
    private final boolean mIsFramework;

    public ValueResourceParser(IValueResourceRepository repository, boolean isFramework) {
        mRepository = repository;
        mIsFramework = isFramework;
    }

    @Override
    public void endElement(String uri, String localName, String qName) throws SAXException {
        if (mCurrentValue != null) {
            String value = mCurrentValue.getValue();
            value = value == null ? "" : ValueXmlHelper.unescapeResourceString(value, false, true);
            mCurrentValue.setValue(value);
        }

        if (inResources && qName.equals(NODE_RESOURCES)) {
            inResources = false;
        } else if (mDepth == 2) {
            mCurrentValue = null;
            mCurrentStyle = null;
            mCurrentDeclareStyleable = null;
            mCurrentAttr = null;
        } else if (mDepth == 3) {
            mCurrentValue = null;
            if (mCurrentDeclareStyleable != null) {
                mCurrentAttr = null;
            }
        }

        mDepth--;
        super.endElement(uri, localName, qName);
    }

    @Override
    public void startElement(String uri, String localName, String qName, Attributes attributes)
            throws SAXException {
        try {
            mDepth++;
            if (inResources == false && mDepth == 1) {
                if (qName.equals(NODE_RESOURCES)) {
                    inResources = true;
                }
            } else if (mDepth == 2 && inResources == true) {
                ResourceType type = getType(qName, attributes);

                if (type != null) {
                    // get the resource name
                    String name = attributes.getValue(ATTR_NAME);
                    if (name != null) {
                        switch (type) {
                            case STYLE:
                                String parent = attributes.getValue(ATTR_PARENT);
                                mCurrentStyle = new StyleResourceValue(type, name, parent,
                                        mIsFramework);
                                mRepository.addResourceValue(mCurrentStyle);
                                break;
                            case DECLARE_STYLEABLE:
                                mCurrentDeclareStyleable = new DeclareStyleableResourceValue(
                                        type, name, mIsFramework);
                                mRepository.addResourceValue(mCurrentDeclareStyleable);
                                break;
                            case ATTR:
                                mCurrentAttr = new AttrResourceValue(type, name, mIsFramework);
                                mRepository.addResourceValue(mCurrentAttr);
                                break;
                            default:
                                mCurrentValue = new ResourceValue(type, name, mIsFramework);
                                mRepository.addResourceValue(mCurrentValue);
                                break;
                        }
                    }
                }
            } else if (mDepth == 3) {
                // get the resource name
                String name = attributes.getValue(ATTR_NAME);
                if (name != null) {

                    if (mCurrentStyle != null) {
                        // is the attribute in the android namespace?
                        boolean isFrameworkAttr = mIsFramework;
                        if (name.startsWith(DEFAULT_NS_PREFIX)) {
                            name = name.substring(DEFAULT_NS_PREFIX_LEN);
                            isFrameworkAttr = true;
                        }

                        mCurrentValue = new ItemResourceValue(name, isFrameworkAttr, mIsFramework);
                        mCurrentStyle.addItem((ItemResourceValue)mCurrentValue);
                    } else if (mCurrentDeclareStyleable != null) {
                        // is the attribute in the android namespace?
                        boolean isFramework = mIsFramework;
                        if (name.startsWith(DEFAULT_NS_PREFIX)) {
                            name = name.substring(DEFAULT_NS_PREFIX_LEN);
                            isFramework = true;
                        }

                        mCurrentAttr = new AttrResourceValue(ResourceType.ATTR, name, isFramework);
                        mCurrentDeclareStyleable.addValue(mCurrentAttr);

                        // also add it to the repository.
                        mRepository.addResourceValue(mCurrentAttr);

                    } else if (mCurrentAttr != null) {
                        // get the enum/flag value
                        String value = attributes.getValue(ATTR_VALUE);

                        try {
                            // Integer.decode/parseInt can't deal with hex value > 0x7FFFFFFF so we
                            // use Long.decode instead.
                            mCurrentAttr.addValue(name, (int)(long)Long.decode(value));
                        } catch (NumberFormatException e) {
                            // pass, we'll just ignore this value
                        }

                    }
                }
            } else if (mDepth == 4 && mCurrentAttr != null) {
                // get the enum/flag name
                String name = attributes.getValue(ATTR_NAME);
                String value = attributes.getValue(ATTR_VALUE);

                try {
                    // Integer.decode/parseInt can't deal with hex value > 0x7FFFFFFF so we
                    // use Long.decode instead.
                    mCurrentAttr.addValue(name, (int)(long)Long.decode(value));
                } catch (NumberFormatException e) {
                    // pass, we'll just ignore this value
                }
            }
        } finally {
            super.startElement(uri, localName, qName, attributes);
        }
    }

    private ResourceType getType(String qName, Attributes attributes) {
        String typeValue;

        // if the node is <item>, we get the type from the attribute "type"
        if (NODE_ITEM.equals(qName)) {
            typeValue = attributes.getValue(ATTR_TYPE);
        } else {
            // the type is the name of the node.
            typeValue = qName;
        }

        ResourceType type = ResourceType.getEnum(typeValue);
        return type;
    }


    @Override
    public void characters(char[] ch, int start, int length) throws SAXException {
        if (mCurrentValue != null) {
            String value = mCurrentValue.getValue();
            if (value == null) {
                mCurrentValue.setValue(new String(ch, start, length));
            } else {
                mCurrentValue.setValue(value + new String(ch, start, length));
            }
        }
    }


}
