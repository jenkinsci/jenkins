/*
 * The MIT License
 *
 * Copyright 2015 CloudBees Inc.
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

package jenkins.model;

import static org.junit.Assert.assertEquals;

import hudson.model.FreeStyleProject;
import hudson.model.LoadStatistics;
import hudson.model.Node;
import hudson.model.ParametersAction;
import hudson.model.Queue;
import hudson.model.StringParameterValue;
import hudson.model.labels.LabelAtom;
import hudson.agents.DumbAgent;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;

/**
 * Tests for {@link UnlabeledLoadStatistics} class.
 * @author Oleg Nenashev
 */
public class UnlabeledLoadStatisticsTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    private final LoadStatistics unlabeledLoad = new UnlabeledLoadStatistics();

    @After
    public void clearQueue() {
        j.getInstance().getQueue().clear();
    }

    @Test
    @Issue("JENKINS-28446")
    public void computeQueueLength() throws Exception {
        final Queue queue = j.jenkins.getQueue();
        assertEquals("Queue must be empty when the test starts", 0, queue.getBuildableItems().size());
        assertEquals("Statistics must return 0 when the test starts", 0, unlabeledLoad.computeQueueLength());

        // Disable builds by default, create an agent to prevent assigning of "built-in" labels
        j.jenkins.setNumExecutors(0);
        DumbAgent agent = j.createOnlineAgent(new LabelAtom("testLabel"));
        agent.setMode(Node.Mode.EXCLUSIVE);

        // Init project
        FreeStyleProject unlabeledProject = j.createFreeStyleProject("UnlabeledProject");
        unlabeledProject.setConcurrentBuild(true);
        FreeStyleProject labeledProject = j.createFreeStyleProject("LabeledProject");
        labeledProject.setAssignedLabel(new LabelAtom("foo"));

        // Put unlabeled build into the queue
        unlabeledProject.scheduleBuild2(0, new ParametersAction(new StringParameterValue("FOO", "BAR1")));
        queue.maintain();
        assertEquals("Unlabeled build must be taken into account", 1, unlabeledLoad.computeQueueLength());
        unlabeledProject.scheduleBuild2(0, new ParametersAction(new StringParameterValue("FOO", "BAR2")));
        queue.maintain();
        assertEquals("Second Unlabeled build must be taken into account", 2, unlabeledLoad.computeQueueLength());

        // Put labeled build into the queue
        labeledProject.scheduleBuild2(0);
        queue.maintain();
        assertEquals("Labeled builds must be ignored", 2, unlabeledLoad.computeQueueLength());

        // Allow executions of unlabeled builds on built-in node, all unlabeled builds should pass
        j.jenkins.setNumExecutors(1);
        j.buildAndAssertSuccess(unlabeledProject);
        queue.maintain();
        assertEquals("Queue must contain the labeled project build", 1, queue.getBuildableItems().size());
        assertEquals("Statistics must return 0 after all builds", 0, unlabeledLoad.computeQueueLength());
    }

}
