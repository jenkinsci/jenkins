package hudson.node_monitors;

import org.jvnet.hudson.test.Bug;
import org.jvnet.hudson.test.HudsonTestCase;
import hudson.slaves.DumbSlave;
import hudson.slaves.SlaveComputer;
import hudson.node_monitors.DiskSpaceMonitorDescriptor.DiskSpace;

/**
 * @author Kohsuke Kawaguchi
 */
public class DiskSpaceMonitorDescriptorTest extends HudsonTestCase {
    /**
     * Makes sure that it returns some value.
     */
    @Bug(3381)
    public void testRemoteDiskSpaceUsage() throws Exception {
        DumbSlave s = createSlave();
        SlaveComputer c = s.getComputer();
        c.connect(false).get(); // wait until it's connected
        if(c.isOffline())
            fail("Slave failed to go online: "+c.getLog());

        DiskSpace du = TemporarySpaceMonitor.DESCRIPTOR.monitor(c);
        du.toHtml();
        assertTrue(du.size>0);
    }
}
