package hudson.maven.agent;

/**
 * Thrown when {@link PluginManagerListener} returned false to orderly
 * abort the execution. The caller shouldn't dump the stack trace for
 * this exception.
 */
public final class AbortException extends RuntimeException {
    public AbortException(String message) {
        super(message);
    }
    public AbortException(String message, Exception e) {
        super(message, e);
    }
}
