/*
 * The MIT License
 *
 * Copyright 2022 CloudBees, Inc.
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

package jenkins.slaves.restarter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import hudson.Functions;
import hudson.model.Slave;
import hudson.slaves.DumbSlave;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import jenkins.security.MasterToSlaveCallable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.LogRecorder;
import org.jvnet.hudson.test.junit.jupiter.InboundAgentExtension;
import org.jvnet.hudson.test.junit.jupiter.JenkinsSessionExtension;

class JnlpSlaveRestarterInstallerTest {

    @RegisterExtension
    private final JenkinsSessionExtension rr = new JenkinsSessionExtension();

    @RegisterExtension
    private final InboundAgentExtension inboundAgents = new InboundAgentExtension();

    private final LogRecorder logging = new LogRecorder().record(JnlpSlaveRestarterInstaller.class, Level.FINE).capture(10);

    @Issue("JENKINS-19055")
    @Test
    void tcpReconnection() throws Throwable {
        // TODO Enable when test is reliable on Windows agents of ci.jenkins.io
        // When builds switched from ACI containers to virtual machines, this test consistently failed
        // When the test is run on local Windows computers, it passes
        // Disable the test on ci.jenkins.io and friends when running Windows
        // Do not disable for Windows developers generally
        assumeFalse(Functions.isWindows() && System.getenv("CI") != null, "TODO: Test fails on Windows VM");
        reconnection(false);
    }

    @Issue("JENKINS-66446")
    @Test
    void webSocketReconnection() throws Throwable {
        // TODO Enable when test is reliable on Windows agents of ci.jenkins.io
        // When builds switched from ACI containers to virtual machines, this test consistently failed
        // When the test is run on local Windows computers, it passes
        // Disable the test on ci.jenkins.io and friends when running Windows
        // Do not disable for Windows developers generally
        assumeFalse(Functions.isWindows() && System.getenv("CI") != null, "TODO: Test fails on Windows VM");
        reconnection(true);
    }

    private void reconnection(boolean webSocket) throws Throwable {
        AtomicBoolean canWork = new AtomicBoolean();
            rr.then(r -> {
                InboundAgentExtension.Options.Builder builder = InboundAgentExtension.Options.newBuilder().name("remote");
                if (webSocket) {
                    builder.webSocket();
                }
                Slave s = inboundAgents.createAgent(r, builder.build());
                assertEquals(1, s.getChannel().call(new JVMCount()).intValue());
                while (logging.getMessages().stream().noneMatch(msg -> msg.contains("Effective SlaveRestarter on remote:"))) {
                    Thread.sleep(100);
                }
                // Likely true on Unix, likely false on Windows (not under winsw):
                canWork.set(logging.getMessages().stream().anyMatch(msg -> msg.contains("Effective SlaveRestarter on remote: [jenkins.slaves.restarter.")));
            });
            rr.then(r -> {
                DumbSlave s = (DumbSlave) r.jenkins.getNode("remote");
                r.waitOnline(s);
                try {
                    assertEquals(canWork.get() ? 1 : 2, s.getChannel().call(new JVMCount()).intValue());
                } finally {
                    inboundAgents.stop(r, s.getNodeName());
                }
            });
    }

    private static final class JVMCount extends MasterToSlaveCallable<Integer, RuntimeException> {
        private static final String KEY = "launch-count";

        @Override
        public Integer call() throws RuntimeException {
            int count = Integer.getInteger(KEY, 0) + 1;
            System.setProperty(KEY, Integer.toString(count));
            return count;
        }
    }

}
