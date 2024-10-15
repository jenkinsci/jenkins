package hudson.node_monitors;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import hudson.slaves.DumbSlave;
import hudson.slaves.SlaveComputer;
import hudson.util.ClockDifference;
import java.util.concurrent.TimeUnit;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

/**
 * @author Richard Mortimer
 */
public class ClockMonitorDescriptorTest {

    @Rule
    public JenkinsRule jenkins = new  JenkinsRule();

    /**
     * Makes sure that it returns sensible values.
     */
    @Test
    public void testClockMonitor() throws Exception {
        DumbSlave s = jenkins.createOnlineSlave();
        SlaveComputer c = s.getComputer();
        if (c.isOffline())
            fail("Slave failed to go online: " + c.getLog());

        ClockDifference cd = ClockMonitor.DESCRIPTOR.monitor(c);
        long diff = cd.diff;
        assertTrue(diff < TimeUnit.SECONDS.toMillis(5));
        assertTrue(diff > TimeUnit.SECONDS.toMillis(-5));
        assertTrue(cd.abs() >= 0);
        assertTrue(cd.abs() < TimeUnit.SECONDS.toMillis(5));
        assertFalse(cd.isDangerous());
        assertFalse("html output too short", cd.toHtml().isEmpty());
    }
}
