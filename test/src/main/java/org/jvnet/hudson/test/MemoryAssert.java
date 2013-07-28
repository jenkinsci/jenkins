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

import edu.umd.cs.findbugs.annotations.SuppressWarnings;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import jenkins.model.Jenkins;
import static org.junit.Assert.*;
import org.netbeans.insane.live.LiveReferences;
import org.netbeans.insane.scanner.CountingVisitor;
import org.netbeans.insane.scanner.Filter;
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
        // TODO could use ScannerUtils.recursiveSizeOf here
        CountingVisitor v = new CountingVisitor();
        ScannerUtils.scan(ScannerUtils.skipNonStrongReferencesFilter(), v, Collections.singleton(o), false);
        int memoryUsage = v.getTotalSize();
        assertTrue(o + " consumes " + memoryUsage + " bytes of heap, " + (memoryUsage - max) + " over the limit of " + max, memoryUsage <= max);
    }

    /**
     * @see #increasedMemory
     * @since 1.500
     */
    public static final class HistogramElement implements Comparable<HistogramElement> {
        public final String className;
        public final int instanceCount;
        public final int byteSize;
        HistogramElement(String className, int instanceCount, int byteSize) {
            this.className = className;
            this.instanceCount = instanceCount;
            this.byteSize = byteSize;
        }
        @Override public int compareTo(HistogramElement o) {
            int r = o.byteSize - byteSize;
            return r != 0 ? r : className.compareTo(o.className);
        }
        @Override public boolean equals(Object obj) {
            if (!(obj instanceof HistogramElement)) {
                return false;
            }
            HistogramElement o = (HistogramElement) obj;
            return o.className.equals(className);
        }
        @Override public int hashCode() {
            return className.hashCode();
        }
    }

    /**
     * Counts how much more memory is held in Jenkins by doing some operation.
     * @param callable an action
     * @param filters things to exclude
     * @return a histogram of the heap delta after running the operation
     * @since 1.500
     */
    public static List<HistogramElement> increasedMemory(Callable<Void> callable, Filter... filters) throws Exception {
        Filter f = ScannerUtils.skipNonStrongReferencesFilter();
        if (filters.length > 0) {
            Filter[] fs = new Filter[filters.length + 1];
            fs[0] = f;
            System.arraycopy(filters, 0, fs, 1, filters.length);
            f = ScannerUtils.compoundFilter(fs);
        }
        CountingVisitor v1 = new CountingVisitor();
        ScannerUtils.scan(f, v1, Collections.singleton(Jenkins.getInstance()), false);
        Set<Class<?>> old = v1.getClasses();
        callable.call();
        CountingVisitor v2 = new CountingVisitor();
        ScannerUtils.scan(f, v2, Collections.singleton(Jenkins.getInstance()), false);
        List<HistogramElement> elements = new ArrayList<HistogramElement>();
        for (Class<?> c : v2.getClasses()) {
            int delta = v2.getCountForClass(c) - (old.contains(c) ? v1.getCountForClass(c) : 0);
            if (delta > 0) {
                elements.add(new HistogramElement(c.getName(), delta, v2.getSizeForClass(c) - (old.contains(c) ? v1.getSizeForClass(c) : 0)));
            }
        }
        Collections.sort(elements);
        return elements;
    }

    /**
     * Forces GC by causing an OOM and then verifies the given {@link WeakReference} has been garbage collected.
     * @param reference object used to verify garbage collection.
     */
    @SuppressWarnings("DLS_DEAD_LOCAL_STORE_OF_NULL")
    public static void assertGC(WeakReference<?> reference) {
        assertTrue(true); reference.get(); // preload any needed classes!
        Set<Object[]> objects = new HashSet<Object[]>();
        while (true) {
            try {
                objects.add(new Object[1024]);
            } catch (OutOfMemoryError ignore) {
                break;
            }
        }
        objects = null;
        System.gc();
        Object obj = reference.get();
        if (obj != null) {
            fail(LiveReferences.fromRoots(Collections.singleton(obj)).toString());
        }
    }

}
