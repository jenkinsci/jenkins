package hudson.os;


/**
 * Indicates an error during POSIX API call.
 * @see PosixAPI
 * @author Kohsuke Kawaguchi
 */
@Deprecated
public class PosixException extends RuntimeException {

    public PosixException() {
    }


}
