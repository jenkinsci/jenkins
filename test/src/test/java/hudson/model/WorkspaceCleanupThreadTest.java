/*
 * The MIT License
 *
 * Copyright 2014 Jesse Glick.
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

package hudson.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import hudson.FilePath;
import hudson.remoting.VirtualChannel;
import hudson.scm.NullSCM;
import hudson.slaves.DumbSlave;
import hudson.slaves.WorkspaceList;
import hudson.util.StreamTaskListener;
import java.io.File;
import java.io.IOException;
import java.io.Serial;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import jenkins.MasterToSlaveFileCallable;
import jenkins.model.Jenkins;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.LogRecorder;
import org.jvnet.hudson.test.MockFolder;
import org.jvnet.hudson.test.WithoutJenkins;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class WorkspaceCleanupThreadTest {

    private final LogRecorder logs = new LogRecorder().record(WorkspaceCleanupThread.class, Level.ALL);

    private JenkinsRule r;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        r = rule;
    }

    @Test
    void cleanUpSlaves() throws Exception {
        FreeStyleProject p = r.createFreeStyleProject();

        FilePath ws1 = createOldWorkspaceOn(r.createOnlineSlave(), p);

        p.setAssignedNode(r.jenkins);
        FreeStyleBuild b = r.buildAndAssertSuccess(p);
        assertEquals(r.jenkins, b.getBuiltOn());
        FilePath ws2 = b.getWorkspace();

        FilePath ws3 = createOldWorkspaceOn(r.createOnlineSlave(), p);

        performCleanup();

        assertFalse(ws1.exists()); // Old one - deleted
        assertTrue(ws2.exists()); // Not old enough - kept
        assertTrue(ws3.exists()); // Latest - kept
    }

    @Issue("JENKINS-21023")
    @Test
    void modernMasterWorkspaceLocation() throws Exception {
        FreeStyleProject p = r.createFreeStyleProject();

        FilePath ws1 = createOldWorkspaceOn(r.jenkins, p);

        DumbSlave s = r.createOnlineSlave();
        FilePath ws2 = createOldWorkspaceOn(s, p);
        assertEquals(s, p.getLastBuiltOn());

        performCleanup();

        assertFalse(ws1.exists());
        assertTrue(ws2.exists());
    }

    @Issue("JENKINS-21023")
    @Test
    void jobInFolder() throws Exception {
        MockFolder d = r.createFolder("d");
        FreeStyleProject p1 = d.createProject(FreeStyleProject.class, "p");
        FilePath ws1 = createOldWorkspaceOn(r.jenkins, p1);

        DumbSlave s1 = r.createOnlineSlave();
        FilePath ws2 = createOldWorkspaceOn(s1, p1);
        DumbSlave s2 = r.createOnlineSlave();
        FilePath ws3 = createOldWorkspaceOn(s2, p1);
        assertEquals(s2, p1.getLastBuiltOn());

        FreeStyleProject p2 = d.createProject(FreeStyleProject.class, "p2");
        FilePath ws4 = createOldWorkspaceOn(s1, p2);
        assertEquals(s1, p2.getLastBuiltOn());
        ws2.getParent().act(new Touch(0)); // ${s1.rootPath}/workspace/d/

        performCleanup();

        assertFalse(ws1.exists());
        assertFalse(ws2.exists());
        assertTrue(ws3.exists());
        assertTrue(ws4.exists());
    }

    @Test
    void doNothingIfDisabled() throws Exception {
        WorkspaceCleanupThread.disabled = true;
        FreeStyleProject p = r.createFreeStyleProject();

        FilePath ws = createOldWorkspaceOn(r.jenkins, p);
        createOldWorkspaceOn(r.createOnlineSlave(), p);

        performCleanup();

        assertTrue(ws.exists());

        WorkspaceCleanupThread.disabled = false;
        performCleanup();

        assertFalse(ws.exists());
    }

    @Test
    void removeOnlyWhatIsOldEnough() throws Exception {
        FreeStyleProject p = r.createFreeStyleProject();
        FilePath ws = createOldWorkspaceOn(r.jenkins, p);
        createOldWorkspaceOn(r.createOnlineSlave(), p);

        long twoDaysOld = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(2);
        ws.act(new Touch(twoDaysOld));

        WorkspaceCleanupThread.retainForDays = 3;
        performCleanup();

        assertTrue(ws.exists());

        WorkspaceCleanupThread.retainForDays = 1;
        performCleanup();

        assertFalse(ws.exists());
    }

    @Test
    @WithoutJenkins
    void recurrencePeriodIsInHours() {
        assertEquals(
                TimeUnit.HOURS.toMillis(WorkspaceCleanupThread.recurrencePeriodHours),
                new WorkspaceCleanupThread().getRecurrencePeriod()
        );
    }

    @Test
    void vetoByScm() throws Exception {
        FreeStyleProject p = r.createFreeStyleProject();
        FilePath ws = createOldWorkspaceOn(r.jenkins, p);
        createOldWorkspaceOn(r.createOnlineSlave(), p);

        p.setScm(new VetoSCM(false));
        performCleanup();

        assertTrue(ws.exists());

        p.setScm(new VetoSCM(true));
        performCleanup();

        assertFalse(ws.exists());
    }

    @Issue("JENKINS-27152")
    @Test
    void deleteTemporaryDirectory() throws Exception {
        FreeStyleProject p = r.createFreeStyleProject();
        FilePath ws = createOldWorkspaceOn(r.jenkins, p);
        FilePath tmp = WorkspaceList.tempDir(ws);
        tmp.child("stuff").write("content", null);
        tmp.act(new Touch(0));
        createOldWorkspaceOn(r.createOnlineSlave(), p);
        performCleanup();
        assertFalse(ws.exists());
        assertFalse(tmp.exists(), "temporary directory should be cleaned up as well");
    }

    @Issue("JENKINS-65829")
    @Test
    void deleteSoleLibsDirectory() throws Exception {
        FreeStyleProject p = r.createFreeStyleProject();
        FilePath jobWs = Jenkins.get().getWorkspaceFor(p);
        FilePath libsWs = jobWs.withSuffix(WorkspaceList.COMBINATOR + "libs");
        libsWs.child("test-libs").write("content", null);
        libsWs.act(new Touch(0));
        assertFalse(jobWs.exists());
        assertTrue(libsWs.exists());
        performCleanup();
        assertFalse(jobWs.exists());
        assertFalse(libsWs.exists(), "libs directory should be cleaned up as well");
    }

    private FilePath createOldWorkspaceOn(Node slave, FreeStyleProject p) throws Exception {
        p.setAssignedNode(slave);
        FreeStyleBuild b1 = r.buildAndAssertSuccess(p);
        assertEquals(slave, b1.getBuiltOn());
        FilePath ws = b1.getWorkspace();
        assertNotNull(ws);
        ws.act(new Touch(0));
        return ws;
    }

    private FilePath createOldLibsWorkspace(FreeStyleProject p) throws IOException, InterruptedException {
        FilePath libsWs = Jenkins.get().getWorkspaceFor(p).withSuffix(WorkspaceList.COMBINATOR + "libs");
        libsWs.child("test-libs").write("content", null);
        libsWs.act(new Touch(0));
        return libsWs;
    }

    private void performCleanup() throws InterruptedException, IOException {
        new WorkspaceCleanupThread().execute(StreamTaskListener.fromStdout());
    }

    private static final class VetoSCM extends NullSCM {
        private final boolean answer;

        VetoSCM(boolean answer) {
            this.answer = answer;
        }

        @Override
        public boolean processWorkspaceBeforeDeletion(
                Job<?, ?> project, FilePath workspace, Node node
        ) {
            return answer;
        }
    }

    private static final class Touch extends MasterToSlaveFileCallable<Void> {
        @Serial
        private static final long serialVersionUID = 1L;
        private final long time;

        Touch(long time) {
            this.time = time;
        }

        @Override public Void invoke(File f, VirtualChannel channel) {
            assumeTrue(f.setLastModified(time), "failed to reset lastModified on " + f);
            return null;
        }
    }
}
