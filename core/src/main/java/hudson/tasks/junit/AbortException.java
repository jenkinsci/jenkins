package hudson.tasks.junit;

import java.io.IOException;

/**
 * Used to signal an orderly abort of the processing.
 */
class AbortException extends IOException {
    public AbortException(String msg) {
        super(msg);
    }

    private static final long serialVersionUID = 1L;
}
