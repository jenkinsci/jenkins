package hudson.util;

import java.io.IOException;
import java.io.OutputStream;

/**
 * @author Kohsuke Kawaguchi
 */
public class ForkOutputStream extends OutputStream {
    private final OutputStream lhs;
    private final OutputStream rhs;

    public ForkOutputStream(OutputStream lhs, OutputStream rhs) {
        this.lhs = lhs;
        this.rhs = rhs;
    }

    public void write(int b) throws IOException {
        lhs.write(b);
        rhs.write(b);
    }

    public void write(byte[] b) throws IOException {
        lhs.write(b);
        rhs.write(b);
    }

    public void write(byte[] b, int off, int len) throws IOException {
        lhs.write(b, off, len);
        rhs.write(b, off, len);
    }

    public void flush() throws IOException {
        lhs.flush();
        rhs.flush();
    }

    public void close() throws IOException {
        lhs.close();
        rhs.close();
    }
}
