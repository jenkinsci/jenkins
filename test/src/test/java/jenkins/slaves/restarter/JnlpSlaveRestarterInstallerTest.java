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

import static org.junit.Assert.assertEquals;

import hudson.Proc;
import hudson.model.Slave;
import hudson.slaves.DumbSlave;
import hudson.slaves.JNLPLauncher;
import hudson.slaves.SlaveComputer;
import java.io.File;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import jenkins.security.MasterToSlaveCallable;
import org.apache.commons.io.FileUtils;
import org.apache.tools.ant.util.JavaEnvUtils;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsSessionRule;
import org.jvnet.hudson.test.LoggerRule;

public class JnlpSlaveRestarterInstallerTest {

    @Rule
    public JenkinsSessionRule rr = new JenkinsSessionRule();

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @Rule
    public LoggerRule logging = new LoggerRule().record(JnlpSlaveRestarterInstaller.class, Level.FINE).capture(10);

    @Issue("JENKINS-19055")
    @Test
    public void tcpReconnection() throws Throwable {
        reconnection(false);
    }

    @Issue("JENKINS-66446")
    @Test
    public void webSocketReconnection() throws Throwable {
        reconnection(true);
    }

    private void reconnection(boolean webSocket) throws Throwable {
        AtomicReference<Proc> proc = new AtomicReference<>();
        AtomicBoolean canWork = new AtomicBoolean();
        try {
            rr.then(r -> {
                JNLPLauncher launcher = new JNLPLauncher(true);
                launcher.setWebSocket(webSocket);
                DumbSlave s = new DumbSlave("remote", tmp.newFolder("agent").getAbsolutePath(), launcher);
                r.jenkins.addNode(s);
                String secret = ((SlaveComputer) s.toComputer()).getJnlpMac();
                File slaveJar = tmp.newFile();
                FileUtils.copyURLToFile(new Slave.JnlpJar("agent.jar").getURL(), slaveJar);
                proc.set(r.createLocalLauncher().launch().cmds(
                    JavaEnvUtils.getJreExecutable("java"), "-jar", slaveJar.getAbsolutePath(),
                    "-jnlpUrl", r.getURL() + "computer/remote/jenkins-agent.jnlp",
                    "-secret", secret
                ).stdout(System.out).start());
                r.waitOnline(s);
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
                assertEquals(canWork.get() ? 1 : 2, s.getChannel().call(new JVMCount()).intValue());
            });
        } finally {
            if (proc.get() != null) {
                proc.get().kill();
            }
        }
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
