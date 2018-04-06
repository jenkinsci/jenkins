/*
 * The MIT License
 *
 * Copyright (c) 2018 Intel Corporation
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import hudson.remoting.Callable;
import jenkins.model.Jenkins;
import hudson.model.Node.Mode;
import hudson.slaves.NodeProperty;
import hudson.slaves.NodeDescriptor;
import hudson.slaves.NodePropertyDescriptor;
import hudson.security.Permission;
import hudson.XmlFile;
import hudson.Launcher;
import hudson.FilePath;
import hudson.model.labels.LabelAtom;
import hudson.util.ClockDifference;
import hudson.util.DescribableList;

import org.acegisecurity.Authentication;

import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.any;

import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.io.File;
import java.io.IOException;
import java.util.Random;
import java.util.Set;
import java.util.Collections;
import java.util.Calendar;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.junit.Test;
import org.junit.Before;
import org.junit.runner.RunWith;

/**
 * @author Jacob Keller
 */
@PrepareForTest(Jenkins.class)
@RunWith(PowerMockRunner.class)
public class NodeTest {

    static class DummyNode extends Node {
        String nodeName = Long.toString(new Random().nextLong());
        public String getNodeName() {
            return nodeName;
        }

        public void setNodeName(String name) {
            throw new UnsupportedOperationException();
        }

        public String getNodeDescription() {
            throw new UnsupportedOperationException();
        }

        public Launcher createLauncher(TaskListener listener) {
            throw new UnsupportedOperationException();
        }

        public int getNumExecutors() {
            throw new UnsupportedOperationException();
        }

        public Mode getMode() {
            throw new UnsupportedOperationException();
        }

        public Computer createComputer() {
            throw new UnsupportedOperationException();
        }

        public Set<LabelAtom> getAssignedLabels() {
            throw new UnsupportedOperationException();
        }

        public String getLabelString() {
            throw new UnsupportedOperationException();
        }

        public void setLabelString(String labelString) throws IOException {
            throw new UnsupportedOperationException();
        }

        public FilePath getWorkspaceFor(TopLevelItem item) {
            throw new UnsupportedOperationException();
        }

        public FilePath getRootPath() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Callable<ClockDifference, IOException> getClockDifferenceCallable() {
            throw new UnsupportedOperationException();
        }

        public NodeDescriptor getDescriptor() {
            throw new UnsupportedOperationException();
        }

        @Override
        public DescribableList<NodeProperty<?>, NodePropertyDescriptor> getNodeProperties() {
            throw new UnsupportedOperationException();
        }
    }

    static class EphemeralNode extends DummyNode implements hudson.slaves.EphemeralNode {
        public Node asNode() {
            return this;
        }
    }

    @Mock private Jenkins jenkins;

    @Before public void setUp() {
        MockitoAnnotations.initMocks(this);

        /* Setup mocked Jenkins static */
        PowerMockito.mockStatic(Jenkins.class);
        PowerMockito.when(Jenkins.getInstance()).thenReturn(jenkins);
        PowerMockito.when(Jenkins.getActiveInstance()).thenReturn(jenkins);

        /* Make sure the jenkins instance is marked exclusive */
        when(jenkins.getMode()).thenReturn(Mode.EXCLUSIVE);
    }

    @Test
    public void testExclusiveNodeCantTakeFlyweight() throws Exception {
        Node node = new DummyNode();
        Node spyNode = spy(node);

        /* Set up our node properties */
        doReturn(true).when(spyNode).hasPermission(any(), any());

        DescribableList<NodeProperty<?>, NodePropertyDescriptor> propList = null;
        Saveable owner = mock(Saveable.class);
        propList = new DescribableList(owner, Collections.emptyList());

        doReturn(propList).when(spyNode).getNodeProperties();
        doReturn(true).when(spyNode).isAcceptingTasks();
        doReturn(Mode.EXCLUSIVE).when(spyNode).getMode();

        /* Setup a buildable item */
        Calendar timestamp = mock(Calendar.class);
        Queue.FlyweightTask project = mock(Queue.FlyweightTask.class);
        when(project.getAssignedLabel()).thenReturn(null);
        List<Action> actions = Collections.<Action>emptyList();
        Queue.WaitingItem wItem = new Queue.WaitingItem(timestamp, project, actions);
        Queue.BuildableItem item = new Queue.BuildableItem(wItem);

        /* No other nodes, master is exclusive */
        when(jenkins.getNodes()).thenReturn(Collections.<Node>emptyList());
        assertEquals(null, spyNode.canTake(item));

        /* One other node, not exclusive */
        Node normalNode = mock(Node.class);
        when(normalNode.getMode()).thenReturn(Mode.NORMAL);
        when(jenkins.getNodes()).thenReturn(Collections.singletonList(normalNode));
        assertNotEquals(null, spyNode.canTake(item));

        /* One other node, exclusive */
        Node exclusiveNode = mock(Node.class);
        when(exclusiveNode.getMode()).thenReturn(Mode.EXCLUSIVE);
        when(jenkins.getNodes()).thenReturn(Collections.singletonList(exclusiveNode));
        assertEquals(null, spyNode.canTake(item));
    }
}
