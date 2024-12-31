package hudson.node_monitors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import hudson.model.Computer;
import hudson.model.ComputerSet;
import hudson.model.Agent;
import hudson.model.User;
import hudson.agents.DumbAgent;
import hudson.agents.OfflineCause;
import hudson.agents.AgentComputer;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.InboundAgentRule;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;

/**
 * @author Andrew Bayer
 */
public class ResponseTimeMonitorTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Rule
    public InboundAgentRule inboundAgents = new InboundAgentRule();

    /**
     * Makes sure that it doesn't try to monitor an already-offline agent.
     */
    @Test
    @Issue("JENKINS-20272")
    public void skipOfflineAgent() throws Exception {
        DumbAgent s = j.createAgent();
        AgentComputer c = s.getComputer();
        c.connect(false).get(); // wait until it's connected

        // Try as temporarily offline first.
        c.setTemporarilyOffline(true, new OfflineCause.UserCause(User.getUnknown(), "Temporarily offline"));
        assertNotNull(ResponseTimeMonitor.DESCRIPTOR.monitor(c));

        // Now try as actually disconnected.
        c.setTemporarilyOffline(false, null);
        j.disconnectAgent(s);
        assertNull(ResponseTimeMonitor.DESCRIPTOR.monitor(c));

        // Now reconnect and make sure we get a non-null response.
        c.connect(false).get(); // wait until it's connected

        assertNotNull(ResponseTimeMonitor.DESCRIPTOR.monitor(c));
    }

    @Test
    public void doNotDisconnectBeforeLaunched() throws Exception {
        Agent agent = inboundAgents.createAgent(j, InboundAgentRule.Options.newBuilder().skipStart().build());
        Computer c = agent.toComputer();
        assertNotNull(c);
        OfflineCause originalOfflineCause = c.getOfflineCause();
        assertNotNull(originalOfflineCause);

        ResponseTimeMonitor rtm = ComputerSet.getMonitors().get(ResponseTimeMonitor.class);
        for (int i = 0; i < 10; i++) {
            rtm.triggerUpdate().join();
            System.out.println(rtm.getDescriptor().get(c));
            assertEquals(originalOfflineCause, c.getOfflineCause());
        }
    }
}
