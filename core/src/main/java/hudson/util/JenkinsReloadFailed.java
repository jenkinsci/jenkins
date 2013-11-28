package hudson.util;

import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

/**
 * Indicates that Jenkins is interrupted during reload.
 *
 * @author Kohsuke Kawaguchi
 */
public class JenkinsReloadFailed extends BootFailure {
    @Restricted(NoExternalUse.class) @Deprecated
    public final Throwable cause;

    public JenkinsReloadFailed(Throwable cause) {
        super(cause);
        this.cause = cause;
    }
}
