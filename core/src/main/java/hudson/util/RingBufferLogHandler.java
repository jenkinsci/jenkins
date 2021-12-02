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

import java.util.AbstractList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Handler;
import java.util.logging.LogRecord;

/**
 * Log {@link Handler} that stores the log records into a ring buffer.
 *
 * @author Kohsuke Kawaguchi
 */
public class RingBufferLogHandler extends Handler {

    private static final int DEFAULT_RING_BUFFER_SIZE = Integer.getInteger(RingBufferLogHandler.class.getName() + ".defaultSize", 256);

    private int start = 0;
    private final LogRecord[] records;
    private AtomicInteger size = new AtomicInteger(0);

    /**
     * This constructor is deprecated. It can't access system properties with {@link jenkins.util.SystemProperties}
     * as it's not legal to use it on remoting agents.
     * @deprecated use {@link #RingBufferLogHandler(int)}
     */
    @Deprecated
    public RingBufferLogHandler() {
        this(DEFAULT_RING_BUFFER_SIZE);
    }

    public RingBufferLogHandler(int ringSize) {
        records = new LogRecord[ringSize];
    }

    /**
     * @return int DEFAULT_RING_BUFFER_SIZE
     * @see <a href="https://issues.jenkins.io/browse/JENKINS-50669">JENKINS-50669</a>
     * @since 2.259
     */
    public static int getDefaultRingBufferSize() {
        return DEFAULT_RING_BUFFER_SIZE;
    }

    @Override
    public synchronized void publish(LogRecord record) {
        int len = records.length;
        final int tempSize = size.get();
        records[(start+ tempSize)%len]=record;
        if(tempSize ==len) {
            start = (start+1)%len;
        } else {
            size.incrementAndGet();
        }
    }

    public synchronized void clear() {
        size.set(0);
        start = 0;
    }

    /**
     * Returns the list view of {@link LogRecord}s in the ring buffer.
     *
     * <p>
     * New records are always placed early in the list.
     */
    public List<LogRecord> getView() {
        return new AbstractList<LogRecord>() {
            @Override
            public LogRecord get(int index) {
                // flip the order
                synchronized (RingBufferLogHandler.this) {
                    return records[(start+(size.get()-(index+1)))%records.length];
                }
            }

            @Override
            public int size() {
                return size.get();
            }
        };
    }

    // noop
    @Override
    public void flush() {}
    @Override
    public void close() throws SecurityException {}
}
