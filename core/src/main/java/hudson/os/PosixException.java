package hudson.os;

/**
 * Indicates an error during POSIX API call.
 * @author Kohsuke Kawaguchi
 */
public class PosixException extends RuntimeException {

    public PosixException(String message) {
        super(message);
    }

    public PosixException(String message, Throwable cause) {
        super(message, cause);
    }
}
