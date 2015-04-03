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

import hudson.FilePath;
import hudson.remoting.VirtualChannel;
import hudson.slaves.DumbSlave;
import hudson.util.StreamTaskListener;

import java.io.File;
import java.io.IOException;
import java.util.logging.ConsoleHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

import jenkins.MasterToSlaveFileCallable;
import static org.junit.Assert.*;

import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MockFolder;

public class WorkspaceCleanupThreadTest {

    // TODO test that new workspaces are skipped
    // TODO test that SCM.processWorkspaceBeforeDeletion can reject

    @Rule public JenkinsRule r = new JenkinsRule();

    private static final Logger logger = Logger.getLogger(WorkspaceCleanupThread.class.getName());
    @BeforeClass public static void logging() {
        logger.setLevel(Level.ALL);
        Handler handler = new ConsoleHandler();
        handler.setLevel(Level.ALL);
        logger.addHandler(handler);
    }

    @Test public void cleanUpSlaves() throws Exception {
        FreeStyleProject p = r.createFreeStyleProject();

        FilePath ws1 = createOldWorkspaceOn(r.createOnlineSlave(), p);

        FilePath ws2 = createOldWorkspaceOn(r.createOnlineSlave(), p);

        p.setAssignedNode(r.jenkins);
        FreeStyleBuild b3 = r.assertBuildStatusSuccess(p.scheduleBuild2(0));
        assertEquals(r.jenkins, b3.getBuiltOn());
        assertEquals(r.jenkins, p.getLastBuiltOn());

        performCleanup();

        assertFalse(ws1.exists());
        assertFalse(ws2.exists());
        assertTrue(b3.getWorkspace().exists());
    }

    @Issue("JENKINS-21023")
    @Test public void modernMasterWorkspaceLocation() throws Exception {
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
    @Test public void jobInFolder() throws Exception {
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
        ws2.getParent().act(new Detouch()); // ${s1.rootPath}/workspace/d/

        performCleanup();

        assertFalse(ws1.exists());
        assertFalse(ws2.exists());
        assertTrue(ws3.exists());
        assertTrue(ws4.exists());
    }

    private FilePath createOldWorkspaceOn(Node slave, FreeStyleProject p) throws Exception {
        p.setAssignedNode(slave);
        FreeStyleBuild b1 = r.assertBuildStatusSuccess(p.scheduleBuild2(0));
        assertEquals(slave, b1.getBuiltOn());
        FilePath ws = b1.getWorkspace();
        assertNotNull(ws);
        ws.act(new Detouch());
        return ws;
    }

    private void performCleanup() throws InterruptedException, IOException {
        new WorkspaceCleanupThread().execute(StreamTaskListener.fromStdout());
    }

    private static final class Detouch extends MasterToSlaveFileCallable<Void> {
        @Override public Void invoke(File f, VirtualChannel channel) throws IOException, InterruptedException {
            Assume.assumeTrue("failed to reset lastModified on " + f, f.setLastModified(0));
            return null;
        }
    }
}
