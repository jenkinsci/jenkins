package hudson;

import jenkins.model.Jenkins;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import java.lang.reflect.Field;

import static org.junit.Assert.assertNull;

public class UDPBroadcastThreadSEC1641Test {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Test
    public void ensureThereIsNoThreadRunningByDefault() throws Exception {
        UDPBroadcastThread thread = getPrivateThread(j.jenkins);
        assertNull(thread);
    }

    private static UDPBroadcastThread getPrivateThread(Jenkins jenkins) throws Exception {
        Field threadField = Jenkins.class.getDeclaredField("udpBroadcastThread");
        threadField.setAccessible(true);

        return (UDPBroadcastThread) threadField.get(jenkins);
    }
}
