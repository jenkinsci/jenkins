/*
 * The MIT License
 *
 * Copyright 2019 CloudBees, Inc.
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

package jenkins.agents;

import hudson.Functions;
import hudson.Proc;
import hudson.model.FreeStyleProject;
import hudson.model.Slave;
import hudson.remoting.Engine;
import hudson.slaves.DumbSlave;
import hudson.slaves.JNLPLauncher;
import hudson.slaves.SlaveComputer;
import hudson.tasks.BatchFile;
import hudson.tasks.Shell;
import java.io.File;
import java.util.Random;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.security.SlaveToMasterCallable;
import org.apache.commons.io.FileUtils;
import org.apache.tools.ant.util.JavaEnvUtils;
import org.junit.ClassRule;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.LoggerRule;

@Issue("JEP-222")
public class WebSocketAgentsTest {

    private static final Logger LOGGER = Logger.getLogger(WebSocketAgentsTest.class.getName());

    @ClassRule
    public static BuildWatcher buildWatcher = new BuildWatcher();

    @Rule
    public JenkinsRule r = new JenkinsRule();

    @Rule
    public LoggerRule logging = new LoggerRule().
        record(Slave.class, Level.FINE).
        record(SlaveComputer.class, Level.FINEST).
        record(WebSocketAgents.class, Level.FINEST).
        record(Engine.class, Level.FINEST);

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    /**
     * Verify basic functionality of an agent in {@code -webSocket} mode.
     * Requires {@code remoting} to have been {@code mvn install}ed.
     * Does not show {@code FINE} or lower agent logs ({@link JenkinsRule#showAgentLogs(Slave, LoggerRule)} cannot be used here).
     * Unlike {@link hudson.slaves.JNLPLauncherTest} this does not use {@code javaws};
     * closer to {@link hudson.bugs.JnlpAccessWithSecuredHudsonTest}.
     * @see hudson.remoting.Launcher
     */
    @SuppressWarnings("ResultOfMethodCallIgnored")
    @Test
    public void smokes() throws Exception {
        AtomicReference<Proc> proc = new AtomicReference<>();
        try {
            JNLPLauncher launcher = new JNLPLauncher(true);
            launcher.setWebSocket(true);
            DumbSlave s = new DumbSlave("remote", tmp.newFolder("agent").getAbsolutePath(), launcher);
            r.jenkins.addNode(s);
            String secret = ((SlaveComputer) s.toComputer()).getJnlpMac();
            File slaveJar = tmp.newFile();
            FileUtils.copyURLToFile(new Slave.JnlpJar("slave.jar").getURL(), slaveJar);
            proc.set(r.createLocalLauncher().launch().cmds(
                JavaEnvUtils.getJreExecutable("java"), "-jar", slaveJar.getAbsolutePath(),
                "-jnlpUrl", r.getURL() + "computer/remote/slave-agent.jnlp",
                "-secret", secret
            ).stdout(System.out).start());
            r.waitOnline(s);
            assertEquals("response", s.getChannel().call(new DummyTask()));
            assertNotNull(s.getChannel().call(new FatTask()));
            FreeStyleProject p = r.createFreeStyleProject();
            p.setAssignedNode(s);
            p.getBuildersList().add(Functions.isWindows() ? new BatchFile("echo hello") : new Shell("echo hello"));
            r.buildAndAssertSuccess(p);
            s.toComputer().getLogText().writeLogTo(0, System.out);
        } finally {
            if (proc.get() != null) {
                proc.get().kill();
                while (r.jenkins.getComputer("remote").isOnline()) {
                    LOGGER.info("waiting for computer to go offline");
                    Thread.sleep(250);
                }
            }
        }
    }

    private static class DummyTask extends SlaveToMasterCallable<String, RuntimeException> {
        @Override
        public String call() {
            return "response";
        }
    }

    private static class FatTask extends SlaveToMasterCallable<String, RuntimeException> {
        private byte[] payload;

        private FatTask() {
            payload = new byte[1024 * 1024];
            new Random().nextBytes(payload);
        }

        @Override
        public String call() {
            return new String(payload);
        }
    }

}
