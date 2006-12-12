package hudson.remoting;

/**
 * Signals that the communication is aborted and thus
 * the pending {@link Request} will never recover its {@link Response}.
 *
 * @author Kohsuke Kawaguchi
 */
public class RequestAbortedException extends RuntimeException {
    public RequestAbortedException(Throwable cause) {
        super(cause);
    }
}
