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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import hudson.Functions;
import hudson.model.FreeStyleProject;
import hudson.model.Slave;
import hudson.remoting.Engine;
import hudson.slaves.SlaveComputer;
import hudson.tasks.BatchFile;
import hudson.tasks.Shell;
import java.nio.charset.StandardCharsets;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.security.SlaveToMasterCallable;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.InboundAgentRule;
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
    public InboundAgentRule inboundAgents = new InboundAgentRule();

    @Rule
    public LoggerRule logging = new LoggerRule().
        record(Slave.class, Level.FINE).
        record(SlaveComputer.class, Level.FINEST).
        record(WebSocketAgents.class, Level.FINEST).
        record(Engine.class, Level.FINEST);

    /**
     * Verify basic functionality of an agent in {@code -webSocket} mode.
     * Requires {@code remoting} to have been {@code mvn install}ed.
     * Does not show {@code FINE} or lower agent logs ({@link JenkinsRule#showAgentLogs(Slave, LoggerRule)} cannot be used here).
     * Related to {@link hudson.slaves.JNLPLauncherTest} (also see closer to {@link hudson.bugs.JnlpAccessWithSecuredHudsonTest}).
     * @see hudson.remoting.Launcher
     */
    @Test
    public void smokes() throws Exception {
        Slave s = inboundAgents.createAgent(r, InboundAgentRule.Options.newBuilder().secret().webSocket().build());
        try {
            assertEquals("response", s.getChannel().call(new DummyTask()));
            assertNotNull(s.getChannel().call(new FatTask()));
            FreeStyleProject p = r.createFreeStyleProject();
            p.setAssignedNode(s);
            p.getBuildersList().add(Functions.isWindows() ? new BatchFile("echo hello") : new Shell("echo hello"));
            r.buildAndAssertSuccess(p);
            s.toComputer().getLogText().writeLogTo(0, System.out);
        } finally {
            inboundAgents.stop(r, s.getNodeName());
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
            return new String(payload, StandardCharsets.UTF_8);
        }
    }

}
