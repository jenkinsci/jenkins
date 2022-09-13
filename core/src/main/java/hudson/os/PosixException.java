package hudson.os;

import java.io.IOException;

/**
 * Indicates an error during POSIX API call.
 * @author Kohsuke Kawaguchi
 * @deprecated use {@link IOException}
 */
@Deprecated
public class PosixException extends RuntimeException {

    public PosixException(String message) {
        super(message);
    }

    public PosixException(String message, Throwable cause) {
        super(message, cause);
    }
}
