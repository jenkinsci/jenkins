package hudson.remoting;

/**
 * Receives status notification from {@link Engine}.
 *
 * The callback will be invoked on a non-GUI thread.
 *
 * @author Kohsuke Kawaguchi
 */
public interface EngineListener {
    void status(String msg);
    void error(Throwable t);

    /**
     * Called when a connection is terminated.
     */
    void onDisconnect();
}
