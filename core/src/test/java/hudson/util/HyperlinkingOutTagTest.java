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

package hudson.util;

import java.io.StringWriter;
import junit.framework.TestCase;
import org.apache.commons.jelly.XMLOutput;
import org.apache.commons.jelly.expression.ConstantExpression;

public class HyperlinkingOutTagTest extends TestCase {

    public HyperlinkingOutTagTest(String name) {
        super(name);
    }
    
    public void testPlain() throws Exception {
        assertOutput("plain text", "plain text");
    }

    public void testOnItsOwnLine() throws Exception {
        assertOutput("line #1\nhttp://nowhere.net/\nline #2\n",
                "line #1\n<a href=\"http://nowhere.net/\">http://nowhere.net/</a>\nline #2\n");
    }

    public void testInline() throws Exception {
        assertOutput("failed; see http://nowhere.net/",
                "failed; see <a href=\"http://nowhere.net/\">http://nowhere.net/</a>");
        assertOutput("failed (see http://nowhere.net/)",
                "failed (see <a href=\"http://nowhere.net/\">http://nowhere.net/</a>)");
    }

    public void testMultipleLinks() throws Exception {
        assertOutput("http://nowhere.net/ - failed: http://elsewhere.net/",
                "<a href=\"http://nowhere.net/\">http://nowhere.net/</a> - failed: " +
                "<a href=\"http://elsewhere.net/\">http://elsewhere.net/</a>");
    }

    public void testHttps() throws Exception {
        assertOutput("https://nowhere.net/",
                "<a href=\"https://nowhere.net/\">https://nowhere.net/</a>");
    }

    public void testMushedUp() throws Exception {
        assertOutput("stuffhttp://nowhere.net/", "stuffhttp://nowhere.net/");
    }

    public void testEscapedMetaChars() throws Exception {
        assertOutput("a < b && c < d", "a &lt; b &amp;&amp; c &lt; d");
    }

    public void testEscapedMetaCharsWithLinks() throws Exception {
        assertOutput("see <http://nowhere.net/>",
                "see &lt;<a href=\"http://nowhere.net/\">http://nowhere.net/</a>>");
        assertOutput("http://google.com/?q=stuff&lang=en",
                "<a href=\"http://google.com/?q=stuff&amp;lang=en\">http://google.com/?q=stuff&amp;lang=en</a>");
    }

    public void testPortNumber() throws Exception {
        assertOutput("http://localhost:8080/stuff/",
                "<a href=\"http://localhost:8080/stuff/\">http://localhost:8080/stuff/</a>");
    }

    private void assertOutput(String in, String out) throws Exception {
        StringWriter w = new StringWriter();
        XMLOutput xmlo = XMLOutput.createXMLOutput(w);
        HyperlinkingOutTag tag = new HyperlinkingOutTag();
        tag.setValue(new ConstantExpression(in));
        tag.doTag(xmlo);
        assertEquals(out, w.toString());
    }

}
