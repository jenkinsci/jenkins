package hudson.node_monitors;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import hudson.model.Computer;
import hudson.model.ComputerSet;
import hudson.model.Slave;
import hudson.model.User;
import hudson.slaves.DumbSlave;
import hudson.slaves.OfflineCause;
import hudson.slaves.SlaveComputer;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.InboundAgentExtension;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

/**
 * @author Andrew Bayer
 */
@WithJenkins
class ResponseTimeMonitorTest {

    @RegisterExtension
    private final InboundAgentExtension inboundAgents = new InboundAgentExtension();

    private JenkinsRule j;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        j = rule;
    }

    /**
     * Makes sure that it doesn't try to monitor an already-offline agent.
     */
    @Test
    @Issue("JENKINS-20272")
    void skipOfflineAgent() throws Exception {
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

        // Retry to compensate for test being flaky in CI
        await().atMost(15, TimeUnit.SECONDS)
            .ignoreException(ExecutionException.class)
            .until(() -> {
                // Now reconnect and make sure we get a non-null response.
                c.connect(false).get(); // wait until it's connected
                return true;
            }
        );

        assertNotNull(ResponseTimeMonitor.DESCRIPTOR.monitor(c));
    }

    @Test
    void doNotDisconnectBeforeLaunched() throws Exception {
        Slave slave = inboundAgents.createAgent(j, InboundAgentExtension.Options.newBuilder().skipStart().build());
        Computer c = slave.toComputer();
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
