// TODO adapted from https://github.com/x-stream/xstream/blob/32e52a6519a25366bbb5774bb536b5e290b64a42/xstream/src/java/com/thoughtworks/xstream/io/xml/PrettyPrintWriter.java pending release of https://github.com/jenkinsci/jenkins/pull/7924

/*
 * Copyright (C) 2004, 2005, 2006 Joe Walnes.
 * Copyright (C) 2006, 2007, 2008, 2009, 2011, 2013, 2014, 2015 XStream Committers.
 * All rights reserved.
 *
 * The software in this package is published under the terms of the BSD
 * style license a copy of which has been included with this distribution in
 * the LICENSE.txt file.
 *
 * Created on 07. March 2004 by Joe Walnes
 */

package hudson.util;

import com.thoughtworks.xstream.core.util.FastStack;
import com.thoughtworks.xstream.core.util.QuickWriter;
import com.thoughtworks.xstream.io.StreamException;
import com.thoughtworks.xstream.io.naming.NameCoder;
import com.thoughtworks.xstream.io.xml.AbstractXmlWriter;
import com.thoughtworks.xstream.io.xml.XmlFriendlyNameCoder;
import com.thoughtworks.xstream.io.xml.XmlFriendlyReplacer;
import java.io.Writer;


/**
 * A simple writer that outputs XML in a pretty-printed indented stream.
 * <p>
 * By default, the chars <br>
 * <code>&amp; &lt; &gt; &quot; ' \r</code><br>
 * are escaped and replaced with a suitable XML entity. To alter this behavior, override the
 * {@link #writeText(com.thoughtworks.xstream.core.util.QuickWriter, String)} and
 * {@link #writeAttributeValue(com.thoughtworks.xstream.core.util.QuickWriter, String)} methods.
 * </p>
 * <p>
 * The XML specification requires XML parsers to drop CR characters completely. This implementation will therefore use
 * only a LF for line endings, never the platform encoding. You can overwrite the {@link #getNewLine()} method for a
 * different behavior.
 * </p>
 * <p>
 * Note: Depending on the XML version some characters cannot be written. Especially a 0 character is never valid in XML,
 * neither directly nor as entity nor within CDATA. However, this writer works by default in a quirks mode, where it
 * will write any character at least as character entity (even a null character). You may switch into XML_1_1 mode
 * (which supports most characters) or XML_1_0 that does only support a very limited number of control characters. See
 * XML specification for version <a href="http://www.w3.org/TR/2006/REC-xml-20060816/#charsets">1.0</a> or <a
 * href="http://www.w3.org/TR/2006/REC-xml11-20060816/#charsets">1.1</a>. If a character is not supported, a
 * {@link StreamException} is thrown. Select a proper parser implementation that respects the version in the XML header
 * (the Xpp3 parser will also read character entities of normally invalid characters).
 * </p>
 *
 * @author Joe Walnes
 * @author J&ouml;rg Schaible
 */
class PrettyPrintWriter extends AbstractXmlWriter {

    public static int XML_QUIRKS = -1;
    public static int XML_1_0 = 0;
    public static int XML_1_1 = 1;

    private final QuickWriter writer;
    private final FastStack elementStack = new FastStack(16);
    private final char[] lineIndenter;
    private final int mode;

    private boolean tagInProgress;
    protected int depth;
    private boolean readyForNewLine;
    private boolean tagIsEmpty;

    private static final char[] NULL = "&#x0;".toCharArray();
    private static final char[] AMP = "&amp;".toCharArray();
    private static final char[] LT = "&lt;".toCharArray();
    private static final char[] GT = "&gt;".toCharArray();
    private static final char[] CR = "&#xd;".toCharArray();
    private static final char[] QUOT = "&quot;".toCharArray();
    private static final char[] APOS = "&apos;".toCharArray();
    private static final char[] CLOSE = "</".toCharArray();

    /**
     * @since 1.4
     */
    PrettyPrintWriter(final Writer writer, final int mode, final char[] lineIndenter, final NameCoder nameCoder) {
        super(nameCoder);
        this.writer = new QuickWriter(writer);
        this.lineIndenter = lineIndenter;
        this.mode = mode;
        if (mode < XML_QUIRKS || mode > XML_1_1) {
            throw new IllegalArgumentException("Not a valid XML mode");
        }
    }

    /**
     * @since 1.3
     * @deprecated As of 1.4 use {@link PrettyPrintWriter#PrettyPrintWriter(Writer, int, char[], NameCoder)} instead
     */
    @Deprecated
    PrettyPrintWriter(
            final Writer writer, final int mode, final char[] lineIndenter, final XmlFriendlyReplacer replacer) {
        this(writer, mode, lineIndenter, (NameCoder) replacer);
    }

    /**
     * @since 1.3
     */
    PrettyPrintWriter(final Writer writer, final int mode, final char[] lineIndenter) {
        this(writer, mode, lineIndenter, new XmlFriendlyNameCoder());
    }

    PrettyPrintWriter(final Writer writer, final char[] lineIndenter) {
        this(writer, XML_QUIRKS, lineIndenter);
    }

    /**
     * @since 1.3
     */
    PrettyPrintWriter(final Writer writer, final int mode, final String lineIndenter) {
        this(writer, mode, lineIndenter.toCharArray());
    }

    PrettyPrintWriter(final Writer writer, final String lineIndenter) {
        this(writer, lineIndenter.toCharArray());
    }

    /**
     * @since 1.4
     */
    PrettyPrintWriter(final Writer writer, final int mode, final NameCoder nameCoder) {
        this(writer, mode, new char[]{' ', ' '}, nameCoder);
    }

    /**
     * @since 1.3
     * @deprecated As of 1.4 use {@link PrettyPrintWriter#PrettyPrintWriter(Writer, int, NameCoder)} instead
     */
    @Deprecated
    PrettyPrintWriter(final Writer writer, final int mode, final XmlFriendlyReplacer replacer) {
        this(writer, mode, new char[]{' ', ' '}, replacer);
    }

    /**
     * @since 1.4
     */
    PrettyPrintWriter(final Writer writer, final NameCoder nameCoder) {
        this(writer, XML_QUIRKS, new char[]{' ', ' '}, nameCoder);
    }

    /**
     * @deprecated As of 1.4 use {@link PrettyPrintWriter#PrettyPrintWriter(Writer, NameCoder)} instead.
     */
    @Deprecated
    PrettyPrintWriter(final Writer writer, final XmlFriendlyReplacer replacer) {
        this(writer, XML_QUIRKS, new char[]{' ', ' '}, replacer);
    }

    /**
     * @since 1.3
     */
    PrettyPrintWriter(final Writer writer, final int mode) {
        this(writer, mode, new char[]{' ', ' '});
    }

    PrettyPrintWriter(final Writer writer) {
        this(writer, new char[]{' ', ' '});
    }

    @Override
    public void startNode(final String name) {
        final String escapedName = encodeNode(name);
        tagIsEmpty = false;
        finishTag();
        writer.write('<');
        writer.write(escapedName);
        elementStack.push(escapedName);
        tagInProgress = true;
        depth++;
        readyForNewLine = true;
        tagIsEmpty = true;
    }

    @Override
    public void startNode(final String name, final Class clazz) {
        startNode(name);
    }

    @Override
    public void setValue(final String text) {
        readyForNewLine = false;
        tagIsEmpty = false;
        finishTag();

        writeText(writer, text);
    }

    @Override
    public void addAttribute(final String key, final String value) {
        writer.write(' ');
        writer.write(encodeAttribute(key));
        writer.write('=');
        writer.write('\"');
        writeAttributeValue(writer, value);
        writer.write('\"');
    }

    protected void writeAttributeValue(final QuickWriter writer, final String text) {
        writeText(text, true);
    }

    protected void writeText(final QuickWriter writer, final String text) {
        writeText(text, false);
    }

    private void writeText(final String text, final boolean isAttribute) {
        text.codePoints().forEach(c -> {
            switch (c) {
            case '\0':
                if (mode == XML_QUIRKS) {
                    writer.write(NULL);
                } else {
                    throw new StreamException("Invalid character 0x0 in XML stream");
                }
                break;
            case '&':
                writer.write(AMP);
                break;
            case '<':
                writer.write(LT);
                break;
            case '>':
                writer.write(GT);
                break;
            case '"':
                writer.write(QUOT);
                break;
            case '\'':
                writer.write(APOS);
                break;
            case '\r':
                writer.write(CR);
                break;
            case '\t':
            case '\n':
                if (!isAttribute) {
                    writer.write(Character.toChars(c));
                    break;
                }
                //$FALL-THROUGH$
            default:
                if (Character.isDefined(c) && !Character.isISOControl(c)) {
                    if (mode != XML_QUIRKS) {
                        if (c > '\ud7ff' && c < '\ue000') {
                            throw new StreamException("Invalid character 0x"
                                + Integer.toHexString(c)
                                + " in XML stream");
                        }
                    }
                    writer.write(Character.toChars(c));
                } else {
                    if (mode == XML_1_0) {
                        if (c < 9 || c == '\u000b' || c == '\u000c' || c == '\u000e' || c >= '\u000f' && c <= '\u001f') {
                            throw new StreamException("Invalid character 0x"
                                + Integer.toHexString(c)
                                + " in XML 1.0 stream");
                        }
                    }
                    if (mode != XML_QUIRKS) {
                        if (c == '\ufffe' || c == '\uffff') {
                            throw new StreamException("Invalid character 0x"
                                + Integer.toHexString(c)
                                + " in XML stream");
                        }
                    }
                    writer.write("&#x");
                    writer.write(Integer.toHexString(c));
                    writer.write(';');
                }
            }
        });
    }

    @Override
    public void endNode() {
        depth--;
        if (tagIsEmpty) {
            writer.write('/');
            readyForNewLine = false;
            finishTag();
            elementStack.popSilently();
        } else {
            finishTag();
            writer.write(CLOSE);
            writer.write((String) elementStack.pop());
            writer.write('>');
        }
        readyForNewLine = true;
        if (depth == 0) {
            writer.flush();
        }
    }

    private void finishTag() {
        if (tagInProgress) {
            writer.write('>');
        }
        tagInProgress = false;
        if (readyForNewLine) {
            endOfLine();
        }
        readyForNewLine = false;
        tagIsEmpty = false;
    }

    protected void endOfLine() {
        writer.write(getNewLine());
        for (int i = 0; i < depth; i++) {
            writer.write(lineIndenter);
        }
    }

    @Override
    public void flush() {
        writer.flush();
    }

    @Override
    public void close() {
        writer.close();
    }

    /**
     * Retrieve the line terminator. This method returns always a line feed, since according the XML specification any
     * parser must ignore a carriage return. Overload this method, if you need different behavior.
     *
     * @return the line terminator
     * @since 1.3
     */
    protected String getNewLine() {
        return "\n";
    }
}
