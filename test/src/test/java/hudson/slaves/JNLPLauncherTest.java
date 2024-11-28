/*
 * The MIT License
 *
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package hudson.slaves;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import hudson.Proc;
import hudson.Util;
import hudson.model.Computer;
import hudson.model.Node;
import hudson.model.Node.Mode;
import hudson.model.Slave;
import hudson.remoting.Launcher;
import hudson.remoting.Which;
import hudson.util.ArgumentListBuilder;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import jenkins.security.SlaveToMasterCallable;
import jenkins.slaves.RemotingWorkDirSettings;
import org.htmlunit.Page;
import org.htmlunit.html.HtmlPage;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.rules.TemporaryFolder;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.LoggerRule;
import org.jvnet.hudson.test.SmokeTest;
import org.jvnet.hudson.test.recipes.LocalData;

/**
 * Tests of {@link JNLPLauncher}.
 * @author Kohsuke Kawaguchi
 */
@Category(SmokeTest.class)
public class JNLPLauncherTest {
    @Rule public JenkinsRule j = new JenkinsRule();

    @Rule public TemporaryFolder tmpDir = new TemporaryFolder();

    @Rule public LoggerRule logging = new LoggerRule().record(Slave.class, Level.FINE);

    /**
     * Starts a JNLP agent and makes sure it successfully connects to Jenkins.
     */
    @Test
    public void testLaunch() throws Exception {
        Computer c = addTestAgent(false);
        launchJnlpAndVerify(c, buildJnlpArgs(c));
    }

    /**
     * Starts a JNLP agent and makes sure it successfully connects to Jenkins.
     */
    @Test
    @Issue("JENKINS-39370")
    public void testLaunchWithWorkDir() throws Exception {
        File workDir = tmpDir.newFolder("workDir");

        Computer c = addTestAgent(false);
        launchJnlpAndVerify(c, buildJnlpArgs(c).add("-workDir", workDir.getAbsolutePath()));
        assertTrue("Remoting work dir should have been created", new File(workDir, "remoting").exists());
    }

    @Test
    @LocalData
    @Issue("JENKINS-44112")
    public void testNoWorkDirMigration() {
        Computer computer = j.jenkins.getComputer("Foo");
        assertThat(computer, instanceOf(SlaveComputer.class));

        SlaveComputer c = (SlaveComputer) computer;
        ComputerLauncher launcher = c.getLauncher();
        assertThat(launcher, instanceOf(JNLPLauncher.class));
        JNLPLauncher jnlpLauncher = (JNLPLauncher) launcher;
        assertNotNull("Work Dir Settings should be defined",
                jnlpLauncher.getWorkDirSettings());
        assertTrue("Work directory should be disabled for the migrated agent",
                jnlpLauncher.getWorkDirSettings().isDisabled());
    }

    @Issue("JENKINS-73011")
    @SuppressWarnings("deprecation")
    @Test
    public void deprecatedFields() throws Exception {
        var launcher = new JNLPLauncher();
        launcher.setWebSocket(true);
        launcher.setWorkDirSettings(new RemotingWorkDirSettings(false, null, "remoting2", false));
        launcher.setTunnel("someproxy");
        var agent = j.createSlave();
        agent.setLauncher(launcher);
        agent = j.configRoundtrip(agent);
        launcher = (JNLPLauncher) agent.getLauncher();
        assertThat(launcher.isWebSocket(), is(true));
        assertThat(launcher.getWorkDirSettings().getInternalDir(), is("remoting2"));
        assertThat(launcher.getTunnel(), is("someproxy"));
        launcher = new JNLPLauncher();
        launcher.setWebSocket(true);
        agent.setLauncher(launcher);
        agent = j.configRoundtrip(agent);
        launcher = (JNLPLauncher) agent.getLauncher();
        assertThat(launcher.isWebSocket(), is(true));
        assertThat(launcher.getWorkDirSettings().getInternalDir(), is("remoting"));
        assertThat(launcher.getTunnel(), nullValue());
    }

    @Test
    public void testDefaults() {
        assertFalse("Work directory enabled by default", new JNLPLauncher().getWorkDirSettings().isDisabled());
    }

    @Test
    @Issue("JENKINS-47056")
    public void testDelegatingComputerLauncher() throws Exception {
        File workDir = tmpDir.newFolder("workDir");

        ComputerLauncher launcher = new JNLPLauncher("", "", new RemotingWorkDirSettings(false, workDir.getAbsolutePath(), "internalDir", false));
        launcher = new DelegatingComputerLauncherImpl(launcher);
        Computer c = addTestAgent(launcher);
        launchJnlpAndVerify(c, buildJnlpArgs(c));
        assertTrue("Remoting work dir should have been created", new File(workDir, "internalDir").exists());
    }

    @Test
    @Issue("JENKINS-47056")
    public void testComputerLauncherFilter() throws Exception {
        File workDir = tmpDir.newFolder("workDir");

        ComputerLauncher launcher = new JNLPLauncher("", "", new RemotingWorkDirSettings(false, workDir.getAbsolutePath(), "internalDir", false));
        launcher = new ComputerLauncherFilterImpl(launcher);
        Computer c = addTestAgent(launcher);
        launchJnlpAndVerify(c, buildJnlpArgs(c));
        assertTrue("Remoting work dir should have been created", new File(workDir, "internalDir").exists());
    }

    private static class DelegatingComputerLauncherImpl extends DelegatingComputerLauncher {
        DelegatingComputerLauncherImpl(ComputerLauncher launcher) {
            super(launcher);
        }
    }

    private static class ComputerLauncherFilterImpl extends ComputerLauncherFilter {
        ComputerLauncherFilterImpl(ComputerLauncher launcher) {
            super(launcher);
        }
    }

    private ArgumentListBuilder buildJnlpArgs(Computer c) throws Exception {
        ArgumentListBuilder args = new ArgumentListBuilder();
        args.add(new File(new File(System.getProperty("java.home")), "bin/java").getPath(), "-jar");
        args.add(Which.jarFile(Launcher.class).getAbsolutePath());
        args.add("-url");
        args.add(j.getURL());
        args.add("-name");
        args.add(c.getName());

        if (c instanceof SlaveComputer) {
            SlaveComputer sc = (SlaveComputer) c;
            args.add("-secret");
            args.add(sc.getJnlpMac());
            ComputerLauncher launcher = sc.getLauncher();
            if (launcher instanceof ComputerLauncherFilter) {
                launcher = ((ComputerLauncherFilter) launcher).getCore();
            } else if (launcher instanceof DelegatingComputerLauncher) {
                launcher = ((DelegatingComputerLauncher) launcher).getLauncher();
            }
            if (launcher instanceof JNLPLauncher) {
                args.add(((JNLPLauncher) launcher).getWorkDirSettings().toCommandLineArgs(sc));
            }
        }

        return args;
    }

    /**
     * Launches the Inbound TCP agent and asserts its basic operations.
     */
    private void launchJnlpAndVerify(Computer c, ArgumentListBuilder args) throws Exception {
        Proc proc = j.createLocalLauncher().launch().cmds(args).stdout(System.out).pwd(".").start();

        try {
            // verify that the connection is established, up to 20 secs
            for (int i = 0; i < 200; i++) {
                Thread.sleep(100);
                if (!c.isOffline())
                    break;
            }

            if (c.isOffline()) {
                System.out.println(c.getLog());
                fail("Agent failed to go online");
            }
            // run some trivial thing
            System.err.println("Calling task...");
            assertEquals("done", c.getChannel().callAsync(new NoopTask()).get(5 * 60, TimeUnit.SECONDS));
            System.err.println("...done.");
        } finally {
            proc.kill();
        }

        Thread.sleep(500);
        assertTrue(c.isOffline());
    }

    /**
     * Adds an Inbound TCP agent to the system and returns it.
     */
    private Computer addTestAgent(boolean enableWorkDir) throws Exception {
        return addTestAgent(new JNLPLauncher(enableWorkDir));
    }

    /**
     * Adds an Inbound TCP agent to the system and returns it.
     */
    private Computer addTestAgent(ComputerLauncher launcher) throws Exception {
        List<Node> agents = new ArrayList<>(j.jenkins.getNodes());
        File dir = Util.createTempDir();
        agents.add(new DumbSlave("test", "dummy", dir.getAbsolutePath(), "1", Mode.NORMAL, "",
                launcher, RetentionStrategy.INSTANCE, new ArrayList<>()));
        j.jenkins.setNodes(agents);
        Computer c = j.jenkins.getComputer("test");
        assertNotNull(c);
        return c;
    }

    private static class NoopTask extends SlaveToMasterCallable<String, RuntimeException> {
        @Override
        public String call() {
            return "done";
        }

        private static final long serialVersionUID = 1L;
    }

    @Test
    public void testConfigRoundtrip() throws Exception {
        DumbSlave s = j.createSlave();
        JNLPLauncher original = new JNLPLauncher("a");
        s.setLauncher(original);
        j.assertEqualDataBoundBeans(((JNLPLauncher) s.getLauncher()).getWorkDirSettings(), RemotingWorkDirSettings.getEnabledDefaults());
        RemotingWorkDirSettings custom = new RemotingWorkDirSettings(false, null, "custom", false);
        ((JNLPLauncher) s.getLauncher()).setWorkDirSettings(custom);
        HtmlPage p = j.createWebClient().getPage(s, "configure");
        j.submit(p.getFormByName("config"));
        j.assertEqualBeans(original, s.getLauncher(), "tunnel");
        j.assertEqualDataBoundBeans(((JNLPLauncher) s.getLauncher()).getWorkDirSettings(), custom);
    }

    @Test
    public void testJnlpFileDownload() throws Exception {
        assertJnlpFileDownload("/jenkins-agent.jnlp");
    }

    @Test
    public void testObsoletedJnlpFileDownload() throws Exception {
        assertJnlpFileDownload("/slave-agent.jnlp"); // deliberately uses old URL
    }

    private void assertJnlpFileDownload(String filename) throws Exception {
        Computer c = addTestAgent(false);
        Page p = j.createWebClient().getPage(j.getURL() + "computer/" + c.getName() + filename);
        assertThat(p.getWebResponse().getStatusCode(), is(200));
    }

}
