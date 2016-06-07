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

package com.android.tools.fd.client;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.utils.XmlUtils;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.File;
import java.util.List;
import java.util.ListIterator;

/**
 * {@link InstantRunBuildInfo} models the build-info.xml file that is generated by an instant-run
 * aware Gradle build.
 */
@SuppressWarnings({"WeakerAccess", "unused"}) // Used in studio and in integration tests.
public class InstantRunBuildInfo {
    /**
     * Right now Gradle plugin doesn't sort the build id's, but when it does we can rely on element
     * order in the doc
     */
    private static final boolean BUILDS_ARE_SORTED = false;

    private static final String ATTR_TIMESTAMP = "timestamp";

    public static final String ATTR_API_LEVEL = "api-level";

    private static final String ATTR_FORMAT = "format";

    private static final String ATTR_VERIFIER_STATUS = "verifier";

    // Note: The verifier status can be any number of values (See InstantRunVerifierStatus enum in gradle).
    // Currently, the only contract between gradle and the IDE is that the value is set to COMPATIBLE if the build can be hotswapped
    public static final String VALUE_VERIFIER_STATUS_COMPATIBLE = "COMPATIBLE";

    private static final String TAG_ARTIFACT = "artifact";

    private static final String TAG_BUILD = "build";

    private static final String ATTR_ARTIFACT_LOCATION = "location";

    private static final String ATTR_ARTIFACT_TYPE = "type";

    private static final String ATTR_TOKEN = "token";

    @NonNull
    private final Element mRoot;

    @Nullable
    private List<InstantRunArtifact> mArtifacts;

    public InstantRunBuildInfo(@NonNull Element root) {
        mRoot = root;
    }

    @NonNull
    public String getTimeStamp() {
        return mRoot.getAttribute(ATTR_TIMESTAMP);
    }

    public long getSecretToken() {
        String tokenString = mRoot.getAttribute(ATTR_TOKEN);
        assert !Strings.isNullOrEmpty(tokenString) : "Application authorization token was not generated";
        return Long.parseLong(tokenString);
    }

    @NonNull
    public String getVerifierStatus() {
        return mRoot.getAttribute(ATTR_VERIFIER_STATUS);
    }

    public boolean canHotswap() {
        String verifierStatus = getVerifierStatus();
        if (VALUE_VERIFIER_STATUS_COMPATIBLE.equals(verifierStatus)) {
            return true;
        } else if (verifierStatus.isEmpty()) {
            // build-info.xml doesn't currently specify a verifier status if there is *only* a resource
            // change!
            List<InstantRunArtifact> artifacts = getArtifacts();
            if (artifacts.size() == 1
                    && artifacts.get(0).type == InstantRunArtifactType.RESOURCES) {
                return true;
            }
        }

        return false;
    }

    /** Returns whether there were NO changes from the previous build. */
    public boolean hasNoChanges() {
        return getArtifacts().isEmpty();
    }

    public int getFeatureLevel() { // The build info calls it as the API level, but we always pass the feature level to Gradle..
        String attribute = mRoot.getAttribute(ATTR_API_LEVEL);
        if (attribute != null && !attribute.isEmpty()) {
            try {
                return Integer.parseInt(attribute);
            } catch (NumberFormatException ignore) {
            }
        }
        return -1; // unknown
    }

    @NonNull
    public List<InstantRunArtifact> getArtifacts() {
        if (mArtifacts == null) {
            List<InstantRunArtifact> artifacts = Lists.newArrayList();

            Element oldestBuild = null;
            long oldestTimeStamp = Long.MAX_VALUE;

            NodeList children = mRoot.getChildNodes();
            for (int i = 0, n = children.getLength(); i < n; i++) {
                Node child = children.item(i);
                if (child.getNodeType() == Node.ELEMENT_NODE) {
                    Element element = (Element) child;
                    String tagName = element.getTagName();
                    if (!TAG_ARTIFACT.equals(tagName)) {
                        if (!BUILDS_ARE_SORTED && TAG_BUILD.equals(tagName)) {
                            String timestamp = element.getAttribute(ATTR_TIMESTAMP);
                            if (!timestamp.isEmpty()) {
                                try {
                                    long time = Long.parseLong(timestamp);
                                    if (time < oldestTimeStamp) {
                                        oldestTimeStamp = time;
                                        oldestBuild = element;
                                    }
                                } catch (NumberFormatException ignore) {
                                }
                            }
                        }
                        continue;
                    }

                    String location = element.getAttribute(ATTR_ARTIFACT_LOCATION);
                    String typeAttribute = element.getAttribute(ATTR_ARTIFACT_TYPE);
                    InstantRunArtifactType type = InstantRunArtifactType.valueOf(typeAttribute);
                    artifacts.add(new InstantRunArtifact(type, new File(location)));
                }
            }

            mArtifacts = artifacts;

            if (hasOneOf(InstantRunArtifactType.SPLIT_MAIN)) {
                // If main has changed, we need ALL the slices. Look in the history for these.
                // This is always available from the LAST build history.
                if (BUILDS_ARE_SORTED) {
                    for (int i = children.getLength() - 1; i >= 0; i--) {
                        Node child = children.item(i);
                        if (child.getNodeType() == Node.ELEMENT_NODE) {
                            Element element = (Element) child;
                            if (!TAG_BUILD.equals(element.getTagName())) {
                                continue;
                            }
                            oldestBuild = element;
                            break;
                        }
                    }
                }

                if (oldestBuild != null) {
                    ListIterator<InstantRunArtifact> iterator = artifacts.listIterator();
                    while (iterator.hasNext()) {
                        InstantRunArtifact artifact = iterator.next();
                        if (artifact.type == InstantRunArtifactType.SPLIT) {
                            iterator.remove();
                        }
                    }

                    NodeList nestedChildren = oldestBuild.getChildNodes();
                    for (int j = 0, n = nestedChildren.getLength(); j < n; j++) {
                        Node nestedChild = nestedChildren.item(j);
                        if (nestedChild.getNodeType() == Node.ELEMENT_NODE) {
                            Element artifactElement = (Element) nestedChild;
                            if (!TAG_ARTIFACT.equals(artifactElement.getTagName())) {
                                continue;
                            }

                            String typeAttribute = artifactElement.getAttribute(ATTR_ARTIFACT_TYPE);
                            InstantRunArtifactType type = InstantRunArtifactType
                                    .valueOf(typeAttribute);
                            if (type == InstantRunArtifactType.SPLIT) {
                                String location = artifactElement
                                        .getAttribute(ATTR_ARTIFACT_LOCATION);
                                artifacts.add(new InstantRunArtifact(type, new File(location)));
                            }
                        }
                    }
                }
            }
        }

        return mArtifacts;
    }

    /**
     * Returns true if the given list of artifacts contains at least one artifact of any of the
     * given types
     *
     * @param types the types to look for
     * @return true if and only if the list of artifacts contains an artifact of any of the given
     * types
     */
    public boolean hasOneOf(@NonNull InstantRunArtifactType... types) {
        for (InstantRunArtifact artifact : getArtifacts()) {
            for (InstantRunArtifactType type : types) {
                if (artifact.type == type) {
                    return true;
                }
            }
        }
        return false;
    }

    public boolean hasMainApk() {
        return hasOneOf(InstantRunArtifactType.MAIN) || hasOneOf(InstantRunArtifactType.SPLIT_MAIN);
    }

    @Nullable
    public static InstantRunBuildInfo get(@NonNull String xml) {
        Document doc = XmlUtils.parseDocumentSilently(xml, false);
        if (doc == null) {
            return null;
        }

        return new InstantRunBuildInfo(doc.getDocumentElement());
    }

    // Keep roughly in sync with InstantRunBuildContext#CURRENT_FORMAT.
    //
    // See longer comment on that field for why they're separate fields rather
    // than this code just referencing that field.
    public boolean isCompatibleFormat() {
        // Right don't accept older versions; due to bugs we want to force everyone to use the latest or no instant run at all.
        // In the future we'll probably accept a range of values here.
        return getFormat() == 8;
    }

    public int getFormat() {
        String attribute = mRoot.getAttribute(ATTR_FORMAT);
        if (Strings.isNullOrEmpty(attribute)) {
            return -1;
        }

        try {
            return Integer.parseInt(attribute);
        } catch (NumberFormatException nfe) {
            return -1;
        }
    }
}
