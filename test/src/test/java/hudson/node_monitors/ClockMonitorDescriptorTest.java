package hudson.node_monitors;

import org.jvnet.hudson.test.HudsonTestCase;
import hudson.slaves.DumbSlave;
import hudson.slaves.SlaveComputer;
import hudson.util.ClockDifference;
import hudson.util.TimeUnit2;

/**
 * @author Richard Mortimer
 */
public class ClockMonitorDescriptorTest extends HudsonTestCase {
    /**
     * Makes sure that it returns sensible values.
     */
    public void testClockMonitor() throws Exception {
        DumbSlave s = createSlave();
        SlaveComputer c = s.getComputer();
        c.connect(false).get(); // wait until it's connected
        if(c.isOffline())
            fail("Slave failed to go online: "+c.getLog());

        ClockDifference cd = ClockMonitor.DESCRIPTOR.monitor(c);
        long diff = cd.diff;
        assertTrue(diff < TimeUnit2.SECONDS.toMillis(5));
        assertTrue(diff > TimeUnit2.SECONDS.toMillis(-5));
        assertTrue(cd.abs() >= 0);
        assertTrue(cd.abs() < TimeUnit2.SECONDS.toMillis(5));
        assertFalse(cd.isDangerous());
        assertTrue("html output too short", cd.toHtml().length() > 0);
    }
}
