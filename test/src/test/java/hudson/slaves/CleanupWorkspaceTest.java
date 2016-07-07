/*
 * The MIT License
 *
 * Copyright (c) 2016, Michael Neale, CloudBees Inc
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

import hudson.model.*;
import org.jvnet.hudson.test.HudsonTestCase;

import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class CleanupWorkspaceTest extends HudsonTestCase {
    @Override
    protected void setUp() throws Exception {
        super.setUp();

        // Set master executor count to zero to force all jobs to slaves
        jenkins.setNumExecutors(0);
    }

    public void testCleanupOnDelete() throws Exception {
        Slave slave = createSlave();
        FreeStyleProject project = createFreeStyleProject();

        Future<FreeStyleBuild> build = project.scheduleBuild2(0);
        assertBuildStatus(Result.SUCCESS, build.get(20, TimeUnit.SECONDS));

        assertTrue(slave.getWorkspaceFor(project).exists());
        project.delete();

        //after deleting workspaces should be gone:
        Thread.sleep(500);
        assertFalse(slave.getWorkspaceFor(project).exists());
    }

}
