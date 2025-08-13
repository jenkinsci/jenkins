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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

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
import jenkins.security.SlaveToMasterCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.LoggerRule;
import org.jvnet.hudson.test.junit.jupiter.BuildWatcherExtension;
import org.jvnet.hudson.test.junit.jupiter.InboundAgentExtension;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@Issue("JEP-222")
@WithJenkins
public class WebSocketAgentsTest {

    @RegisterExtension
    private static final BuildWatcherExtension buildWatcher = new BuildWatcherExtension();

    @RegisterExtension
    private final InboundAgentExtension inboundAgents = new InboundAgentExtension();

    private JenkinsRule r;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        r = rule;
    }

    /**
     * Verify basic functionality of an agent in {@code -webSocket} mode.
     * Requires {@code remoting} to have been {@code mvn install}ed.
     * Does not show {@code FINE} or lower agent logs ({@link JenkinsRule#showAgentLogs(Slave, LoggerRule)} cannot be used here).
     * Related to {@link hudson.slaves.JNLPLauncherTest} (also see closer to {@link hudson.bugs.JnlpAccessWithSecuredHudsonTest}).
     * @see hudson.remoting.Launcher
     */
    @Test
    void smokes() throws Exception {
        Slave s = inboundAgents.createAgent(r, InboundAgentExtension.Options.newBuilder().webSocket().build());
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
