package hudson.util;

import java.io.FilterOutputStream;
import java.io.OutputStream;
import java.io.IOException;

/**
 * Works like {@link FilterOutputStream} except its performance problem.
 *
 * @author Kohsuke Kawaguchi
 */
public abstract class DelegatingOutputStream extends OutputStream {
    protected OutputStream out;

    protected DelegatingOutputStream(OutputStream out) {
        this.out = out;
    }

    public void write(int b) throws IOException {
        out.write(b);
    }

    public void write(byte[] b) throws IOException {
        out.write(b);
    }

    public void write(byte[] b, int off, int len) throws IOException {
        out.write(b, off, len);
    }

    public void flush() throws IOException {
        out.flush();
    }

    public void close() throws IOException {
        out.close();
    }
}
