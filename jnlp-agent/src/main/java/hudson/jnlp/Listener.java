package hudson.jnlp;

/**
 * Receives status notification from {@link Engine}.
 *
 * The callback will be invoked on a non-GUI thread.
 *
 * @author Kohsuke Kawaguchi
 */
public interface Listener {
    void status(String msg);
    void error(Throwable t);
}
