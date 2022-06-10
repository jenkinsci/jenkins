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

import hudson.remoting.ProxyException;
import java.util.AbstractList;
import java.util.List;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.SimpleFormatter;
import jenkins.util.JenkinsJVM;

/**
 * Log {@link Handler} that stores the log records into a ring buffer.
 *
 * @author Kohsuke Kawaguchi
 */
public class RingBufferLogHandler extends Handler {

    private static final int DEFAULT_RING_BUFFER_SIZE = Integer.getInteger(RingBufferLogHandler.class.getName() + ".defaultSize", 256);

    /**
     * Just to access {@link Formatter#formatMessage} which is not {@code static} though it could have been.
     */
    private static final Formatter dummyFormatter = new SimpleFormatter();

    private int start = 0;
    private final LogRecord[] records;
    private int size;

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
    public void publish(LogRecord record) {
        if (JenkinsJVM.isJenkinsJVM() && record.getParameters() != null) {
            try {
                LogRecord clone = new LogRecord(record.getLevel(), dummyFormatter.formatMessage(record));
                clone.setLoggerName(record.getLoggerName());
                clone.setMillis(record.getMillis());
                clone.setSequenceNumber(record.getSequenceNumber());
                clone.setSourceClassName(record.getSourceClassName());
                clone.setSourceMethodName(record.getSourceMethodName());
                clone.setThreadID(record.getThreadID());
                Throwable t = record.getThrown();
                if (t != null) {
                    clone.setThrown(new ProxyException(t));
                }
                record = clone;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        synchronized (this) {
            int len = records.length;
            records[(start + size) % len] = record;
            if (size == len) {
                start = (start + 1) % len;
            } else {
                size++;
            }
        }
    }

    public synchronized void clear() {
        size = 0;
        start = 0;
    }

    /**
     * Returns the list view of {@link LogRecord}s in the ring buffer.
     *
     * <p>
     * New records are always placed early in the list.
     */
    public List<LogRecord> getView() {
        // Since Jenkins.logRecords is a field used as an API, we are forced to implement a dynamic list.
        return new AbstractList<LogRecord>() {
            @Override
            public LogRecord get(int index) {
                // flip the order
                synchronized (RingBufferLogHandler.this) {
                    return records[(start + (size - (index + 1))) % records.length];
                }
            }

            @Override
            public int size() {
                synchronized (RingBufferLogHandler.this) {
                    // Not actually correct if a log record is added
                    // after this is called but before the list is iterated.
                    // However the size should only ever grow, up to the ring buffer max,
                    // so get(int) should never throw AIOOBE.
                    return size;
                }
            }
        };
    }

    // noop
    @Override
    public void flush() {}

    @Override
    public void close() throws SecurityException {}
}
