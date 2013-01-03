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
import java.util.Collections;
import static org.junit.Assert.*;
import org.netbeans.insane.scanner.CountingVisitor;
import org.netbeans.insane.scanner.ScannerUtils;

/**
 * Static utility methods for verifying heap memory usage.
 * Uses the <a href="http://performance.netbeans.org/insane/">INSANE library</a>
 * to traverse the heap from within your test.
 * <p>Object sizes are in an idealized JVM in which pointers are 4 bytes
 * (realistic even for modern 64-bit JVMs in which {@code -XX:+UseCompressedOops} is the default)
 * but objects are aligned on 8-byte boundaries (so dropping an {@code int} field does not always save memory).
 * <p>{@code import static org.jvnet.hudson.test.MemoryAssert.*;} to use.
 */
public class MemoryAssert {

    private MemoryAssert() {}

    /**
     * Verifies that an object and its transitive reference graph occupy at most a predetermined amount of memory.
     * The referents of {@link WeakReference} and the like are ignored.
     * <p>To use, run your test for the first time with {@code max} of {@code 0};
     * when it fails, use the reported actual size as your assertion maximum.
     * When improving memory usage, run again with {@code 0} and tighten the test to both demonstrate
     * your improvement quantitatively and prevent regressions.
     * @param o the object to measure
     * @param max the maximum desired memory usage (in bytes)
     */
    public static void assertHeapUsage(Object o, int max) throws Exception {
        CountingVisitor v = new CountingVisitor();
        ScannerUtils.scan(ScannerUtils.skipNonStrongReferencesFilter(), v, Collections.singleton(o), false);
        int memoryUsage = v.getTotalSize();
        assertTrue(o + " consumes " + memoryUsage + " bytes of heap, " + (memoryUsage - max) + " over the limit of " + max, memoryUsage <= max);
    }

}
