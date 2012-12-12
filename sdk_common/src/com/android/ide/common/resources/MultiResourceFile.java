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

package com.android.ide.common.resources;

import com.android.ide.common.rendering.api.ResourceValue;
import com.android.ide.common.resources.ValueResourceParser.IValueResourceRepository;
import com.android.io.IAbstractFile;
import com.android.io.StreamException;
import com.android.resources.ResourceType;

import org.xml.sax.SAXException;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

/**
 * Represents a resource file able to declare multiple resources, which could be of
 * different {@link ResourceType}.
 * <p/>
 * This is typically an XML file inside res/values.
 */
public final class MultiResourceFile extends ResourceFile implements IValueResourceRepository {

    private final static SAXParserFactory sParserFactory = SAXParserFactory.newInstance();

    private final Map<ResourceType, Map<String, ResourceValue>> mResourceItems =
        new EnumMap<ResourceType, Map<String, ResourceValue>>(ResourceType.class);

    private Collection<ResourceType> mResourceTypeList = null;

    public MultiResourceFile(IAbstractFile file, ResourceFolder folder) {
        super(file, folder);
    }

    // Boolean flag to track whether a named element has been added or removed, thus requiring
    // a new ID table to be generated
    private boolean mNeedIdRefresh;

    @Override
    protected void load(ScanningContext context) {
        // need to parse the file and find the content.
        parseFile();

        // create new ResourceItems for the new content.
        mResourceTypeList = Collections.unmodifiableCollection(mResourceItems.keySet());

        // We need an ID generation step
        mNeedIdRefresh = true;

        // create/update the resource items.
        updateResourceItems(context);
    }

    @Override
    protected void update(ScanningContext context) {
        // Reset the ID generation flag
        mNeedIdRefresh = false;

        // Copy the previous version of our list of ResourceItems and types
        Map<ResourceType, Map<String, ResourceValue>> oldResourceItems
                        = new EnumMap<ResourceType, Map<String, ResourceValue>>(mResourceItems);

        // reset current content.
        mResourceItems.clear();

        // need to parse the file and find the content.
        parseFile();

        // create new ResourceItems for the new content.
        mResourceTypeList = Collections.unmodifiableCollection(mResourceItems.keySet());

        // Check to see if any names have changed. If so, mark the flag so updateResourceItems
        // can notify the ResourceRepository that an ID refresh is needed
        if (oldResourceItems.keySet().equals(mResourceItems.keySet())) {
            for (ResourceType type : mResourceTypeList) {
                // We just need to check the names of the items.
                // If there are new or removed names then we'll have to regenerate IDs
                if (mResourceItems.get(type).keySet()
                                          .equals(oldResourceItems.get(type).keySet()) == false) {
                    mNeedIdRefresh = true;
                }
            }
        } else {
            // If our type list is different, obviously the names will be different
            mNeedIdRefresh = true;
        }
        // create/update the resource items.
        updateResourceItems(context);
    }

    @Override
    protected void dispose(ScanningContext context) {
        ResourceRepository repository = getRepository();

        // only remove this file from all existing ResourceItem.
        repository.removeFile(mResourceTypeList, this);

        // We'll need an ID refresh because we deleted items
        context.requestFullAapt();

        // don't need to touch the content, it'll get reclaimed as this objects disappear.
        // In the mean time other objects may need to access it.
    }

    @Override
    public Collection<ResourceType> getResourceTypes() {
        return mResourceTypeList;
    }

    @Override
    public boolean hasResources(ResourceType type) {
        Map<String, ResourceValue> list = mResourceItems.get(type);
        return (list != null && list.size() > 0);
    }

    private void updateResourceItems(ScanningContext context) {
        ResourceRepository repository = getRepository();

        // remove this file from all existing ResourceItem.
        repository.removeFile(mResourceTypeList, this);

        for (ResourceType type : mResourceTypeList) {
            Map<String, ResourceValue> list = mResourceItems.get(type);

            if (list != null) {
                Collection<ResourceValue> values = list.values();
                for (ResourceValue res : values) {
                    ResourceItem item = repository.getResourceItem(type, res.getName());

                    // add this file to the list of files generating this resource item.
                    item.add(this);
                }
            }
        }

        // If we need an ID refresh, ask the repository for that now
        if (mNeedIdRefresh) {
            context.requestFullAapt();
        }
    }

    /**
     * Parses the file and creates a list of {@link ResourceType}.
     */
    private void parseFile() {
        try {
            SAXParser parser = sParserFactory.newSAXParser();
            parser.parse(getFile().getContents(), new ValueResourceParser(this, isFramework()));
        } catch (ParserConfigurationException e) {
        } catch (SAXException e) {
        } catch (IOException e) {
        } catch (StreamException e) {
        }
    }

    /**
     * Adds a resource item to the list
     * @param value The value of the resource.
     */
    @Override
    public void addResourceValue(ResourceValue value) {
        ResourceType resType = value.getResourceType();

        Map<String, ResourceValue> list = mResourceItems.get(resType);

        // if the list does not exist, create it.
        if (list == null) {
            list = new HashMap<String, ResourceValue>();
            mResourceItems.put(resType, list);
        } else {
            // look for a possible value already existing.
            ResourceValue oldValue = list.get(value.getName());

            if (oldValue != null) {
                oldValue.replaceWith(value);
                return;
            }
        }

        // empty list or no match found? add the given resource
        list.put(value.getName(), value);
    }

    @Override
    public boolean hasResourceValue(ResourceType type, String name) {
        Map<String, ResourceValue> map = mResourceItems.get(type);
        return map != null && map.containsKey(name);
    }

    @Override
    public ResourceValue getValue(ResourceType type, String name) {
        // get the list for the given type
        Map<String, ResourceValue> list = mResourceItems.get(type);

        if (list != null) {
            return list.get(name);
        }

        return null;
    }
}
