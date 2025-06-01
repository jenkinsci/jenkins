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

import static org.junit.jupiter.api.Assertions.assertEquals;

import hudson.MarkupText.SubText;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * @author Kohsuke Kawaguchi
 */
class MarkupTextTest {

    @Test
    void test1() {
        MarkupText t = new MarkupText("I fixed issue #155. The rest is trick text: xissue #155 issue #123x");
        for (SubText st : t.findTokens(pattern)) {
            assertEquals(1, st.groupCount());
            st.surroundWith("<$1>", "<$1>");
        }

        assertEquals("I fixed <155>issue #155<155>. The rest is trick text: xissue #155 issue #123x", t.toString(false));
    }

    @Test
    void boundary() {
        MarkupText t = new MarkupText("issue #155---issue #123");
        for (SubText st : t.findTokens(pattern))
            st.surroundWith("<$1>", "<$1>");

        assertEquals("<155>issue #155<155>---<123>issue #123<123>", t.toString(false));
    }

    @Test
    void findTokensOnSubText() {
        MarkupText t = new MarkupText("Fixed 2 issues in this commit, fixing issue 155, 145");
        List<SubText> tokens = t.findTokens(Pattern.compile("issue .*"));
        assertEquals(1, tokens.size(), "Expected one token");
        assertEquals("issue 155, 145", tokens.get(0).group(0), "Expected single token was incorrect");
        for (SubText st : tokens.get(0).findTokens(Pattern.compile("([0-9]+)")))
            st.surroundWith("<$1>", "<$1>");

        assertEquals("Fixed 2 issues in this commit, fixing issue <155>155<155>, <145>145<145>", t.toString(false));
    }

    @Test
    void literalTextSurround() {
        MarkupText text = new MarkupText("AAA test AAA");
        for (SubText token : text.findTokens(Pattern.compile("AAA"))) {
            token.surroundWithLiteral("$9", "$9");
        }
        assertEquals("$9AAA$9 test $9AAA$9", text.toString(false));
    }

    /**
     * Start/end tag nesting should be correct regardless of the order tags are added.
     */
    @Test
    void addMarkupInOrder() {
        MarkupText text = new MarkupText("abcdef");
        text.addMarkup(0, 3, "$", "$");
        text.addMarkup(3, 6, "#", "#");
        assertEquals("$abc$#def#", text.toString(false));
    }

    @Test
    void addMarkupInReversedOrder() {
        MarkupText text = new MarkupText("abcdef");
        text.addMarkup(3, 6, "#", "#");
        text.addMarkup(0, 3, "$", "$");
        assertEquals("$abc$#def#", text.toString(false));
    }

    @Test
    void escape() {
        MarkupText text = new MarkupText("&&&");
        assertEquals("&amp;&amp;&amp;", text.toString(false));

        text.addMarkup(1, "<foo>");
        text.addMarkup(2, "&nbsp;");
        assertEquals("&amp;<foo>&amp;&nbsp;&amp;", text.toString(false));
    }

    @Test
    void preEscape() {
        MarkupText text = new MarkupText("Line\n2   & 3\n<End>\n");
        assertEquals("Line\n2   &amp; 3\n&lt;End&gt;\n", text.toString(true));
        text.addMarkup(4, "<hr/>");
        assertEquals("Line<hr/>\n2   &amp; 3\n&lt;End&gt;\n", text.toString(true));
    }

    /* @Issue("JENKINS-6252") */
    @Test
    void subTextSubText() {
        MarkupText text = new MarkupText("abcdefgh");
        SubText sub = text.subText(2, 7);
        assertEquals("cdefg", sub.getText());
        sub = sub.subText(1, 4);
        assertEquals("def", sub.getText());

        // test negative end
        sub = text.subText(2, -3);
        assertEquals("cdef", sub.getText());
        sub = sub.subText(1, -2);
        assertEquals("de", sub.getText());
    }

    private static final Pattern pattern = Pattern.compile("issue #([0-9]+)");
}
