package jenkins.security;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.assertThrows;

import hudson.model.Node.Mode;
import hudson.model.Slave;
import hudson.remoting.Channel;
import hudson.slaves.DumbSlave;
import hudson.slaves.JNLPLauncher;
import hudson.slaves.RetentionStrategy;
import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.Collections;
import java.util.logging.Level;
import org.apache.commons.io.FileUtils;
import org.apache.tools.ant.util.JavaEnvUtils;
import org.codehaus.groovy.runtime.MethodClosure;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.LoggerRule;

/**
 * @author Kohsuke Kawaguchi
 */
@Issue("SECURITY-218")
public class Security218Test implements Serializable {
    @Rule
    public transient JenkinsRule j = new JenkinsRule();

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @Rule
    public LoggerRule logging = new LoggerRule().record(ClassFilterImpl.class, Level.FINE);

    /**
     * JNLP agent.
     */
    private transient Process jnlp;

    /**
     * Makes sure SECURITY-218 fix also applies to agents.
     *
     * This test is for regular static agent
     */
    @Test
    public void dumbSlave() throws Exception {
        check(j.createOnlineSlave());
    }

    /**
     * Makes sure SECURITY-218 fix also applies to agents.
     *
     * This test is for JNLP agent
     */
    @Test
    public void jnlpSlave() throws Exception {
        DumbSlave a = createJnlpSlave("test");
        launchJnlpSlave(a);
        check(a);
    }

    /**
     * The attack scenario here is that the controller sends a normal command to an agent and it
     * returns a malicious response.
     */
    @SuppressWarnings("ConstantConditions")
    private void check(DumbSlave s) {
        IOException e = assertThrows(
                "Expected the connection to die",
                IOException.class,
                () -> s.getComputer().getChannel().call(new EvilReturnValue()));
        assertThat(e.getMessage(), containsString(MethodClosure.class.getName()));
    }

    private static class EvilReturnValue extends MasterToSlaveCallable<Object, RuntimeException> {
        @Override
        public Object call() {
            return new MethodClosure("oops", "trim");
        }
    }

// TODO: reconcile this duplicate with JnlpAccessWithSecuredHudsonTest
    /**
     * Creates a new agent that needs to be launched via JNLP.
     *
     * @see #launchJnlpSlave(Slave)
     */
    public DumbSlave createJnlpSlave(String name) throws Exception {
        DumbSlave s = new DumbSlave(name, "", System.getProperty("java.io.tmpdir") + '/' + name, "2", Mode.NORMAL, "", new JNLPLauncher(true), RetentionStrategy.INSTANCE, Collections.EMPTY_LIST);
        j.jenkins.addNode(s);
        return s;
    }

// TODO: reconcile this duplicate with JnlpAccessWithSecuredHudsonTest
    /**
     * Launch a JNLP agent created by {@link #createJnlpSlave(String)}
     */
    public Channel launchJnlpSlave(Slave slave) throws Exception {
        j.createWebClient().goTo("computer/" + slave.getNodeName() + "/jenkins-agent.jnlp?encrypt=true", "application/octet-stream");
        String secret = slave.getComputer().getJnlpMac();
        File slaveJar = tmp.newFile();
        FileUtils.copyURLToFile(new Slave.JnlpJar("agent.jar").getURL(), slaveJar);
        // To watch it fail: secret = secret.replace('1', '2');
        ProcessBuilder pb = new ProcessBuilder(JavaEnvUtils.getJreExecutable("java"),
                "-jar", slaveJar.getAbsolutePath(),
                "-jnlpUrl", j.getURL() + "computer/" + slave.getNodeName() + "/jenkins-agent.jnlp", "-secret", secret);

        pb.inheritIO();
        System.err.println("Running: " + pb.command());

        jnlp = pb.start();

        for (int i = 0; i < /* one minute */600; i++) {
            if (slave.getComputer().isOnline()) {
                return slave.getComputer().getChannel();
            }
            Thread.sleep(100);
        }

        throw new AssertionError("The JNLP agent failed to connect");
    }

    @After
    public void tearDown() {
        if (jnlp != null)
            jnlp.destroy();
    }
}
