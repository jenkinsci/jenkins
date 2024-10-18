package hudson.cli;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;

// TODO COPIED FROM hudson.remoting

/**
 * Filter input stream that records the content as it's read, so that it can be reported
 * in case of a catastrophic stream corruption problem.
 *
 * @author Kohsuke Kawaguchi
 */
class FlightRecorderInputStream extends InputStream {

    /**
     * Size (in bytes) of the flight recorder ring buffer used for debugging remoting issues.
     * @since 2.41
     */
    static final int BUFFER_SIZE = Integer.getInteger("hudson.remoting.FlightRecorderInputStream.BUFFER_SIZE", 1024);

    private final InputStream source;
    private ByteArrayRingBuffer recorder = new ByteArrayRingBuffer(BUFFER_SIZE);

    FlightRecorderInputStream(InputStream source) {
        this.source = source;
    }

    /**
     * Rewinds the record buffer and forget everything that was recorded.
     */
    public void clear() {
        recorder = new ByteArrayRingBuffer(BUFFER_SIZE);
    }

    /**
     * Gets the recorded content.
     */
    public byte[] getRecord() {
        return recorder.toByteArray();
    }

    /**
     * Creates a {@link DiagnosedStreamCorruptionException} based on the recorded content plus read ahead.
     * The caller is responsible for throwing the exception.
     */
    public DiagnosedStreamCorruptionException analyzeCrash(Exception problem, String diagnosisName) {
        final ByteArrayOutputStream readAhead = new ByteArrayOutputStream();
        final IOException[] error = new IOException[1];

        Thread diagnosisThread = new Thread(diagnosisName + " stream corruption diagnosis thread") {
            @Override
            public void run() {
                int b;
                try {
                    // not all InputStream will look for the thread interrupt flag, so check that explicitly to be defensive
                    while (!Thread.interrupted() && (b = source.read()) != -1) {
                        readAhead.write(b);
                    }
                } catch (IOException e) {
                    error[0] = e;
                }
            }
        };

        // wait up to 1 sec to grab as much data as possible
        diagnosisThread.start();
        try {
            diagnosisThread.join(1000);
        } catch (InterruptedException ignored) {
            // we are only waiting for a fixed amount of time, so we'll pretend like we were in a busy loop
            Thread.currentThread().interrupt();
            // fall through
        }

        IOException diagnosisProblem = error[0]; // capture the error, if any, before we kill the thread
        if (diagnosisThread.isAlive())
            diagnosisThread.interrupt();    // if it's not dead, kill

        return new DiagnosedStreamCorruptionException(problem, diagnosisProblem, getRecord(), readAhead.toByteArray());

    }

    @Override
    public int read() throws IOException {
        int i = source.read();
        if (i >= 0)
            recorder.write(i);
        return i;
    }

    @Override
    public int read(@NonNull byte[] b, int off, int len) throws IOException {
        len = source.read(b, off, len);
        if (len > 0)
            recorder.write(b, off, len);
        return len;
    }

    /**
     * To record the bytes we've skipped, convert the call to read.
     */
    @Override
    public long skip(long n) throws IOException {
        byte[] buf = new byte[(int) Math.min(n, 64 * 1024)];
        return read(buf, 0, buf.length);
    }

    @Override
    public int available() throws IOException {
        return source.available();
    }

    @Override
    public void close() throws IOException {
        source.close();
    }

    @Override
    public boolean markSupported() {
        return false;
    }

    // http://stackoverflow.com/a/3651696/12916
    private static class ByteArrayRingBuffer extends OutputStream {

        byte[] data;

        int capacity, pos = 0;

        boolean filled = false;

        ByteArrayRingBuffer(int capacity) {
            data = new byte[capacity];
            this.capacity = capacity;
        }

        @Override
        public synchronized void write(int b) {
            if (pos == capacity) {
                filled = true;
                pos = 0;
            }
            data[pos++] = (byte) b;
        }

        public synchronized byte[] toByteArray() {
            if (!filled) {
                return Arrays.copyOf(data, pos);
            }
            byte[] ret = new byte[capacity];
            System.arraycopy(data, pos, ret, 0, capacity - pos);
            System.arraycopy(data, 0, ret, capacity - pos, pos);
            return ret;
        }

        /** @author @roadrunner2 */
        @Override public synchronized void write(@NonNull byte[] buf, int off, int len) {
            // no point in trying to copy more than capacity; this also simplifies logic below
            if (len > capacity) {
                off += len - capacity;
                len = capacity;
            }

            // copy to buffer, but no farther than the end
            int num = Math.min(len, capacity - pos);
            if (num > 0) {
                System.arraycopy(buf, off, data, pos, num);
                off += num;
                len -= num;
                pos += num;
            }

            // wrap around if necessary
            if (pos == capacity) {
                filled = true;
                pos = 0;
            }

            // copy anything still left
            if (len > 0) {
                System.arraycopy(buf, off, data, pos, len);
                pos += len;
            }
        }

    }

}
