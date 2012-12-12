/*
 * Copyright (C) 2012 The Android Open Source Project
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
package com.android.utils;

import static com.android.SdkConstants.AMP_ENTITY;
import static com.android.SdkConstants.ANDROID_NS_NAME;
import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.APOS_ENTITY;
import static com.android.SdkConstants.APP_PREFIX;
import static com.android.SdkConstants.LT_ENTITY;
import static com.android.SdkConstants.QUOT_ENTITY;
import static com.android.SdkConstants.XMLNS;
import static com.android.SdkConstants.XMLNS_PREFIX;
import static com.android.SdkConstants.XMLNS_URI;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.google.common.base.Splitter;

import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.HashSet;

/** XML Utilities */
public class XmlUtils {
    /**
     * Returns the namespace prefix matching the requested namespace URI.
     * If no such declaration is found, returns the default "android" prefix for
     * the Android URI, and "app" for other URI's. By default the app namespace
     * will be created. If this is not desirable, call
     * {@link #lookupNamespacePrefix(Node, String, boolean)} instead.
     *
     * @param node The current node. Must not be null.
     * @param nsUri The namespace URI of which the prefix is to be found,
     *              e.g. {@link SdkConstants#ANDROID_URI}
     * @return The first prefix declared or the default "android" prefix
     *              (or "app" for non-Android URIs)
     */
    @NonNull
    public static String lookupNamespacePrefix(@NonNull Node node, @NonNull String nsUri) {
        String defaultPrefix = ANDROID_URI.equals(nsUri) ? ANDROID_NS_NAME : APP_PREFIX;
        return lookupNamespacePrefix(node, nsUri, defaultPrefix, true /*create*/);
    }

    /**
     * Returns the namespace prefix matching the requested namespace URI. If no
     * such declaration is found, returns the default "android" prefix for the
     * Android URI, and "app" for other URI's.
     *
     * @param node The current node. Must not be null.
     * @param nsUri The namespace URI of which the prefix is to be found, e.g.
     *            {@link SdkConstants#ANDROID_URI}
     * @param create whether the namespace declaration should be created, if
     *            necessary
     * @return The first prefix declared or the default "android" prefix (or
     *         "app" for non-Android URIs)
     */
    @NonNull
    public static String lookupNamespacePrefix(@NonNull Node node, @NonNull String nsUri,
            boolean create) {
        String defaultPrefix = ANDROID_URI.equals(nsUri) ? ANDROID_NS_NAME : APP_PREFIX;
        return lookupNamespacePrefix(node, nsUri, defaultPrefix, create);
    }

    /**
     * Returns the namespace prefix matching the requested namespace URI. If no
     * such declaration is found, returns the default "android" prefix.
     *
     * @param node The current node. Must not be null.
     * @param nsUri The namespace URI of which the prefix is to be found, e.g.
     *            {@link SdkConstants#ANDROID_URI}
     * @param defaultPrefix The default prefix (root) to use if the namespace is
     *            not found. If null, do not create a new namespace if this URI
     *            is not defined for the document.
     * @param create whether the namespace declaration should be created, if
     *            necessary
     * @return The first prefix declared or the provided prefix (possibly with a
     *            number appended to avoid conflicts with existing prefixes.
     */
    public static String lookupNamespacePrefix(
            @Nullable Node node, @Nullable String nsUri, @Nullable String defaultPrefix,
            boolean create) {
        // Note: Node.lookupPrefix is not implemented in wst/xml/core NodeImpl.java
        // The following code emulates this simple call:
        //   String prefix = node.lookupPrefix(NS_RESOURCES);

        // if the requested URI is null, it denotes an attribute with no namespace.
        if (nsUri == null) {
            return null;
        }

        // per XML specification, the "xmlns" URI is reserved
        if (XMLNS_URI.equals(nsUri)) {
            return XMLNS;
        }

        HashSet<String> visited = new HashSet<String>();
        Document doc = node == null ? null : node.getOwnerDocument();

        // Ask the document about it. This method may not be implemented by the Document.
        String nsPrefix = null;
        try {
            nsPrefix = doc != null ? doc.lookupPrefix(nsUri) : null;
            if (nsPrefix != null) {
                return nsPrefix;
            }
        } catch (Throwable t) {
            // ignore
        }

        // If that failed, try to look it up manually.
        // This also gathers prefixed in use in the case we want to generate a new one below.
        for (; node != null && node.getNodeType() == Node.ELEMENT_NODE;
               node = node.getParentNode()) {
            NamedNodeMap attrs = node.getAttributes();
            for (int n = attrs.getLength() - 1; n >= 0; --n) {
                Node attr = attrs.item(n);
                if (XMLNS.equals(attr.getPrefix())) {
                    String uri = attr.getNodeValue();
                    nsPrefix = attr.getLocalName();
                    // Is this the URI we are looking for? If yes, we found its prefix.
                    if (nsUri.equals(uri)) {
                        return nsPrefix;
                    }
                    visited.add(nsPrefix);
                }
            }
        }

        // Failed the find a prefix. Generate a new sensible default prefix, unless
        // defaultPrefix was null in which case the caller does not want the document
        // modified.
        if (defaultPrefix == null) {
            return null;
        }

        //
        // We need to make sure the prefix is not one that was declared in the scope
        // visited above. Pick a unique prefix from the provided default prefix.
        String prefix = defaultPrefix;
        String base = prefix;
        for (int i = 1; visited.contains(prefix); i++) {
            prefix = base + Integer.toString(i);
        }
        // Also create & define this prefix/URI in the XML document as an attribute in the
        // first element of the document.
        if (doc != null) {
            node = doc.getFirstChild();
            while (node != null && node.getNodeType() != Node.ELEMENT_NODE) {
                node = node.getNextSibling();
            }
            if (node != null && create) {
                // This doesn't work:
                //Attr attr = doc.createAttributeNS(XMLNS_URI, prefix);
                //attr.setPrefix(XMLNS);
                //
                // Xerces throws
                //org.w3c.dom.DOMException: NAMESPACE_ERR: An attempt is made to create or
                // change an object in a way which is incorrect with regard to namespaces.
                //
                // Instead pass in the concatenated prefix. (This is covered by
                // the UiElementNodeTest#testCreateNameSpace() test.)
                Attr attr = doc.createAttributeNS(XMLNS_URI, XMLNS_PREFIX + prefix);
                attr.setValue(nsUri);
                node.getAttributes().setNamedItemNS(attr);
            }
        }

        return prefix;
    }

    /**
     * Converts the given attribute value to an XML-attribute-safe value, meaning that
     * single and double quotes are replaced with their corresponding XML entities.
     *
     * @param attrValue the value to be escaped
     * @return the escaped value
     */
    @NonNull
    public static String toXmlAttributeValue(@NonNull String attrValue) {
        for (int i = 0, n = attrValue.length(); i < n; i++) {
            char c = attrValue.charAt(i);
            if (c == '"' || c == '\'' || c == '<' || c == '&') {
                StringBuilder sb = new StringBuilder(2 * attrValue.length());
                appendXmlAttributeValue(sb, attrValue);
                return sb.toString();
            }
        }

        return attrValue;
    }

    /**
     * Converts the given attribute value to an XML-text-safe value, meaning that
     * less than and ampersand characters are escaped.
     *
     * @param textValue the text value to be escaped
     * @return the escaped value
     */
    @NonNull
    public static String toXmlTextValue(@NonNull String textValue) {
        for (int i = 0, n = textValue.length(); i < n; i++) {
            char c = textValue.charAt(i);
            if (c == '<' || c == '&') {
                StringBuilder sb = new StringBuilder(2 * textValue.length());
                appendXmlTextValue(sb, textValue);
                return sb.toString();
            }
        }

        return textValue;
    }

    /**
     * Appends text to the given {@link StringBuilder} and escapes it as required for a
     * DOM attribute node.
     *
     * @param sb the string builder
     * @param attrValue the attribute value to be appended and escaped
     */
    public static void appendXmlAttributeValue(@NonNull StringBuilder sb,
            @NonNull String attrValue) {
        int n = attrValue.length();
        // &, ", ' and < are illegal in attributes; see http://www.w3.org/TR/REC-xml/#NT-AttValue
        // (' legal in a " string and " is legal in a ' string but here we'll stay on the safe
        // side)
        for (int i = 0; i < n; i++) {
            char c = attrValue.charAt(i);
            if (c == '"') {
                sb.append(QUOT_ENTITY);
            } else if (c == '<') {
                sb.append(LT_ENTITY);
            } else if (c == '\'') {
                sb.append(APOS_ENTITY);
            } else if (c == '&') {
                sb.append(AMP_ENTITY);
            } else {
                sb.append(c);
            }
        }
    }

    /**
     * Appends text to the given {@link StringBuilder} and escapes it as required for a
     * DOM text node.
     *
     * @param sb the string builder
     * @param textValue the text value to be appended and escaped
     */
    public static void appendXmlTextValue(@NonNull StringBuilder sb, @NonNull String textValue) {
        for (int i = 0, n = textValue.length(); i < n; i++) {
            char c = textValue.charAt(i);
            if (c == '<') {
                sb.append(LT_ENTITY);
            } else if (c == '&') {
                sb.append(AMP_ENTITY);
            } else {
                sb.append(c);
            }
        }
    }

    /**
     * Dump an XML tree to string. This isn't going to do a beautiful job pretty
     * printing the XML; it's intended mostly for non-user editable files and
     * for debugging. If true, preserve whitespace exactly as in the DOM
     * document (typically used for a DOM which is already formatted), otherwise
     * this method will insert some newlines here and there (for example, one
     * per element and one per attribute.)
     *
     * @param node the node (which can be a document, an element, a text node,
     *            etc.
     * @param preserveWhitespace whether to preserve the whitespace (text nodes)
     *            in the DOM
     * @return a string version of the file
     */
    public static String toXml(Node node, boolean preserveWhitespace) {
        StringBuilder sb = new StringBuilder(1000);

        append(sb, node, 0, preserveWhitespace);

        return sb.toString();
    }

    private static void indent(StringBuilder sb, int indent) {
        for (int i = 0; i < indent; i++) {
            sb.append("    ");
        }
    }

    private static void append(
            @NonNull StringBuilder sb,
            @NonNull Node node,
            int indent,
            boolean preserveWhitespace) {
        short nodeType = node.getNodeType();
        switch (nodeType) {
            case Node.DOCUMENT_NODE:
            case Node.DOCUMENT_FRAGMENT_NODE: {
                sb.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"); //$NON-NLS-1$
                NodeList children = node.getChildNodes();
                for (int i = 0, n = children.getLength(); i < n; i++) {
                    append(sb, children.item(i), indent, preserveWhitespace);
                }
                break;
            }
            case Node.COMMENT_NODE:
            case Node.TEXT_NODE: {
                if (nodeType == Node.COMMENT_NODE) {
                    if (!preserveWhitespace) {
                        indent(sb, indent);
                    }
                    sb.append("<!--"); //$NON-NLS-1$
                    if (!preserveWhitespace) {
                        sb.append('\n');
                    }
                }
                String text = node.getNodeValue();
                if (!preserveWhitespace) {
                    text = text.trim();
                    for (String line : Splitter.on('\n').split(text)) {
                        indent(sb, indent + 1);
                        sb.append(toXmlTextValue(line));
                        sb.append('\n');
                    }
                } else {
                    sb.append(toXmlTextValue(text));
                }
                if (nodeType == Node.COMMENT_NODE) {
                    if (!preserveWhitespace) {
                        indent(sb, indent);
                    }
                    sb.append("-->"); //$NON-NLS-1$
                    if (!preserveWhitespace) {
                        sb.append('\n');
                    }
                }
                break;
            }
            case Node.ELEMENT_NODE: {
                if (!preserveWhitespace) {
                    indent(sb, indent);
                }
                sb.append('<');
                Element element = (Element) node;
                sb.append(element.getTagName());

                NamedNodeMap attributes = element.getAttributes();
                NodeList children = element.getChildNodes();
                int childCount = children.getLength();
                int attributeCount = attributes.getLength();

                if (attributeCount > 0) {
                    for (int i = 0; i < attributeCount; i++) {
                        Node attribute = attributes.item(i);
                        sb.append(' ');
                        sb.append(attribute.getNodeName());
                        sb.append('=').append('"');
                        sb.append(toXmlAttributeValue(attribute.getNodeValue()));
                        sb.append('"');
                    }
                }

                if (childCount == 0) {
                    sb.append('/');
                }
                sb.append('>');
                if (!preserveWhitespace) {
                    sb.append('\n');
                }
                if (childCount > 0) {
                    for (int i = 0; i < childCount; i++) {
                        Node child = children.item(i);
                        append(sb, child, indent + 1, preserveWhitespace);
                    }
                    if (!preserveWhitespace) {
                        if (sb.charAt(sb.length() - 1) != '\n') {
                            sb.append('\n');
                        }
                        indent(sb, indent);
                    }
                    sb.append('<').append('/');
                    sb.append(element.getTagName());
                    sb.append('>');
                    if (!preserveWhitespace) {
                        sb.append('\n');
                    }
                }
                break;
            }

            default:
                throw new UnsupportedOperationException(
                        "Unsupported node type " + nodeType + ": not yet implemented");
        }
    }
}