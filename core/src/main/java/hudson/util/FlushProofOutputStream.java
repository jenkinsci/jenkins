package hudson.util;

import java.io.IOException;
import java.io.OutputStream;

/**
 * {@link OutputStream} that blocks {@link #flush()} method.
 * @author Kohsuke Kawaguchi
 * @since 1.349
 */
public class FlushProofOutputStream extends DelegatingOutputStream {
    public FlushProofOutputStream(OutputStream out) {
        super(out);
    }

    @Override
    public void flush() throws IOException {
    }
}

