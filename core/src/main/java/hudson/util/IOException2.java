package hudson.util;

import java.io.IOException;

/**
 * {@link IOException} with linked exception.
 *
 * @author Kohsuke Kawaguchi
 */
public class IOException2 extends IOException  {
    private final Throwable cause;

    public IOException2(Throwable cause) {
        super(cause.getMessage());
        this.cause = cause;
    }

    public IOException2(String s, Throwable cause) {
        super(s);
        this.cause = cause;
    }

    public Throwable getCause() {
        return cause;
    }
}
