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

import static org.junit.Assert.assertArrayEquals;

import java.util.Arrays;

import org.junit.Test;

/**
 * @author Kohsuke Kawaguchi
 */
public class QuotedStringTokenizerTest {

    @Test
    public void test1() {
        check("foo bar",
              "foo","bar");
    }

    @Test
    public void test2() {
        check("foo \"bar zot\"",
              "foo","bar zot");
    }

    @Test
    public void test3() {
        check("foo bar=\"quote zot\"",
              "foo","bar=quote zot");
    }

    @Test
    public void test4() {
        check("foo\\\"",
              "foo\"");
    }

    @Test
    public void test5() {
        check("foo\\ bar",
              "foo bar");
    }

    @Test
    public void test6() {
        check("foo\\\\ bar",
              "foo\\","bar");
    }

    // see http://www.nabble.com/Error-parsing-%22-in-msbuild-task-to20535754.html
    @Test
    public void test7() {
        check("foo=\"bar\\zot\"",
              "foo=bar\\zot");
    }

    private void check(String src, String... expected) {
        String[] r = QuotedStringTokenizer.tokenize(src);
        System.out.println(Arrays.asList(r));
        assertArrayEquals(expected, r);
    }
}
