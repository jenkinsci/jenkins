package hudson.util;

/**
 * Indicates that Jenkins is interrupted during reload.
 *
 * @author Kohsuke Kawaguchi
 */
public class JenkinsReloadFailed extends ErrorObject {
    public final Throwable cause;

    public JenkinsReloadFailed(Throwable cause) {
        this.cause = cause;
    }
}
