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

package jenkins.slaves;

import hudson.Functions;
import hudson.model.Computer;
import hudson.model.FreeStyleProject;
import hudson.remoting.Engine;
import hudson.slaves.DumbSlave;
import hudson.slaves.JNLPLauncher;
import hudson.slaves.SlaveComputer;
import hudson.tasks.BatchFile;
import hudson.tasks.Shell;
import java.util.logging.Level;
import jenkins.security.SlaveToMasterCallable;
import org.junit.ClassRule;
import org.junit.Test;
import static org.junit.Assert.*;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.LoggerRule;

public class WebSocketAgentsTest {

    @ClassRule public static BuildWatcher buildWatcher = new BuildWatcher();

    @Rule public JenkinsRule r = new JenkinsRule();

    @Rule public LoggerRule logging = new LoggerRule().record(SlaveComputer.class, Level.FINEST).record(WebSocketAgents.class, Level.FINEST).record(Engine.class, Level.FINEST);

    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    @Test public void smokes() throws Exception {
        DumbSlave s = new DumbSlave("remote", tmp.newFolder("agent").getAbsolutePath(), new JNLPLauncher(true));
        r.jenkins.addNode(s);
        String secret = ((SlaveComputer) r.jenkins.getComputer("remote")).getJnlpMac();
        Computer.threadPoolForRemoting.submit(() -> {
            // Not as realistic class loading as JNLPLauncherTest.testHeadlessLaunch, but faster to iterate since everything runs inside one JVM.
            hudson.remoting.jnlp.Main._main(new String[] {"-headless", "-url", r.getURL().toString(), "-workDir", tmp.newFolder("work").getAbsolutePath(), secret, "remote"});
            return null;
        });
        r.waitOnline(s);
        assertEquals("response", s.getChannel().call(new DummyTask()));
        FreeStyleProject p = r.createFreeStyleProject();
        p.setAssignedNode(s);
        p.getBuildersList().add(Functions.isWindows() ? new BatchFile("echo hello") : new Shell("echo hello"));
        r.buildAndAssertSuccess(p);
    }

    private static class DummyTask extends SlaveToMasterCallable<String, RuntimeException> {
        @Override public String call() {
            return "response";
        }
    }

}
