package hudson.lifecycle;

/**
 * Indicates that the {@link Lifecycle} doesn't support restart.
 * 
 * @author Kohsuke Kawaguchi
 */
public class RestartNotSupportedException extends Exception {
    public RestartNotSupportedException(String reason) {
        super(reason);
    }

    public RestartNotSupportedException(String reason, Throwable cause) {
        super(reason, cause);
    }
}
