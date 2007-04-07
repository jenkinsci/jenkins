package hudson.maven;

import java.io.IOException;

/**
 * Signals a failure where the error message was reported.
 * The stack trace shouldn't be printed.
 * 
 * @author Kohsuke Kawaguchi
*/
class AbortException extends IOException {
    private static final long serialVersionUID = 1L;
}
