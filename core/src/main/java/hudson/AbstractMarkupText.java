/*
 * The MIT License
 *
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi
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

import hudson.MarkupText.SubText;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Common part between {@link MarkupText} and {@link MarkupText.SubText}.
 *
 * <p>
 * See {@link MarkupText} for more discussion about what this class represents.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.250
 */
public abstract class AbstractMarkupText {
    /*package*/ AbstractMarkupText() {} // limit who can subclass this type.


    /**
     * Returns the plain text portion of this {@link MarkupText} without
     * any markup, nor any escape.
     */
    public abstract String getText();

    public char charAt(int idx) {
        return getText().charAt(idx);
    }

    /**
     * Length of the plain text.
     */
    public final int length() {
        return getText().length();
    }

    /**
     * Returns a subtext.
     *
     * @param end
     *      If negative, -N means "trim the last N-1 chars". That is, (s,-1) is the same as (s,length)
     */
    public abstract MarkupText.SubText subText(int start, int end);

    /**
     * Adds a start tag and end tag at the specified position.
     *
     * <p>
     * For example, if the text was "abc", then {@code addMarkup(1,2,"<b>","</b>")}
     * would generate {@code "a<b>b</b>c"}
     */
    public abstract void addMarkup(int startPos, int endPos, String startTag, String endTag);

    /**
     * Inserts an A tag that surrounds the given position.
     *
     * @since 1.349
     */
    public void addHyperlink(int startPos, int endPos, String url) {
        addMarkup(startPos, endPos, "<a href='" + Functions.htmlAttributeEscape(url) + "'>", "</a>");
    }

    /**
     * Inserts an A tag that surrounds the given position.
     * But this hyperlink is less visible.
     *
     * @since 1.395
     */
    public void addHyperlinkLowKey(int startPos, int endPos, String url) {
        addMarkup(startPos, endPos, "<a class='lowkey' href='" + Functions.htmlAttributeEscape(url) + "'>", "</a>");
    }

    /**
     * Hides the given text.
     */
    public void hide(int startPos, int endPos) {
        addMarkup(startPos, endPos, "<span style='display:none'>", "</span>");
    }

    /**
     * Adds a start tag and end tag around the entire text
     */
    public final void wrapBy(String startTag, String endTag) {
        addMarkup(0, length(), startTag, endTag);
    }

    /**
     * Find the first occurrence of the given pattern in this text, or null.
     *
     * @since 1.349
     */
    public MarkupText.SubText findToken(Pattern pattern) {
        String text = getText();
        Matcher m = pattern.matcher(text);

        if (m.find())
            return createSubText(m);

        return null;
    }

    /**
     * Find all "tokens" that match the given pattern in this text.
     *
     * <p>
     * A token is like a substring, except that it's aware of word boundaries.
     * For example, while "bc" is a string of "abc", calling {@code findTokens}
     * with "bc" as a pattern on string "abc" won't match anything.
     *
     * <p>
     * This method is convenient for finding keywords that follow a certain syntax
     * from natural text. You can then use {@link MarkupText.SubText#surroundWith(String,String)}
     * to put mark up around such text.
     */
    public List<MarkupText.SubText> findTokens(Pattern pattern) {
        String text = getText();
        Matcher m = pattern.matcher(text);
        List<SubText> r = new ArrayList<>();

        while (m.find()) {
            int idx = m.start();
            if (idx > 0) {
                char ch = text.charAt(idx - 1);
                if (Character.isLetter(ch) || Character.isDigit(ch))
                    continue;   // not at a word boundary
            }
            idx = m.end();
            if (idx < text.length()) {
                char ch = text.charAt(idx);
                if (Character.isLetter(ch) || Character.isDigit(ch))
                    continue;   // not at a word boundary
            }
            r.add(createSubText(m));
        }

        return r;
    }

    protected abstract SubText createSubText(Matcher m);
}
