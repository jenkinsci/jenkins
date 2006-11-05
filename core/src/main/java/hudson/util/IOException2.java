package hudson.util;

import java.io.IOException;

/**
 * {@link IOException} with linked exception.
 *
 * @author Kohsuke Kawaguchi
 */
public class IOException2 extends IOException  {
    private final Exception cause;

    public IOException2(Exception cause) {
        super(cause.getMessage());
        this.cause = cause;
    }

    public IOException2(String s, Exception cause) {
        super(s);
        this.cause = cause;
    }

    public Throwable getCause() {
        return cause;
    }
}
