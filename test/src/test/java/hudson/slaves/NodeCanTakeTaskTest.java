/*
 * The MIT License
 *
 * Copyright (c) 2010, InfraDNA, Inc.
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Messages;
import hudson.model.Node;
import hudson.model.Result;
import hudson.model.Queue.BuildableItem;
import hudson.model.Queue.Task;
import hudson.model.Slave;
import hudson.model.queue.CauseOfBlockage;

import org.jvnet.hudson.test.HudsonTestCase;

public class NodeCanTakeTaskTest extends HudsonTestCase {
    @Override
    protected void setUp() throws Exception {
        super.setUp();

        // Set master executor count to zero to force all jobs to slaves
        jenkins.setNumExecutors(0);
    }

    public void testTakeBlockedByProperty() throws Exception {
        Slave slave = createSlave();
        FreeStyleProject project = createFreeStyleProject();

        // First, attempt to run our project before adding the property
        Future<FreeStyleBuild> build = project.scheduleBuild2(0);
        assertBuildStatus(Result.SUCCESS, build.get(20, TimeUnit.SECONDS));

        // Add the build-blocker property and try again
        slave.getNodeProperties().add(new RejectAllTasksProperty());

        build = project.scheduleBuild2(0);
        try {
            build.get(10, TimeUnit.SECONDS);
            fail("Expected timeout exception");
        } catch (TimeoutException e) {
            List<BuildableItem> buildables = jenkins.getQueue().getBuildableItems();
            assertNotNull(buildables);
            assertEquals(1, buildables.size());

            BuildableItem item = buildables.get(0);
            assertEquals(project, item.task);
            assertNotNull(item.getCauseOfBlockage());
            assertEquals(Messages.Queue_WaitingForNextAvailableExecutor(), item.getCauseOfBlockage().getShortDescription());
        }
    }

    private static class RejectAllTasksProperty extends NodeProperty<Node> {
        @Override
        public CauseOfBlockage canTake(Task task) {
            return CauseOfBlockage.fromMessage(null);
        }
    }
}
