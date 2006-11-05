package hudson.util;

import java.io.OutputStream;

/**
 * @author Kohsuke Kawaguchi
 */
public final class NullStream extends OutputStream {
    public NullStream() {}

    public void write(byte b[]) {
    }

    public void write(byte b[], int off, int len) {
    }

    public void write(int b) {
    }
}
