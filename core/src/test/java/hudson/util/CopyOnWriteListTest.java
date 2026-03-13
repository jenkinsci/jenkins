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

package hudson.util;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.xmlunit.matchers.CompareMatcher.isSimilarTo;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.xmlunit.diff.DefaultNodeMatcher;
import org.xmlunit.diff.ElementSelectors;

/**
 * @author Kohsuke Kawaguchi, Alan Harder
 */
class CopyOnWriteListTest {

    public static final class TestData {
        CopyOnWriteList list1 = new CopyOnWriteList();
        List list2 = new ArrayList();
    }

    /**
     * Verify that the serialization form of List and CopyOnWriteList are the same.
     */
    @Test
    void serialization() {
        XStream2 xs = new XStream2();
        TestData td = new TestData();

        String out = xs.toXML(td);
        String expected = "<hudson.util.CopyOnWriteListTest_-TestData>"
                + "<list1/><list2/></hudson.util.CopyOnWriteListTest_-TestData>";
        assertThat(out, isSimilarTo(expected).ignoreWhitespace()
                .withNodeMatcher(new DefaultNodeMatcher(ElementSelectors.byNameAndText)));


        TestData td2 = (TestData) xs.fromXML(out);
        assertTrue(td2.list1.isEmpty());
        assertTrue(td2.list2.isEmpty());

        td.list1.add("foobar1");
        td.list2.add("foobar2");
        out = xs.toXML(td);
        expected = "<hudson.util.CopyOnWriteListTest_-TestData>"
                + "<list1><string>foobar1</string></list1><list2><string>foobar2"
                + "</string></list2></hudson.util.CopyOnWriteListTest_-TestData>";
        assertThat(out, isSimilarTo(expected).ignoreWhitespace()
                .withNodeMatcher(new DefaultNodeMatcher(ElementSelectors.byNameAndText)));
        td2 = (TestData) xs.fromXML(out);
        assertEquals("foobar1", td2.list1.getView().getFirst());
        assertEquals("foobar2", td2.list2.getFirst());
    }
}
