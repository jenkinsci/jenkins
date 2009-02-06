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

import junit.framework.TestCase;
import hudson.MarkupText.SubText;

import java.util.List;
import java.util.regex.Pattern;
import java.net.URL;
import java.net.MalformedURLException;

/**
 * @author Kohsuke Kawaguchi
 */
public class MarkupTextTest extends TestCase {
    public void test1() {
        MarkupText t = new MarkupText("I fixed issue #155. The rest is trick text: xissue #155 issue #123x");
        for (SubText st : t.findTokens(pattern))
            st.surroundWith("<$1>","<$1>");

        assertEquals("I fixed <155>issue #155<155>. The rest is trick text: xissue #155 issue #123x", t.toString());
    }

    public void testBoundary() {
        MarkupText t = new MarkupText("issue #155---issue #123");
        for (SubText st : t.findTokens(pattern))
            st.surroundWith("<$1>","<$1>");

        assertEquals("<155>issue #155<155>---<123>issue #123<123>", t.toString());
    }

    public void testFindTokensOnSubText() {
        MarkupText t = new MarkupText("Fixed 2 issues in this commit, fixing issue 155, 145");
        List<SubText> tokens = t.findTokens(Pattern.compile("issue .*"));
        assertEquals("Expected one token", 1, tokens.size());
        assertEquals("Expected single token was incorrect", "issue 155, 145", tokens.get(0).group(0));
        for (SubText st : tokens.get(0).findTokens(Pattern.compile("([0-9]+)")))
            st.surroundWith("<$1>","<$1>");

        assertEquals("Fixed 2 issues in this commit, fixing issue <155>155<155>, <145>145<145>", t.toString());
    }

    public void testLiteralTextSurround() {
        MarkupText text = new MarkupText("AAA test AAA");
        for(SubText token : text.findTokens(Pattern.compile("AAA"))) {
            token.surroundWithLiteral("$9","$9");
        }
        assertEquals("$9AAA$9 test $9AAA$9",text.toString());
    }

    private static final Pattern pattern = Pattern.compile("issue #([0-9]+)");
}
