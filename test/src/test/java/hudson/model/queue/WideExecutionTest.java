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
package hudson.model.queue;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import hudson.matrix.Axis;
import hudson.matrix.AxisList;
import hudson.matrix.MatrixProject;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Executor;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Queue;
import hudson.model.Queue.Executable;
import hudson.model.Queue.Task;
import hudson.model.labels.LabelExpression;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestExtension;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.ExecutionException;

/**
 * @author Kohsuke Kawaguchi
 */
public class WideExecutionTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @TestExtension
    public static class Contributor extends SubTaskContributor {
        public Collection<? extends SubTask> forProject(final AbstractProject<?, ?> p) {
            return Collections.singleton(new AbstractSubTask() {
                private final AbstractSubTask outer = this;
                public Executable createExecutable() throws IOException {
                    return new Executable() {
                        public SubTask getParent() {
                            return outer;
                        }

                        public void run() {
                            WorkUnitContext wuc = Executor.currentExecutor().getCurrentWorkUnit().context;
                            AbstractBuild b = (AbstractBuild)wuc.getPrimaryWorkUnit().getExecutable();
                            try {
                                b.setDescription("I was here");
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }

                        public long getEstimatedDuration() {
                            return 0;
                        }
                    };
                }

                public Task getOwnerTask() {
                    return p;
                }

                public String getDisplayName() {
                    return "Company of "+p.getDisplayName();
                }
            });
        }
    }

    @Issue("JENKINS-30084")
    @Test
    /*
     * this is to test that when the assigned executor is not available the flyweighttask is put into the buildable list,
     * thus the node will be provisioned.
     * when the flyweight task is not assigned to an offline executors the buildable list is empty.
     *
     */
    public void flyWeightTaskQueue () throws IOException {
        MatrixProject project2 = j.createMatrixProject();
        project2.setAxes(new AxisList(
                new Axis("axis", "a", "b")
        ));
        project2.scheduleBuild2(0);
        Queue.getInstance().maintain();
        assertTrue(Queue.getInstance().getBuildableItems().isEmpty());
        MatrixProject project = j.createMatrixProject();
        project.setAxes(new AxisList(
                new Axis("axis", "a", "b")
        ));
        project.setAssignedLabel(LabelExpression.get("aws-linux"));
        project.scheduleBuild2(0);
        Queue.getInstance().maintain();
        assertTrue(Queue.getInstance().getBuildableItems().get(0).task.equals(project));
        assertEquals(Queue.getInstance().getBuildableItems().get(0).getAssignedLabel().getExpression(), "aws-linux");
    }

    @Test
    public void run() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject();
        FreeStyleBuild b = j.assertBuildStatusSuccess(p.scheduleBuild2(0));
        assertEquals("I was here", b.getDescription());
    }
}
