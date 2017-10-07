/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.tools.lint.detector.api;

import com.android.annotations.NonNull;
import com.android.utils.SdkUtils;
import com.android.utils.XmlUtils;

/**
 * Lint error message, issue explanations and location descriptions
 * are described in a {@link #RAW} format which looks similar to text
 * but which can contain bold, symbols and links. These issues can
 * also be converted to plain text and to HTML markup, using the
 * {@link #convertTo(String, TextFormat)} method.
 *
 * @see Issue#getExplanation(TextFormat)
 * @see Issue#getBriefDescription(TextFormat)
 */
public enum TextFormat {
    /**
     * Raw output format which is similar to text but allows some markup:
     * <ul>
     * <li>HTTP urls (http://...)
     * <li>Sentences immediately surrounded by * will be shown as italics.
     * <li>Sentences immediately surrounded by ** will be shown as bold.
     * <li>Sentences immediately surrounded by *** will be shown as bold italics.
     * <li>Sentences immediately surrounded by ` will be shown using monospace
     * fonts
     * <li>You can escape the previous characters with a backslash, \. Backslash
     * characters must themselves be escaped with a backslash, e.g. use \\.
     * <li>If you want to use bold or italics within a word, you can use the
     * trick of putting a zero-width space between the characters by entering
     * a \\u200b unicode character.</li>
     * </ul>
     * Furthermore, newlines are converted to br's when converting newlines.
     * Note: It does not insert {@code <html>} tags around the fragment for HTML output.
     * <p>
     * TODO: Consider switching to the restructured text format -
     *  http://docutils.sourceforge.net/docs/user/rst/quickstart.html
     */
    RAW,

    /**
     * Plain text output
     */
    TEXT,

    /**
     * HTML formatted output (note: does not include surrounding {@code <html></html>} tags)
     */
    HTML,

    /**
     * HTML formatted output (note: does not include surrounding {@code <html></html>} tags).
     * This is like {@link #HTML}, but it does not escape unicode characters with entities.
     * <p>
     * (This is used for example in the IDE, where some partial HTML support in some
     * label widgets support some HTML markup, but not numeric code character entities.)
     */
    HTML_WITH_UNICODE;

    /**
     * Converts the given text to HTML
     *
     * @param text the text to format
     * @return the corresponding text formatted as HTML
     */
    @NonNull
    public String toHtml(@NonNull String text) {
        return convertTo(text, HTML);
    }

    /**
     * Converts the given text to plain text
     *
     * @param text the tetx to format
     * @return the corresponding text formatted as HTML
     */
    @NonNull
    public String toText(@NonNull String text) {
        return convertTo(text, TEXT);
    }

    /**
     * Converts the given message to the given format. Note that some
     * conversions are lossy; e.g. once converting away from the raw format
     * (which contains all the markup) you can't convert back to it.
     * Note that you can convert to the format it's already in; that just
     * returns the same string.
     *
     * @param message the message to convert
     * @param to the format to convert to
     * @return a converted message
     */
    public String convertTo(@NonNull String message, @NonNull TextFormat to) {
        if (this == to) {
            return message;
        }
        switch (this) {
            case RAW: {
                switch (to) {
                    case RAW:
                        return message;
                    case TEXT:
                    case HTML:
                    case HTML_WITH_UNICODE:
                        return to.fromRaw(message);
                }
            }
            case TEXT: {
                switch (to) {
                    case RAW:
                        return textToRaw(message);
                    case TEXT:
                        return message;
                    case HTML:
                    case HTML_WITH_UNICODE:
                        return XmlUtils.toXmlTextValue(message);
                }
            }
            case HTML: {
                switch (to) {
                    case HTML:
                        return message;
                    case HTML_WITH_UNICODE:
                        return removeNumericEntities(message);
                    case RAW:
                    case TEXT: {
                        return to.fromHtml(message);

                    }
                }
            }
            case HTML_WITH_UNICODE: {
                switch (to) {
                    case HTML:
                    case HTML_WITH_UNICODE:
                        return message;
                    case RAW:
                    case TEXT: {
                        return to.fromHtml(message);

                    }
                }
            }
        }
        return message;
    }

    @NonNull
    private static String textToRaw(@NonNull String message) {
        boolean mustEscape = false;
        int n = message.length();
        for (int i = 0; i < n; i++) {
            char c = message.charAt(i);
            if (c == '\\' || c == '*' || c == '`') {
                mustEscape = true;
                break;
            }
        }

        if (!mustEscape) {
            return message;
        }

        StringBuilder sb = new StringBuilder(message.length() * 2);
        for (int i = 0; i < n; i++) {
            char c = message.charAt(i);
            if (c == '\\' || c == '*' || c == '`') {
                sb.append('\\');
            }
            sb.append(c);
        }

        return sb.toString();
    }

    /** Converts to this output format from the given HTML-format text */
    @NonNull
    private String fromHtml(@NonNull String html) {
        assert this == RAW || this == TEXT : this;

        // Drop all tags; replace all entities, insert newlines
        // (this won't do wrapping)
        StringBuilder sb = new StringBuilder(html.length());
        boolean inPre = false;
        for (int i = 0, n = html.length(); i < n; i++) {
            char c = html.charAt(i);
            if (c == '<') {
                // Strip comments
                if (html.startsWith("<!--", i)) {
                    int end = html.indexOf("-->", i);
                    if (end == -1) {
                        break; // Unclosed comment
                    } else {
                        i = end + 2;
                    }
                    continue;
                }
                // Tags: scan forward to the end
                int begin;
                boolean isEndTag = false;
                if (html.startsWith("</", i)) {
                    begin = i + 2;
                    isEndTag = true;
                } else {
                    begin = i + 1;
                }
                i = html.indexOf('>', i);
                if (i == -1) {
                    // Unclosed tag
                    break;
                }
                int end = i;
                if (html.charAt(i - 1) == '/') {
                    end--;
                    isEndTag = true;
                }
                // TODO: Handle <pre> such that we don't collapse spaces and reformat there!
                // (We do need to strip out tags and expand entities)
                String tag = html.substring(begin, end).trim();
                if (tag.equalsIgnoreCase("br")) {
                    sb.append('\n');
                } else if (tag.equalsIgnoreCase("p") // Most common block tags
                           || tag.equalsIgnoreCase("div")
                           || tag.equalsIgnoreCase("pre")
                           || tag.equalsIgnoreCase("blockquote")
                           || tag.equalsIgnoreCase("dl")
                           || tag.equalsIgnoreCase("dd")
                           || tag.equalsIgnoreCase("dt")
                           || tag.equalsIgnoreCase("ol")
                           || tag.equalsIgnoreCase("ul")
                           || tag.equalsIgnoreCase("li")
                            || tag.length() == 2 && tag.startsWith("h")
                                    && Character.isDigit(tag.charAt(1))) {
                    // Block tag: ensure new line
                    if (sb.length() > 0 && sb.charAt(sb.length() - 1) != '\n') {
                        sb.append('\n');
                    }
                    if (tag.equals("li") && !isEndTag) {
                        sb.append("* ");
                    }
                    if (tag.equalsIgnoreCase("pre")) {
                        inPre = !isEndTag;
                    }
                }
            } else if (c == '&') {
                int end = html.indexOf(';', i);
                if (end > i) {
                    String entity = html.substring(i, end + 1);
                    String s = XmlUtils.fromXmlAttributeValue(entity);
                    if (s.startsWith("&")) {
                        // Not an XML entity; for example, &nbsp;
                        // Sadly Guava's HtmlEscapes don't handle this either.
                        if (entity.equalsIgnoreCase("&nbsp;")) {
                            s = " ";
                        } else if (entity.startsWith("&#")) {
                            try {
                                int value = Integer.parseInt(entity.substring(2));
                                s = Character.toString((char)value);
                            } catch (NumberFormatException ignore) {
                            }
                        }
                    }
                    sb.append(s);
                    i = end;
                } else {
                    sb.append(c);
                }
            } else if (Character.isWhitespace(c)) {
                if (inPre) {
                    sb.append(c);
                } else if (sb.length() == 0
                                || !Character.isWhitespace(sb.charAt(sb.length() - 1))) {
                    sb.append(' ');
                }
            } else {
                sb.append(c);
            }
        }

        String s = sb.toString();

        // Line-wrap
        s = SdkUtils.wrap(s, 60, null);

        return s;
    }

    private static final String HTTP_PREFIX = "http://";
    private static final String HTTPS_PREFIX = "https://";

    /** Converts to this output format from the given raw-format text */
    @NonNull
    private String fromRaw(@NonNull String text) {
        assert this == HTML || this == HTML_WITH_UNICODE || this == TEXT : this;
        StringBuilder sb = new StringBuilder(3 * text.length() / 2);
        boolean html = this == HTML || this == HTML_WITH_UNICODE;
        boolean escapeUnicode = this == HTML;

        char prev = 0;
        int flushIndex = 0;
        int n = text.length();
        boolean escaped = false;
        for (int i = 0; i < n; i++) {
            char c = text.charAt(i);
            if (c == '\\' && !escaped) {
                escaped = true;
                if (i > flushIndex) {
                    appendEscapedText(sb, text, html, flushIndex, i, escapeUnicode);
                }
                flushIndex = i + 1;
                continue;
            }

            if (!escaped && (c == '*' || c == '`') && i < n - 1) {
                // Scout ahead for range end
                if (!Character.isLetterOrDigit(prev)
                        && !Character.isWhitespace(text.charAt(i + 1))) {
                    // Found * or ` immediately before a letter, and not in the middle of a word
                    // Find end
                    int end = text.indexOf(c, i + 1);
                    boolean bold = false;
                    if (end == i + 1 && c == '*') {
                        int end2 = text.indexOf('*', end + 1);
                        if (end2 == end + 1) {
                            end2 = text.indexOf("***", end2 + 1);
                            if (end2 != -1) {
                                // *** means bold italics
                                if (i > flushIndex) {
                                    appendEscapedText(sb, text, html, flushIndex, i, escapeUnicode);
                                }
                                if (html) {
                                    sb.append("<b><i>");
                                    appendEscapedText(sb, text, true, i + 3, end2, escapeUnicode);
                                    sb.append("</i></b>");
                                } else {
                                    appendEscapedText(sb, text, false, i + 3, end2, escapeUnicode);
                                }
                                flushIndex = end2 + 3;
                                i = flushIndex - 1; // -1: account for the i++ in the loop
                            }
                            continue;
                        } else if (end2 != -1 && end2 > end + 1 && end2 < n - 1 &&
                                text.charAt(end2 + 1) == '*') {
                            end = end2;
                            bold = true;
                        }
                    }

                    if (end != -1 && (end == n - 1 || !Character.isLetter(text.charAt(end + 1)))) {
                        if (i > flushIndex) {
                            appendEscapedText(sb, text, html, flushIndex, i, escapeUnicode);
                        }
                        if (bold) {
                            i++;
                        }
                        if (html) {
                            String tag = bold ? "b" : c == '*' ? "i" : "code";
                            sb.append('<').append(tag).append('>');
                            appendEscapedText(sb, text, true, i + 1, end, escapeUnicode);
                            sb.append('<').append('/').append(tag).append('>');
                        } else {
                            appendEscapedText(sb, text, false, i + 1, end, escapeUnicode);
                        }
                        flushIndex = end + 1;
                        if (bold) {
                            flushIndex++;
                        }
                        i = flushIndex - 1; // -1: account for the i++ in the loop
                    }
                }
            } else if (html
                    && c == 'h' && i < n - 1 && text.charAt(i + 1) == 't'
                    && (text.startsWith(HTTP_PREFIX, i) || text.startsWith(HTTPS_PREFIX, i))
                    && !Character.isLetterOrDigit(prev)) {
                // Find url end
                int length = text.startsWith(HTTP_PREFIX, i) ?
                        HTTP_PREFIX.length() : HTTPS_PREFIX.length();
                int end = i + length;
                while (end < n) {
                    char d = text.charAt(end);
                    if (terminatesUrl(d)) {
                        break;
                    }
                    end++;
                }
                char last = text.charAt(end - 1);
                if (last == '.' || last == ')' || last == '!') {
                    end--;
                }
                if (end > i + length) {
                    if (i > flushIndex) {
                        appendEscapedText(sb, text, true, flushIndex, i, escapeUnicode);
                    }

                    String url = text.substring(i, end);
                    sb.append("<a href=\"");
                    sb.append(url);
                    sb.append('"').append('>');
                    sb.append(url);
                    sb.append("</a>");

                    flushIndex = end;
                    i = flushIndex - 1; // -1: account for the i++ in the loop
                }
            } else if (c == '\n' && escaped) {
                flushIndex++;
            }
            prev = c;
            escaped = false;
        }

        if (flushIndex < n) {
            appendEscapedText(sb, text, html, flushIndex, n, escapeUnicode);
        }

        return sb.toString();
    }

    private static boolean terminatesUrl(char c) {
        if (c >= 'a' && c <= 'z') {
            return false;
        }
        if (c >= 'A' && c <= 'Z') {
            return false;
        }
        if (c >= '0' && c <= '9') {
            return false;
        }
        switch (c) {
            case '-':
            case '_':
            case '.':
            case '*':
            case '+':
            case '%':
            case '/':
            case '#':
                return false;
        }

        return true;
    }

    private static String removeNumericEntities(@NonNull String html) {
        if (!html.contains("&#")) {
            return html;
        }

        StringBuilder sb = new StringBuilder(html.length());
        for (int i = 0, n = html.length(); i < n; i++) {
            char c = html.charAt(i);
            if (c == '&' && i < n - 1 && html.charAt(i + 1) == '#') {
                int end = html.indexOf(';', i + 2);
                if (end != -1) {
                    String decimal = html.substring(i + 2, end);
                    try {
                        c = (char)Integer.parseInt(decimal);
                        sb.append(c);
                        i = end;
                        continue;
                    } catch (NumberFormatException ignore) {
                        // fall through to not escape this
                    }
                }
            }
            sb.append(c);
        }

        return sb.toString();
    }

    private static void appendEscapedText(@NonNull StringBuilder sb, @NonNull String text,
            boolean html, int start, int end, boolean escapeUnicode) {
        if (html) {
            for (int i = start; i < end; i++) {
                char c = text.charAt(i);
                if (c == '<') {
                    sb.append("&lt;");
                } else if (c == '&') {
                    sb.append("&amp;");
                } else if (c == '\n') {
                    sb.append("<br/>\n");
                } else {
                    if (c > 255 && escapeUnicode) {
                        if (c == '\u200b') {
                            // Skip zero-width spaces; they're there to let you insert "word"
                            // separators when you want to use * characters for formatting,
                            // e.g. to get italics for "NN" in "values-vNN" you can't use
                            // "values-v*NN*" since "*" is in the middle of the word, but you
                            // can use "values-v\u200b*NN*"
                            continue;
                        }
                        sb.append("&#");
                        sb.append(Integer.toString(c));
                        sb.append(';');
                    } else if (c == '\u00a0') {
                        sb.append("&nbsp;");
                    } else {
                        sb.append(c);
                    }
                }
            }
        } else {
            for (int i = start; i < end; i++) {
                char c = text.charAt(i);
                if (c == '\u200b') {
                    // See comment under HTML section
                    continue;
                }
                sb.append(c);
            }
        }
    }
}
