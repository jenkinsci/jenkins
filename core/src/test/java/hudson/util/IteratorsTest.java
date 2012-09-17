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

import junit.framework.TestCase;

import java.util.List;

/**
 * @author Kohsuke Kawaguchi
 */
public class IteratorsTest extends TestCase {
    public void testReverseSequence() {
        List<Integer> lst = Iterators.reverseSequence(1,4);
        assertEquals(3,(int)lst.get(0));
        assertEquals(2,(int)lst.get(1));
        assertEquals(1,(int)lst.get(2));
        assertEquals(3,lst.size());
    }

    public void testSequence() {
        List<Integer> lst = Iterators.sequence(1,4);
        assertEquals(1,(int)lst.get(0));
        assertEquals(2,(int)lst.get(1));
        assertEquals(3,(int)lst.get(2));
        assertEquals(3,lst.size());
    }
}
