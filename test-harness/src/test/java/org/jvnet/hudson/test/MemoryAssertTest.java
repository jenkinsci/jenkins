/*
 * The MIT License
 *
 * Copyright 2012 Jesse Glick.
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

package org.jvnet.hudson.test;

import java.lang.ref.WeakReference;
import static org.jvnet.hudson.test.MemoryAssert.*;
import static org.junit.Assert.*;
import org.junit.Test;

public class MemoryAssertTest {

    @Test public void heapUsage() throws Exception {
        Object[] biggie = new Object[1000];
        assertHeapUsage(biggie, 4016);
        assertHeapUsage(new WeakReference<Object>(biggie), 56);
        assertHeapUsage("hello world", 64);
        AssertionError e = null;
        try {
            assertHeapUsage(biggie, 1016);
        } catch (AssertionError _e) {
            e = _e;
        }
        assertNotNull(e);
        assertTrue(e.toString(), e.getMessage().contains("3000"));
    }

}
