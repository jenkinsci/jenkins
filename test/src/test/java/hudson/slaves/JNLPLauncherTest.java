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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

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
import java.io.IOException;
import java.io.Serial;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import jenkins.model.Jenkins;
import jenkins.security.SlaveToMasterCallable;
import jenkins.slaves.RemotingWorkDirSettings;
import org.htmlunit.Page;
import org.htmlunit.html.HtmlPage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.LogRecorder;
import org.jvnet.hudson.test.SimpleCommandLauncher;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.jvnet.hudson.test.recipes.LocalData;

/**
 * Tests of {@link JNLPLauncher}.
 * @author Kohsuke Kawaguchi
 */
@Tag("SmokeTest")
@WithJenkins
class JNLPLauncherTest {

    @TempDir
    private File tmpDir;

    private final LogRecorder logging = new LogRecorder().record(Slave.class, Level.FINE);

    private JenkinsRule j;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        j = rule;
    }

    /**
     * Starts a JNLP agent and makes sure it successfully connects to Jenkins.
     */
    @Test
    void testLaunch() throws Exception {
        Computer c = addTestAgent(false);
        launchJnlpAndVerify(c, buildJnlpArgs(c));
    }

    /**
     * Starts a JNLP agent and makes sure it successfully connects to Jenkins.
     */
    @Test
    @Issue("JENKINS-39370")
    void testLaunchWithWorkDir() throws Exception {
        File workDir = newFolder(tmpDir, "workDir");

        Computer c = addTestAgent(false);
        launchJnlpAndVerify(c, buildJnlpArgs(c).add("-workDir", workDir.getAbsolutePath()));
        assertTrue(new File(workDir, "remoting").exists(), "Remoting work dir should have been created");
    }

    @Test
    @LocalData
    @Issue("JENKINS-44112")
    void testNoWorkDirMigration() {
        Computer computer = j.jenkins.getComputer("Foo");
        assertThat(computer, instanceOf(SlaveComputer.class));

        SlaveComputer c = (SlaveComputer) computer;
        ComputerLauncher launcher = c.getLauncher();
        assertThat(launcher, instanceOf(JNLPLauncher.class));
        JNLPLauncher jnlpLauncher = (JNLPLauncher) launcher;
        assertNotNull(jnlpLauncher.getWorkDirSettings(),
                "Work Dir Settings should be defined");
        assertTrue(jnlpLauncher.getWorkDirSettings().isDisabled(),
                "Work directory should be disabled for the migrated agent");
    }

    @Issue("JENKINS-73011")
    @SuppressWarnings("deprecation")
    @Test
    void deprecatedFields() throws Exception {
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
    void testDefaults() {
        assertFalse(new JNLPLauncher().getWorkDirSettings().isDisabled(), "Work directory enabled by default");
    }

    @Test
    @Issue("JENKINS-47056")
    void testDelegatingComputerLauncher() throws Exception {
        File workDir = newFolder(tmpDir, "workDir");

        ComputerLauncher launcher = new JNLPLauncher("", "", new RemotingWorkDirSettings(false, workDir.getAbsolutePath(), "internalDir", false));
        launcher = new DelegatingComputerLauncherImpl(launcher);
        Computer c = addTestAgent(launcher);
        launchJnlpAndVerify(c, buildJnlpArgs(c));
        assertTrue(new File(workDir, "internalDir").exists(), "Remoting work dir should have been created");
    }

    @Test
    @Issue("JENKINS-47056")
    void testComputerLauncherFilter() throws Exception {
        File workDir = newFolder(tmpDir, "workDir");

        ComputerLauncher launcher = new JNLPLauncher("", "", new RemotingWorkDirSettings(false, workDir.getAbsolutePath(), "internalDir", false));
        launcher = new ComputerLauncherFilterImpl(launcher);
        Computer c = addTestAgent(launcher);
        launchJnlpAndVerify(c, buildJnlpArgs(c));
        assertTrue(new File(workDir, "internalDir").exists(), "Remoting work dir should have been created");
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

        if (c instanceof SlaveComputer sc) {
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

    @Test
    void changeLauncher() throws Exception {
        Computer c = addTestAgent(false);
        var name = c.getName();
        var node = c.getNode();
        assertThat(c.isLaunchSupported(), is(false));
        var nodeCopy = (Slave) Jenkins.XSTREAM2.fromXML(Jenkins.XSTREAM2.toXML(node));
        nodeCopy.setLauncher(new SimpleCommandLauncher("true"));
        Jenkins.get().getNodesObject().replaceNode(node, nodeCopy);
        assertThat(Jenkins.get().getComputer(name).isLaunchSupported(), is(true));
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

        @Serial
        private static final long serialVersionUID = 1L;
    }

    @Test
    void testConfigRoundtrip() throws Exception {
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
    void testJnlpFileDownload() throws Exception {
        assertJnlpFileDownload("/jenkins-agent.jnlp");
    }

    @Test
    void testObsoletedJnlpFileDownload() throws Exception {
        assertJnlpFileDownload("/slave-agent.jnlp"); // deliberately uses old URL
    }

    private void assertJnlpFileDownload(String filename) throws Exception {
        Computer c = addTestAgent(false);
        Page p = j.createWebClient().getPage(j.getURL() + "computer/" + c.getName() + filename);
        assertThat(p.getWebResponse().getStatusCode(), is(200));
    }

    private static File newFolder(File root, String... subDirs) throws IOException {
        String subFolder = String.join("/", subDirs);
        File result = new File(root, subFolder);
        if (!result.mkdirs()) {
            throw new IOException("Couldn't create folders " + root);
        }
        return result;
    }

}
