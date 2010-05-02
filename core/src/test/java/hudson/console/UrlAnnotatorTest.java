/*
 * The MIT License
 * 
 * Copyright (c) 2004-2010, Sun Microsystems, Inc., Alan Harder
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
package hudson.console;

import hudson.MarkupText;
import junit.framework.TestCase;

/**
 * @author Alan Harder
 */
public class UrlAnnotatorTest extends TestCase {
    public void testAnnotate() {
        ConsoleAnnotator ca = new UrlAnnotator().newInstance(null);
        MarkupText text = new MarkupText("Hello <foo>http://foo/</foo> Bye");
        ca.annotate(null, text);
        assertEquals("Hello &lt;foo><a href='http://foo/'>http://foo/</a>&lt;/foo> Bye",
                     text.toString(true));
        text = new MarkupText("Hello [foo]http://foo/bar.txt[/foo] Bye");
        ca.annotate(null, text);
        assertEquals("Hello [foo]<a href='http://foo/bar.txt'>http://foo/bar.txt</a>[/foo] Bye",
                     text.toString(true));
        text = new MarkupText(
                "Hello 'http://foo' or \"ftp://bar\" or <https://baz/> or (http://a.b.c/x.y) Bye");
        ca.annotate(null, text);
        assertEquals("Hello '<a href='http://foo'>http://foo</a>' or \"<a href='ftp://bar'>"
                + "ftp://bar</a>\" or &lt;<a href='https://baz/'>https://baz/</a>> or (<a "
                + "href='http://a.b.c/x.y'>http://a.b.c/x.y</a>) Bye",
                text.toString(true));
        text = new MarkupText("Fake 'http://foo or \"ftp://bar or <https://baz/ or (http://a.b.c/x.y Bye");
        ca.annotate(null, text);
        assertEquals("Fake '<a href='http://foo'>http://foo</a> or \"<a href='ftp://bar'>"
                + "ftp://bar</a> or &lt;<a href='https://baz/'>https://baz/</a> or (<a "
                + "href='http://a.b.c/x.y'>http://a.b.c/x.y</a> Bye",
                text.toString(true));
    }
}
