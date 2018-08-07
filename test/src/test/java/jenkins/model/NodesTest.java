/*
 * The MIT License
 *
 * Copyright 2018 CloudBees, Inc.
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

import hudson.model.Descriptor;
import hudson.model.Executor;
import hudson.model.Label;
import hudson.model.Node;
import hudson.model.Queue;
import hudson.model.Slave;
import hudson.model.queue.SubTask;
import hudson.slaves.ComputerLauncher;
import java.io.IOException;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;

import javax.annotation.Nonnull;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

public class NodesTest {

    @Rule
    public JenkinsRule r = new JenkinsRule();

    @Test
    @Issue("JENKINS-50599")
    public void addNodeShouldFailAtomically() throws Exception {
        InvalidNode node = new InvalidNode("foo", "temp", r.createComputerLauncher(null));
        try {
            r.jenkins.addNode(node);
            fail("Adding the node should have thrown an exception during serialization");
        } catch (IOException e) {
            String className = InvalidNode.class.getName();
            assertThat("The exception should be from failing to serialize the node",
                    e.getMessage(), containsString("Failed to serialize " + className + "#cl for class " + className));
        }
        assertThat("The node should not exist since #addNode threw an exception",
                r.jenkins.getNode("foo"), nullValue());
    }

    @Test
    @Issue("JENKINS-50599")
    public void addNodeShouldFailAtomicallyWhenReplacingNode() throws Exception {
        Node oldNode = r.createSlave("foo", "", null);
        r.jenkins.addNode(oldNode);
        InvalidNode newNode = new InvalidNode("foo", "temp", r.createComputerLauncher(null));
        try {
            r.jenkins.addNode(newNode);
            fail("Adding the node should have thrown an exception during serialization");
        } catch (IOException e) {
            String className = InvalidNode.class.getName();
            assertThat("The exception should be from failing to serialize the node",
                    e.getMessage(), containsString("Failed to serialize " + className + "#cl for class " + className));
        }
        assertThat("The old node should still exist since #addNode threw an exception",
                r.jenkins.getNode("foo"), sameInstance(oldNode));
    }

    @Test
    public void addNodeShouldReplaceExistingNode() throws Exception {
        Node oldNode = r.createSlave("foo", "", null);
        r.jenkins.addNode(oldNode);
        Node newNode = r.createSlave("foo", "", null);
        r.jenkins.addNode(newNode);
        assertThat(r.jenkins.getNode("foo"), sameInstance(newNode));
    }

    @Test
    public void removeNodeWithNonIdleExecutorsTest() throws Exception {
        Node node = r.createOnlineSlave();
        r.jenkins.addNode(node);
        TestFlyweightTask task = new TestFlyweightTask();
        task.label = node.getSelfLabel();
        r.jenkins.getQueue().schedule(task,0);
        //wait for execution
        while(!task.isBuilding){
            Thread.sleep(100);
        }
        Executor executor = node.toComputer().getOneOffExecutors().get(0);
        r.jenkins.getNodesObject().removeNode(node);
        //wait for executing of interruption
        Thread.sleep(1000);
        Assert.assertFalse(executor.isActive());
    }

    static class TestFlyweightTask implements Queue.FlyweightTask, Queue.Task {

        boolean isBuilding = false;
        transient Label label;

        @Override
        public String getDisplayName() {
            return "test";
        }

        @Override
        public Queue.Executable createExecutable() throws IOException {
            return new Queue.Executable() {
                @Nonnull
                @Override
                public SubTask getParent() {
                    return TestFlyweightTask.this;
                }

                @Override
                public void run() {
                    try {
                        TestFlyweightTask.this.isBuilding = true;
                        Thread.sleep(10000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    TestFlyweightTask.this.isBuilding=false;
                }

                @Override
                public String toString() {
                    return "test";
                }
            };
        }

        @Override
        public String getName() {
            return "test";
        }

        @Override
        public String getFullDisplayName() {
            return "test";
        }

        @Override
        public void checkAbortPermission() {

        }

        @Override
        public boolean hasAbortPermission() {
            return true;
        }

        @Override
        public String getUrl() {
            return "test";
        }

        public Label getAssignedLabel(){
            return label;
        }

    }

    private static class InvalidNode extends Slave {
        // JEP-200 whitelist changes prevent this field (and thus instances of this class) from being serialized.
        private ClassLoader cl = InvalidNode.class.getClassLoader();

        public InvalidNode(String name, String remoteFS, ComputerLauncher launcher) throws Descriptor.FormException, IOException {
            super(name, remoteFS, launcher);
        }
    }
}
