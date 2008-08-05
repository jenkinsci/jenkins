package hudson;

import java.io.IOException;

/**
 * Signals a failure where the error message was reported.
 * When this exception is caughted,
 * the stack trace will not be printed, and the build will be marked as a failure.
 *
 * @author Kohsuke Kawaguchi
*/
public final class AbortException extends IOException {
    private static final long serialVersionUID = 1L;
}
