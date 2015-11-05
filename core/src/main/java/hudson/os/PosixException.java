package hudson.os;

import org.jruby.ext.posix.POSIX.ERRORS;

/**
 * Indicates an error during POSIX API call.
 * @see PosixAPI
 * @author Kohsuke Kawaguchi
 */
public class PosixException extends RuntimeException {
    private final ERRORS errors;

    public PosixException(String message, ERRORS errors) {
        super(message);
        this.errors = errors;
    }

    /** @deprecated Leaks reference to deprecated jna-posix API. */
    @Deprecated
    public ERRORS getErrorCode() {
        return errors;
    }

    @Override
    public String toString() {
        return super.toString()+" "+errors;
    }
}
