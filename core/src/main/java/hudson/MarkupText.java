/*
 * The MIT License
 * 
 * Copyright (c) 2004-2010, Sun Microsystems, Inc., Kohsuke Kawaguchi
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package hudson;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Mutable representation of string with HTML mark up.
 *
 * <p>
 * This class is used to put mark up on plain text.
 * See {@code MarkupTextTest} for a typical usage and its result.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.70
 */
public class MarkupText extends AbstractMarkupText {
    private final String text;

    /**
     * Added mark up tags.
     */
    private final List<Tag> tags = new ArrayList<Tag>();

    /**
     * Represents one mark up inserted into text.
     */
    private static final class Tag implements Comparable<Tag> {
        /**
         * Char position of this tag in {@link MarkupText#text}.
         * This tag is placed in front of the character of this index.
         */
        private final int pos;
        private final String markup;


        public Tag(int pos, String markup) {
            this.pos = pos;
            this.markup = markup;
        }

        public int compareTo(Tag that) {
            return this.pos-that.pos;
        }
    }

    /**
     * Represents a substring of a {@link MarkupText}.
     */
    public final class SubText extends AbstractMarkupText {
        private final int start,end;
        private final int[] groups;

        public SubText(Matcher m, int textOffset) {
            start = m.start() + textOffset;
            end   = m.end() + textOffset;

            int cnt = m.groupCount();
            groups = new int[cnt*2];
            for( int i=0; i<cnt; i++ ) {
                groups[i*2  ] = m.start(i+1) + textOffset;
                groups[i*2+1] = m.end(i+1) + textOffset;
            }
        }

        public SubText(int start, int end) {
            this.start = start;
            this.end = end;
            groups = new int[0];
        }

        @Override
        public SubText subText(int start, int end) {
            return MarkupText.this.subText(this.start+start,
                    end<0 ? this.end+1+end : this.start+end);
        }

        @Override
        public String getText() {
            return text.substring(start,end);
        }

        @Override
        public void addMarkup(int startPos, int endPos, String startTag, String endTag) {
            MarkupText.this.addMarkup(startPos+start,  endPos+start, startTag, endTag);
        }

        /**
         * Surrounds this subtext with the specified start tag and the end tag.
         *
         * <p>
         * Start/end tag text can contain special tokens "$0", "$1", ...
         * and they will be replaced by their {@link #group(int) group match}.
         * "\$" can be used to escape characters.
         */
        public void surroundWith(String startTag, String endTag) {
            addMarkup(0,length(),replace(startTag),replace(endTag));
        }

        /**
         * Works like {@link #surroundWith(String, String)} except
         * that the token replacement is not performed on parameters.
         */
        public void surroundWithLiteral(String startTag, String endTag) {
            addMarkup(0,length(),startTag,endTag);
        }

        /**
         * Surrounds this subtext with &lt;a>...&lt;/a>. 
         */
        public void href(String url) {
            addHyperlink(0,length(),url);
        }

        /**
         * Gets the start index of the captured group within {@link MarkupText#getText()}.
         *
         * @param groupIndex
         *      0 means the start of the whole subtext. 1, 2, ... are
         *      groups captured by '(...)' in the regexp.
         */
        public int start(int groupIndex) {
            if(groupIndex==0)    return start;
            return groups[groupIndex*2-2];
        }

        /**
         * Gets the start index of this subtext within {@link MarkupText#getText()}.
         */
        public int start() {
            return start;
        }

        /**
         * Gets the end index of the captured group within {@link MarkupText#getText()}.
         */
        public int end(int groupIndex) {
            if(groupIndex==0)    return end;
            return groups[groupIndex*2-1];
        }

        /**
         * Gets the end index of this subtext within {@link MarkupText#getText()}.
         */
        public int end() {
            return end;
        }

        /**
         * Gets the text that represents the captured group.
         */
        public String group(int groupIndex) {
            if(start(groupIndex)==-1)
                return null;
            return text.substring(start(groupIndex),end(groupIndex));
        }

        /**
         * How many captured groups are in this subtext.
         * @since 1.357
         */
        public int groupCount() {
            return groups.length / 2;
        }

        /**
         * Replaces the group tokens like "$0", "$1", and etc with their actual matches.
         */
        public String replace(String s) {
            StringBuilder buf = new StringBuilder(s.length() + 10);

            for( int i=0; i<s.length(); i++) {
                char ch = s.charAt(i);
                if (ch == '\\') {// escape char
                    i++;
                    buf.append(s.charAt(i));
                } else if (ch == '$') {// replace by group
                    i++;

                    ch = s.charAt(i);
                    // get the group number
                    int groupId = ch - '0';
                    if (groupId < 0 || groupId > 9) {
                    	buf.append('$').append(ch);
                    } else {
                    	// add the group text
                    	String group = group(groupId);
                    	if (group != null) 
                    		buf.append(group);
                    }

                } else {
                    // other chars
                    buf.append(ch);
                }
            }

            return buf.toString();
        }

        @Override
        protected SubText createSubText(Matcher m) {
            return new SubText(m,start);
        }
    }

    /**
     *
     * @param text
     *      Plain text. This shouldn't include any markup nor escape. Those are done later in {@link #toString(boolean)}.
     */
    public MarkupText(String text) {
        this.text = text;
    }

    @Override
    public String getText() {
        return text;
    }

    /**
     * Returns a subtext.
     *
     * @param end
     *      If negative, -N means "trim the last N-1 chars". That is, (s,-1) is the same as (s,length)
     */
    public SubText subText(int start, int end) {
        return new SubText(start, end<0 ? text.length()+1+end : end);
    }

    @Override
    public void addMarkup( int startPos, int endPos, String startTag, String endTag ) {
        rangeCheck(startPos);
        rangeCheck(endPos);
        if(startPos>endPos) throw new IndexOutOfBoundsException();

        // when multiple tags are added to the same range, we want them to show up like
        // <b><i>abc</i></b>, not <b><i>abc</b></i>. Also, we'd like <b>abc</b><i>def</i>,
        // not <b>abc<i></b>def</i>. Do this by inserting them to different places.
        tags.add(new Tag(startPos, startTag));
        tags.add(0,new Tag(endPos,endTag));
    }

    public void addMarkup(int pos, String tag) {
        rangeCheck(pos);
        tags.add(new Tag(pos,tag));
    }

    private void rangeCheck(int pos) {
        if(pos<0 || pos>text.length())
            throw new IndexOutOfBoundsException();
    }

    /**
     * Returns the fully marked-up text.
     *
     * @deprecated as of 1.350.
     *      Use {@link #toString(boolean)} to be explicit about the escape mode.
     */
    @Override
    @Deprecated
    public String toString() {
        return toString(false);
    }

    /**
     * Returns the fully marked-up text.
     *
     * @param preEscape
     *      If true, the escaping is for the &lt;PRE> context. This leave SP and CR/LF intact.
     *      If false, the escape is for the normal HTML, thus SP becomes &amp;nbsp; and CR/LF becomes &lt;BR>
     */
    public String toString(boolean preEscape) {
        if(tags.isEmpty())
            return preEscape? Util.xmlEscape(text) : Util.escape(text);  // the most common case

        Collections.sort(tags);

        StringBuilder buf = new StringBuilder();
        int copied = 0; // # of chars already copied from text to buf

        for (Tag tag : tags) {
            if (copied<tag.pos) {
                String portion = text.substring(copied, tag.pos);
                buf.append(preEscape ? Util.xmlEscape(portion) : Util.escape(portion));
                copied = tag.pos;
            }
            buf.append(tag.markup);
        }
        if (copied<text.length()) {
            String portion = text.substring(copied, text.length());
            buf.append(preEscape ? Util.xmlEscape(portion) : Util.escape(portion));
        }

        return buf.toString();
    }

    // perhaps this method doesn't need to be here to remain binary compatible with past versions,
    // but having this seems to be safer.
    @Override
    public List<SubText> findTokens(Pattern pattern) {
        return super.findTokens(pattern);
    }

    @Override
    protected SubText createSubText(Matcher m) {
        return new SubText(m,0);
    }
}
