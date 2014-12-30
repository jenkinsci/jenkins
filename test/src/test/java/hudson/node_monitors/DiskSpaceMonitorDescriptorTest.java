package hudson.node_monitors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Rule;
import org.junit.Test;
import hudson.slaves.DumbSlave;
import hudson.slaves.SlaveComputer;
import hudson.node_monitors.DiskSpaceMonitorDescriptor.DiskSpace;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.WithoutJenkins;

/**
 * @author Kohsuke Kawaguchi
 */
public class DiskSpaceMonitorDescriptorTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    /**
     * Makes sure that it returns some value.
     */
    @Test
    @Issue("JENKINS-3381")
    public void remoteDiskSpaceUsage() throws Exception {
        DumbSlave s = j.createSlave();
        SlaveComputer c = s.getComputer();
        c.connect(false).get(); // wait until it's connected
        if(c.isOffline())
            fail("Slave failed to go online: "+c.getLog());

        DiskSpace du = TemporarySpaceMonitor.DESCRIPTOR.monitor(c);
        du.toHtml();
        assertTrue(du.size>0);
    }

    @Test
    @WithoutJenkins
    public void parse() throws Exception {
        assertEquals(1,DiskSpace.parse("1").size);
        assertEquals(1024,DiskSpace.parse("1KB").size);
        assertEquals(1024,DiskSpace.parse("1K").size);
        assertEquals(1024,DiskSpace.parse("1kb").size);
        assertEquals(1024*1024,DiskSpace.parse("1MB").size);
        assertEquals(1024*1024*1024,DiskSpace.parse("1GB").size);
        assertEquals(512*1024*1024,DiskSpace.parse("0.5GB").size);
    }
}
