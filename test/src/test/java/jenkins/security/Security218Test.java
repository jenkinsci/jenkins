package jenkins.security;

import hudson.model.Node.Mode;
import hudson.model.Slave;
import hudson.remoting.Channel;
import hudson.remoting.Which;
import hudson.slaves.DumbSlave;
import hudson.slaves.JNLPLauncher;
import hudson.slaves.RetentionStrategy;
import org.apache.tools.ant.util.JavaEnvUtils;
import org.codehaus.groovy.runtime.Security218;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;

import java.io.Serializable;
import java.util.Collections;

import static org.junit.Assert.*;

/**
 * @author Kohsuke Kawaguchi
 */
@Issue("SECURITY-218")
public class Security218Test implements Serializable {
    @Rule
    public transient JenkinsRule j = new JenkinsRule();

    /**
     * JNLP slave.
     */
    private transient Process jnlp;

    /**
     * Makes sure SECURITY-218 fix also applies to slaves.
     *
     * This test is for regular dumb slave
     */
    @Test
    public void dumbSlave() throws Exception {
        check(j.createOnlineSlave());
    }

    /**
     * Makes sure SECURITY-218 fix also applies to slaves.
     *
     * This test is for JNLP slave
     */
    @Test
    public void jnlpSlave() throws Exception {
        DumbSlave s = createJnlpSlave("test");
        launchJnlpSlave(s);
        check(s);
    }

    /**
     * The attack scenario here is that a master sends a normal command to a slave and a slave
     * inserts a malicious response.
     */
    @SuppressWarnings("ConstantConditions")
    private void check(DumbSlave s) throws Exception {
        try {
            s.getComputer().getChannel().call(new MasterToSlaveCallable<Object, RuntimeException>() {
                public Object call() {
                    return new Security218();
                }
            });
            fail("Expected the connection to die");
        } catch (SecurityException e) {
            assertTrue(e.getMessage().contains(Security218.class.getName()));
        }
    }

// TODO: reconcile this duplicate with JnlpAccessWithSecuredHudsonTest
    /**
     * Creates a new slave that needs to be launched via JNLP.
     *
     * @see #launchJnlpSlave(Slave)
     */
    public DumbSlave createJnlpSlave(String name) throws Exception {
        DumbSlave s = new DumbSlave(name, "", System.getProperty("java.io.tmpdir") + '/' + name, "2", Mode.NORMAL, "", new JNLPLauncher(), RetentionStrategy.INSTANCE, Collections.EMPTY_LIST);
        j.jenkins.addNode(s);
        return s;
    }

// TODO: reconcile this duplicate with JnlpAccessWithSecuredHudsonTest
    /**
     * Launch a JNLP slave created by {@link #createJnlpSlave(String)}
     */
    public Channel launchJnlpSlave(Slave slave) throws Exception {
        j.createWebClient().goTo("computer/"+slave.getNodeName()+"/slave-agent.jnlp?encrypt=true", "application/octet-stream");
        String secret = slave.getComputer().getJnlpMac();
        // To watch it fail: secret = secret.replace('1', '2');
        ProcessBuilder pb = new ProcessBuilder(JavaEnvUtils.getJreExecutable("java"),
                "-jar", Which.jarFile(hudson.remoting.Launcher.class).getAbsolutePath(),
                "-jnlpUrl", j.getURL() + "computer/"+slave.getNodeName()+"/slave-agent.jnlp", "-secret", secret);

        pb.inheritIO();
        System.err.println("Running: " + pb.command());

        jnlp = pb.start();

        for (int i = 0; i < /* one minute */600; i++) {
            if (slave.getComputer().isOnline()) {
                return slave.getComputer().getChannel();
            }
            Thread.sleep(100);
        }

        throw new AssertionError("JNLP slave agent failed to connect");
    }

    @After
    public void tearDown() {
        if (jnlp !=null)
            jnlp.destroy();
    }
}
