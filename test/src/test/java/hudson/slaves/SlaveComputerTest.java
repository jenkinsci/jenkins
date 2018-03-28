package hudson.slaves;

import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

public class SlaveComputerTest {
    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Test
    public void testGetAbsoluteRemotePath() throws Exception {
        DumbSlave slave = j.createSlave();
        SlaveComputer computer = slave.getComputer();
        String path = computer.getAbsoluteRemotePath();

        assert(path == null);
    }
}