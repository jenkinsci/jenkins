package hudson;

import java.io.FilterOutputStream;
import java.io.OutputStream;

/**
 * @author Kohsuke Kawaguchi
 */
public class CloseProofOutputStream extends FilterOutputStream {
    public CloseProofOutputStream(OutputStream out) {
        super(out);
    }

    public void close() {
    }
}
