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

    public void testParse() throws Exception {
        assertEquals(1,DiskSpace.parse("1").size);
        assertEquals(1024,DiskSpace.parse("1KB").size);
        assertEquals(1024,DiskSpace.parse("1K").size);
        assertEquals(1024,DiskSpace.parse("1kb").size);
        assertEquals(1024*1024,DiskSpace.parse("1MB").size);
        assertEquals(1024*1024*1024,DiskSpace.parse("1GB").size);
        assertEquals(512*1024*1024,DiskSpace.parse("0.5GB").size);
    }
}
