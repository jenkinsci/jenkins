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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.ExtensionList;
import hudson.XmlFile;
import hudson.model.Descriptor;
import hudson.model.Failure;
import hudson.model.Node;
import hudson.model.Saveable;
import hudson.model.Slave;
import hudson.model.listeners.SaveableListener;
import hudson.slaves.ComputerLauncher;
import hudson.slaves.DumbSlave;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import org.hamcrest.Description;
import org.hamcrest.TypeSafeMatcher;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestExtension;
import org.jvnet.hudson.test.recipes.LocalData;

public class NodesTest {
    @Rule
    public JenkinsRule r = new JenkinsRule();

    @Test
    @Issue("JENKINS-50599")
    public void addNodeShouldFailAtomically() throws Exception {
        InvalidNode node = new InvalidNode("foo", "temp", r.createComputerLauncher(null));
        IOException e = assertThrows(
                "Adding the node should have thrown an exception during serialization",
                IOException.class,
                () -> r.jenkins.addNode(node));
        String className = InvalidNode.class.getName();
        assertThat("The exception should be from failing to serialize the node",
                e.getMessage(), containsString("Failed to serialize " + className + "#cl for class " + className));
        assertThat("The node should not exist since #addNode threw an exception",
                r.jenkins.getNode("foo"), nullValue());
    }

    @Test
    @Issue("JENKINS-50599")
    public void addNodeShouldFailAtomicallyWhenReplacingNode() throws Exception {
        Node oldNode = r.createSlave("foo", "", null);
        r.jenkins.addNode(oldNode);
        InvalidNode newNode = new InvalidNode("foo", "temp", r.createComputerLauncher(null));
        IOException e = assertThrows(
                "Adding the node should have thrown an exception during serialization",
                IOException.class,
                () -> r.jenkins.addNode(newNode));
        String className = InvalidNode.class.getName();
        assertThat("The exception should be from failing to serialize the node",
                e.getMessage(), containsString("Failed to serialize " + className + "#cl for class " + className));
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
        ListenerImpl l = ExtensionList.lookupSingleton(ListenerImpl.class);
        assertEquals(0, l.deleted);
        assertEquals(1, l.updated);
        assertEquals(1, l.created);
        var saveableListener = ExtensionList.lookupSingleton(SaveableListenerImpl.class);
        assertEquals(0, saveableListener.deleted);
        r.jenkins.removeNode(newNode);
        assertEquals(1, saveableListener.deleted);
    }

    @TestExtension("addNodeShouldReplaceExistingNode")
    public static final class ListenerImpl extends NodeListener {
        int deleted, updated, created;

        @Override
        protected void onDeleted(Node node) {
            deleted++;
        }

        @Override
        protected void onUpdated(Node oldOne, Node newOne) {
            assertNotSame(oldOne, newOne);
            updated++;
        }

        @Override
        protected void onCreated(Node node) {
            created++;
        }
    }

    @TestExtension("addNodeShouldReplaceExistingNode")
    public static final class SaveableListenerImpl extends SaveableListener {
        int deleted;

        @Override
        public void onDeleted(Saveable o, XmlFile file) {
            deleted++;
        }
    }

    @Test
    @Issue("JENKINS-56403")
    public void replaceNodeShouldRemoveOldNode() throws Exception {
        Node oldNode = r.createSlave("foo", "", null);
        Node newNode = r.createSlave("foo-new", "", null);
        r.jenkins.addNode(oldNode);
        r.jenkins.getNodesObject().replaceNode(oldNode, newNode);
        r.jenkins.getNodesObject().load();
        assertNull(r.jenkins.getNode("foo"));
    }

    @Test
    @Issue("JENKINS-56403")
    public void replaceNodeShouldNotRemoveIdenticalOldNode() throws Exception {
        Node oldNode = r.createSlave("foo", "", null);
        Node newNode = r.createSlave("foo", "", null);
        r.jenkins.addNode(oldNode);
        r.jenkins.getNodesObject().replaceNode(oldNode, newNode);
        r.jenkins.getNodesObject().load();
        assertNotNull(r.jenkins.getNode("foo"));
    }

    private static class InvalidNode extends Slave {
        // JEP-200 whitelist changes prevent this field (and thus instances of this class) from being serialized.
        private ClassLoader cl = InvalidNode.class.getClassLoader();

        InvalidNode(String name, String remoteFS, ComputerLauncher launcher) throws Descriptor.FormException, IOException {
            super(name, remoteFS, launcher);
        }
    }

    @Test
    @Issue("SECURITY-2424")
    public void cannotCreateNodeWithTrailingDot_withoutOtherNode() throws Exception {
        assertThat(r.jenkins.getNodes(), hasSize(0));

        DumbSlave node = new DumbSlave("nodeA.", "temp", r.createComputerLauncher(null));
        Failure e = assertThrows(
                "Adding the node should have thrown an exception during checkGoodName",
                Failure.class,
                () -> r.jenkins.addNode(node));
        assertEquals(hudson.model.Messages.Hudson_TrailingDot(), e.getMessage());

        assertThat(r.jenkins.getNodes(), hasSize(0));
    }

    @Test
    @Issue("SECURITY-2424")
    public void cannotCreateNodeWithTrailingDot_withExistingNode() throws Exception {
        assertThat(r.jenkins.getNodes(), hasSize(0));
        r.createSlave("nodeA", "", null);
        assertThat(r.jenkins.getNodes(), hasSize(1));

        DumbSlave node = new DumbSlave("nodeA.", "temp", r.createComputerLauncher(null));
        Failure e = assertThrows(
                "Adding the node should have thrown an exception during checkGoodName",
                Failure.class,
                () -> r.jenkins.addNode(node));
        assertEquals(hudson.model.Messages.Hudson_TrailingDot(), e.getMessage());

        assertThat(r.jenkins.getNodes(), hasSize(1));
    }

    @Test
    @Issue("SECURITY-2424")
    public void cannotCreateNodeWithTrailingDot_exceptIfEscapeHatchIsSet() throws Exception {
        String propName = Jenkins.NAME_VALIDATION_REJECTS_TRAILING_DOT_PROP;
        String initialValue = System.getProperty(propName);
        System.setProperty(propName, "false");
        try {
            assertThat(r.jenkins.getNodes(), hasSize(0));

            DumbSlave node = new DumbSlave("nodeA.", "temp", r.createComputerLauncher(null));
            r.jenkins.addNode(node);

            assertThat(r.jenkins.getNodes(), hasSize(1));
        } finally {
            if (initialValue == null) {
                System.clearProperty(propName);
            } else {
                System.setProperty(propName, initialValue);
            }
        }
    }

    @Test
    @LocalData
    public void vetoLoad() {
        assertNull("one-node should not have been loaded because vetoed by VetoLoadingNodes", Jenkins.get().getNode("one-node"));
    }

    @TestExtension("vetoLoad")
    public static class VetoLoadingNodes extends NodeListener {
        @Override
        protected boolean allowLoad(@NonNull Node node) {
            // Don't allow loading any node.
            return false;
        }
    }

    @Test
    public void listenersCalledOnSetNodes() throws URISyntaxException, IOException, Descriptor.FormException {
        var agentA = new DumbSlave("nodeA", "temp", r.createComputerLauncher(null));
        var agentB = new DumbSlave("nodeB", "temp", r.createComputerLauncher(null));
        var agentA2 = new DumbSlave("nodeA", "temp2", r.createComputerLauncher(null));
        Jenkins.get().setNodes(List.of(agentA, agentB));
        assertThat(CheckSetNodes.created, containsInAnyOrder("nodeA", "nodeB"));
        assertThat(CheckSetNodes.updated, empty());
        assertThat(CheckSetNodes.deleted, empty());
        Jenkins.get().setNodes(List.of(agentA2));
        assertThat(CheckSetNodes.created, containsInAnyOrder("nodeA", "nodeB"));
        assertThat(CheckSetNodes.updated, contains(new DumbSlaveNameAndRemoteFSMatcher(new DumbSlavePair(agentA, agentA2))));
        assertThat(CheckSetNodes.deleted, contains("nodeB"));
        Jenkins.get().setNodes(List.of());
        assertThat(CheckSetNodes.created, containsInAnyOrder("nodeA", "nodeB"));
        assertThat(CheckSetNodes.updated, contains(new DumbSlaveNameAndRemoteFSMatcher(new DumbSlavePair(agentA, agentA2))));
        assertThat(CheckSetNodes.deleted, containsInAnyOrder("nodeA", "nodeB"));
    }

    private record DumbSlavePair(DumbSlave oldNode, DumbSlave newNode) {
        @Override
        public String toString() {
            return "NodePair{" +
                    "oldNode=" + toStringNode(oldNode) +
                    ", newNode=" + toStringNode(newNode) +
                    '}';
        }

        private String toStringNode(DumbSlave node) {
            return "(name=" + node.getNodeName() + ",remoteFS=" + node.getRemoteFS() + ")";
        }
    }

    @TestExtension("listenersCalledOnSetNodes")
    public static class CheckSetNodes extends NodeListener {
        private static final List<String> created = new ArrayList<>();
        private static final List<DumbSlavePair> updated = new ArrayList<>();
        private static final List<String> deleted = new ArrayList<>();

        @Override
        protected void onCreated(@NonNull Node node) {
            node.getRootDir();
            created.add(node.getNodeName());
        }

        @Override
        protected void onUpdated(@NonNull Node oldOne, @NonNull Node newOne) {
            if (oldOne instanceof DumbSlave oldDumbSlave && newOne instanceof DumbSlave newDumbSlave) {
                updated.add(new DumbSlavePair(oldDumbSlave, newDumbSlave));
            }
        }

        @Override
        protected void onDeleted(@NonNull Node node) {
            deleted.add(node.getNodeName());
        }
    }

    private static class DumbSlaveNameAndRemoteFSMatcher extends TypeSafeMatcher<DumbSlavePair> {
        private final DumbSlavePair expected;

        DumbSlaveNameAndRemoteFSMatcher(DumbSlavePair expected) {
            this.expected = expected;
        }

        @Override
        protected boolean matchesSafely(DumbSlavePair dumbSlavePair) {
            return expected.oldNode.getNodeName().equals(dumbSlavePair.oldNode.getNodeName())
                    && expected.oldNode.getRemoteFS().equals(dumbSlavePair.oldNode.getRemoteFS())
                    && expected.newNode.getNodeName().equals(dumbSlavePair.newNode.getNodeName())
                    && expected.newNode.getRemoteFS().equals(dumbSlavePair.newNode.getRemoteFS());
        }

        @Override
        public void describeTo(Description description) {
            description.appendText("NodePair(").appendValue(expected).appendText(")");
        }
    }
}
