package hudson;

import hudson.util.DelegatingOutputStream;

import java.io.OutputStream;

/**
 * {@link OutputStream} that blocks {@link #close()} method.
 * @author Kohsuke Kawaguchi
 */
public class CloseProofOutputStream extends DelegatingOutputStream {
    public CloseProofOutputStream(OutputStream out) {
        super(out);
    }

    public void close() {
    }
}
