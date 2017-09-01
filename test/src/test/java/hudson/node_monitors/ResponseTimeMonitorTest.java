package hudson.node_monitors;

import hudson.model.User;
import hudson.slaves.DumbSlave;
import hudson.slaves.OfflineCause;
import hudson.slaves.SlaveComputer;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

/**
 * @author Andrew Bayer
 */
public class ResponseTimeMonitorTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    /**
     * Makes sure that it doesn't try to monitor an already-offline agent.
     */
    @Test
    @Issue("JENKINS-20272")
    public void skipOfflineAgent() throws Exception {
        DumbSlave s = j.createSlave();
        SlaveComputer c = s.getComputer();
        c.connect(false).get(); // wait until it's connected

        // Try as temporarily offline first.
        c.setTemporarilyOffline(true, new OfflineCause.UserCause(User.getUnknown(), "Temporarily offline"));
        assertNotNull(ResponseTimeMonitor.DESCRIPTOR.monitor(c));

        // Now try as actually disconnected.
        c.setTemporarilyOffline(false, null);
        j.disconnectSlave(s);
        assertNull(ResponseTimeMonitor.DESCRIPTOR.monitor(c));

        // Now reconnect and make sure we get a non-null response.
        c.connect(false).get(); // wait until it's connected

        assertNotNull(ResponseTimeMonitor.DESCRIPTOR.monitor(c));
    }

}
