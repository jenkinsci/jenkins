package hudson.util;

import java.util.AbstractList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.LogRecord;

/**
 * Log {@link Handler} that stores the log records into a ring buffer.
 *
 * @author Kohsuke Kawaguchi
 */
public class RingBufferLogHandler extends Handler {

    private int start = 0;
    private final LogRecord[] records;
    private int size = 0;

    public RingBufferLogHandler() {
        this(256);
    }

    public RingBufferLogHandler(int ringSize) {
        records = new LogRecord[ringSize];
    }

    public synchronized void publish(LogRecord record) {
        int len = records.length;
        records[(start+size)%len]=record;
        if(size==len) {
            start++;
        } else {
            size++;
        }
    }

    /**
     * Returns the list view of {@link LogRecord}s in the ring buffer.
     *
     * <p>
     * New records are always placed early in the list.
     */
    public List<LogRecord> getView() {
        return new AbstractList<LogRecord>() {
            public LogRecord get(int index) {
                // flip the order
                return records[(start+(size-(index+1)))%records.length];
            }

            public int size() {
                return size;
            }
        };
    }

    // noop
    public void flush() {}
    public void close() throws SecurityException {}
}
