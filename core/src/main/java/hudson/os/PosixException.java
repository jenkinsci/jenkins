package hudson.os;

import jnr.constants.platform.Errno;

/**
 * Indicates an error during POSIX API call.
 *
 * @author Kohsuke Kawaguchi
 */
public class PosixException extends RuntimeException {
    private final Errno error;

    public PosixException(String message, Errno error) {
        super(message);
        this.error = error;
    }

    public Errno getErrorCode() {
        return error;
    }

    @Override
    public String toString() {
        return super.toString()+" "+error;
    }
}
