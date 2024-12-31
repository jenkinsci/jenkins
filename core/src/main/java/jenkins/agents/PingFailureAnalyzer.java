package jenkins.agents;

import hudson.ExtensionList;
import hudson.ExtensionPoint;
import hudson.remoting.Channel;
import java.io.IOException;
import jenkins.model.Jenkins;

/**
 * Get notified when a channel triggered a ping failure, but before the channel is killed.
 *
 * <p>
 * This provides the opportunity to perform diagnostic activities.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.592
 */
public abstract class PingFailureAnalyzer implements ExtensionPoint {
    /**
     *
     * @param c
     *      The channel that caused the ping failure. Because this channel is in a troubled state,
     *      do not attempt a remote call on this channel. Doing so would risk creating a hang.
     * @param cause
     *      Cause of the ping failure. Informational, and probably uninteresting to most callees.
     */
    public abstract void onPingFailure(Channel c, Throwable cause) throws IOException;

    public static ExtensionList<PingFailureAnalyzer> all() {
        return Jenkins.get().getExtensionList(PingFailureAnalyzer.class);
    }
}
