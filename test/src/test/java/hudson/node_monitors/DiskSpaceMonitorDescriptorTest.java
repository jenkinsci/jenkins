package hudson.node_monitors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import hudson.node_monitors.DiskSpaceMonitorDescriptor.DiskSpace;
import hudson.slaves.DumbSlave;
import hudson.slaves.SlaveComputer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.WithoutJenkins;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

/**
 * @author Kohsuke Kawaguchi
 */
@WithJenkins
class DiskSpaceMonitorDescriptorTest {

    private JenkinsRule j;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        j = rule;
    }

    /**
     * Makes sure that it returns some value.
     */
    @Test
    @Issue("JENKINS-3381")
    void remoteDiskSpaceUsage() throws Exception {
        DumbSlave s = j.createSlave();
        SlaveComputer c = s.getComputer();
        c.connect(false).get(); // wait until it's connected
        if (c.isOffline())
            fail("Slave failed to go online: " + c.getLog());

        DiskSpace du = TemporarySpaceMonitor.DESCRIPTOR.monitor(c);
        du.toHtml();
        assertTrue(du.size > 0);
    }

    @Test
    @WithoutJenkins
    void parse() throws Exception {
        assertEquals(1, DiskSpace.parse("1").size);
        assertEquals(1024, DiskSpace.parse("1KB").size);
        assertEquals(1024, DiskSpace.parse("1K").size);
        assertEquals(1024, DiskSpace.parse("1kb").size);
        assertEquals(1024, DiskSpace.parse("1kib").size);
        assertEquals(1024 * 1024, DiskSpace.parse("1MB").size);
        assertEquals(1024 * 1024, DiskSpace.parse("1MIB").size);
        assertEquals(1024 * 1024 * 1024, DiskSpace.parse("1GB").size);
        assertEquals(512 * 1024 * 1024, DiskSpace.parse("0.5GB").size);
    }

    @Test
    @WithoutJenkins
    @Issue("JENKINS-59383")
    void string() {
        DiskSpace du = new DiskSpace("/tmp", 123 * 1024 * 1024);
        du.setTotalSize(300 * 1024 * 1024);
        du.setThreshold(100 * 1024 * 1024);
        assertEquals("123.00 MiB out of 300.00 MiB left on /tmp.", du.toString());
        du.setThreshold(200 * 1024 * 1024);
        assertEquals("Disk space is below threshold of 200.00 MiB. Only 123.00 MiB out of 300.00 MiB left on /tmp.", du.toString());
    }
}
