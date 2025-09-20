/*
 * The MIT License
 *
 * Copyright 2013 Red Hat, Inc.
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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.EnvVars;
import hudson.ExtensionList;
import hudson.FilePath;
import hudson.model.Node.Mode;
import hudson.model.Queue.WaitingItem;
import hudson.model.labels.LabelAtom;
import hudson.model.labels.LabelExpression;
import hudson.model.queue.CauseOfBlockage;
import hudson.security.ACL;
import hudson.security.ACLContext;
import hudson.security.GlobalMatrixAuthorizationStrategy;
import hudson.security.HudsonPrivateSecurityRealm;
import hudson.security.Permission;
import hudson.slaves.ComputerListener;
import hudson.slaves.DumbSlave;
import hudson.slaves.NodeProperty;
import hudson.slaves.OfflineCause;
import hudson.util.TagCloud;
import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.Collection;
import java.util.GregorianCalendar;
import java.util.List;
import jenkins.model.Jenkins;
import jenkins.model.NodeListener;
import jenkins.security.QueueItemAuthenticatorConfiguration;
import org.htmlunit.HttpMethod;
import org.htmlunit.Page;
import org.htmlunit.WebRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MockQueueItemAuthenticator;
import org.jvnet.hudson.test.TestExtension;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 *
 * @author Lucie Votypkova
 */
@WithJenkins
class NodeTest {

    private static boolean addDynamicLabel = false;
    private static boolean notTake = false;

    private JenkinsRule j;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        j = rule;
        addDynamicLabel = false;
        notTake = false;
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
    }

    @TestExtension("testSetTemporaryOfflineCause")
    public static class NodeListenerImpl extends NodeListener {
        private int count;

        public static int getCount() {
            return ExtensionList.lookupSingleton(NodeListenerImpl.class).count;
        }

        @Override
        protected void onUpdated(@NonNull Node oldOne, @NonNull Node newOne) {
            count++;
        }
    }

    @Test
    void testSetTemporaryOfflineCause() throws Exception {
        Node node = j.createOnlineSlave();
        FreeStyleProject project = j.createFreeStyleProject();
        project.setAssignedLabel(j.jenkins.getLabel(node.getDisplayName()));
        OfflineCause cause = new OfflineCause.ByCLI("message");
        node.setTemporaryOfflineCause(cause);
        assertThat(NodeListenerImpl.getCount(), is(1));
        for (ComputerListener l : ComputerListener.all()) {
            l.onOnline(node.toComputer(), TaskListener.NULL);
        }
        assertEquals(cause, node.toComputer().getOfflineCause(), "Node should have offline cause which was set.");
        OfflineCause cause2 = new OfflineCause.ByCLI("another message");
        node.setTemporaryOfflineCause(cause2);
        assertThat(NodeListenerImpl.getCount(), is(2));
        assertEquals(cause2, node.toComputer().getOfflineCause(), "Node should have the new offline cause.");
        // Exists in some plugins
        node.toComputer().setTemporarilyOffline(false, new OfflineCause.ByCLI("A third message"));
        assertThat(node.getTemporaryOfflineCause(), nullValue());
        assertThat(NodeListenerImpl.getCount(), is(3));
    }

    @Test
    void testOfflineCause() throws Exception {
        Node node = j.createOnlineSlave();
        Computer computer = node.toComputer();
        OfflineCause.UserCause cause;

        final User someone = User.getOrCreateByIdOrFullName("someone@somewhere.com");
        try (ACLContext ignored = ACL.as2(someone.impersonate2())) {
            computer.doToggleOffline("original message");
            cause = (OfflineCause.UserCause) computer.getOfflineCause();
            assertThat(computer.getOfflineCauseReason(), is("original message"));
            assertThat(computer.getTemporaryOfflineCauseReason(), is("original message"));
            assertTrue(cause.toString().matches("^.*?Disconnected by someone@somewhere.com : original message"), cause.toString());
            assertEquals(someone, cause.getUser());
        }
        final User root = User.getOrCreateByIdOrFullName("root@localhost");
        try (ACLContext ignored = ACL.as2(root.impersonate2())) {
            computer.doChangeOfflineCause("new message");
            cause = (OfflineCause.UserCause) computer.getOfflineCause();
            assertThat(computer.getTemporaryOfflineCauseReason(), is("new message"));
            assertTrue(cause.toString().matches("^.*?Disconnected by root@localhost : new message"), cause.toString());
            assertEquals(root, cause.getUser());

            computer.doToggleOffline(null);
            assertNull(computer.getOfflineCause());
        }
    }

    @Test
    void testOfflineCauseAsAnonymous() throws Exception {
        Node node = j.createOnlineSlave();
        final Computer computer = node.toComputer();
        OfflineCause.UserCause cause;
        try (ACLContext ctxt = ACL.as2(Jenkins.ANONYMOUS2)) {
            computer.doToggleOffline("original message");
        }

        cause = (OfflineCause.UserCause) computer.getOfflineCause();
        assertThat(cause.toString(), endsWith("Disconnected by anonymous : original message"));
        assertEquals(User.getUnknown(), cause.getUser());


        final User root = User.get("root@localhost");
        try (ACLContext ctxt = ACL.as2(root.impersonate2())) {
            computer.doChangeOfflineCause("new message");
        }
        cause = (OfflineCause.UserCause) computer.getOfflineCause();
        assertThat(cause.toString(), endsWith("Disconnected by root@localhost : new message"));
        assertEquals(root, cause.getUser());

        computer.doToggleOffline(null);
        assertNull(computer.getOfflineCause());
    }

    @Test
    void testGetLabelCloud() throws Exception {
        Node node = j.createOnlineSlave();
        node.setLabelString("label1 label2");
        FreeStyleProject project = j.createFreeStyleProject();
        final Label label = j.jenkins.getLabel("label1");
        project.setAssignedLabel(label);
        label.reset(); // Make sure cached value is not used
        TagCloud<LabelAtom> cloud = node.getLabelCloud();
        for (TagCloud.Entry e : cloud) {
            if (e.item.equals(label)) {
                assertEquals(1, e.weight, 0, "Label label1 should have one tied project.");
            } else {
                assertEquals(0, e.weight, 0, "Label " + e.item + " should not have any tied project.");
            }
        }

    }

    @Test
    void testGetAssignedLabels() throws Exception {
        Node node = j.createOnlineSlave();
        node.setLabelString("label1 label2");
        LabelAtom notContained = j.jenkins.getLabelAtom("notContained");
        addDynamicLabel = true;
        assertTrue(node.getAssignedLabels().contains(j.jenkins.getLabelAtom("label1")), "Node should have label1.");
        assertTrue(node.getAssignedLabels().contains(j.jenkins.getLabelAtom("label2")), "Node should have label2.");
        assertTrue(node.getAssignedLabels().contains(j.jenkins.getLabelAtom("dynamicLabel")), "Node should have dynamically added dynamicLabel.");
        assertFalse(node.getAssignedLabels().contains(notContained), "Node should not have label notContained.");
        assertTrue(node.getAssignedLabels().contains(node.getSelfLabel()), "Node should have self label.");
    }

    @Test
    void testCanTake() throws Exception {
        Slave node = j.createOnlineSlave();
        node.setLabelString("label1 label2");
        FreeStyleProject project = j.createFreeStyleProject();
        project.setAssignedLabel(j.jenkins.getLabel("label1"));
        FreeStyleProject project2 = j.createFreeStyleProject();
        FreeStyleProject project3 = j.createFreeStyleProject();
        project3.setAssignedLabel(j.jenkins.getLabel("notContained"));
        Queue.BuildableItem item = new Queue.BuildableItem(new WaitingItem(new GregorianCalendar(), project, new ArrayList<>()));
        Queue.BuildableItem item2 = new Queue.BuildableItem(new WaitingItem(new GregorianCalendar(), project2, new ArrayList<>()));
        Queue.BuildableItem item3 = new Queue.BuildableItem(new WaitingItem(new GregorianCalendar(), project3, new ArrayList<>()));
        assertNull(node.canTake(item), "Node should take project which is assigned to its label.");
        assertNull(node.canTake(item2), "Node should take project which is assigned to its label.");
        assertNotNull(node.canTake(item3), "Node should not take project which is not assigned to its label.");
        String message = Messages._Node_LabelMissing(node.getNodeName(), j.jenkins.getLabel("notContained")).toString();
        assertEquals(message, node.canTake(item3).getShortDescription(), "Cause of blockage should be missing label.");
        node.setMode(Node.Mode.EXCLUSIVE);
        assertNotNull(node.canTake(item2), "Node should not take project which has null label because it is in exclusive mode.");
        message = Messages._Node_BecauseNodeIsReserved(node.getNodeName()).toString();
        assertEquals(message, node.canTake(item2).getShortDescription(), "Cause of blockage should be reserved label.");
        node.getNodeProperties().add(new NodePropertyImpl());
        notTake = true;
        assertNotNull(node.canTake(item), "Node should not take project because node property does not allow it.");
        assertThat("Cause of blockage should be busy label.", node.canTake(item), instanceOf(CauseOfBlockage.BecauseLabelIsBusy.class));
        User user = User.get("John");
        GlobalMatrixAuthorizationStrategy auth = new GlobalMatrixAuthorizationStrategy();
        j.jenkins.setAuthorizationStrategy(auth);
        j.jenkins.setCrumbIssuer(null);
        HudsonPrivateSecurityRealm realm = new HudsonPrivateSecurityRealm(false);
        j.jenkins.setSecurityRealm(realm);
        realm.createAccount("John", "");
        notTake = false;
        QueueItemAuthenticatorConfiguration.get().getAuthenticators().add(new MockQueueItemAuthenticator().authenticate(project.getFullName(), user.impersonate2()));
        assertNotNull(node.canTake(item), "Node should not take project because user does not have build permission.");
        message = Messages._Node_LackingBuildPermission(item.authenticate2().getName(), node.getNodeName()).toString();
        assertEquals(message, node.canTake(item).getShortDescription(), "Cause of blockage should be build permission label.");
    }

    @Test
    void testCreatePath() throws Exception {
        Slave node = j.createOnlineSlave();
        Node node2 = j.createSlave();
        String absolutePath = node.remoteFS;
        FilePath path = node.createPath(absolutePath);
        assertNotNull(path, "Path should be created.");
        assertNotNull(path.getChannel(), "Channel should be set.");
        assertEquals(node.getChannel(), path.getChannel(), "Channel should be equals to channel of node.");
        path = node2.createPath(absolutePath);
        assertNull(path, "Path should be null if agent have channel null.");
    }

    @Test
    void testHasPermission() throws Exception {
        Node node = j.createOnlineSlave();
        GlobalMatrixAuthorizationStrategy auth = new GlobalMatrixAuthorizationStrategy();
        j.jenkins.setAuthorizationStrategy(auth);
        j.jenkins.setCrumbIssuer(null);
        HudsonPrivateSecurityRealm realm = new HudsonPrivateSecurityRealm(false);
        j.jenkins.setSecurityRealm(realm);
        User user = realm.createAccount("John Smith", "abcdef");
        SecurityContextHolder.getContext().setAuthentication(user.impersonate2());
        assertFalse(node.hasPermission(Permission.READ), "Current user should not have permission read.");
        auth.add(Computer.CONFIGURE, user.getId());
        assertTrue(user.hasPermission(Permission.CONFIGURE), "Current user should have permission CONFIGURE.");
        auth.add(Jenkins.ADMINISTER, user.getId());
        assertTrue(user.hasPermission(Permission.READ), "Current user should have permission read, because he has permission administer.");
        SecurityContextHolder.getContext().setAuthentication(Jenkins.ANONYMOUS2);

        user = User.getOrCreateByIdOrFullName("anonymous");
        assertFalse(user.hasPermission(Permission.READ), "Current user should not have permission read, because does not have global permission read and authentication is anonymous.");
    }

    @Test
    void testGetChannel() throws Exception {
        Slave agent = j.createOnlineSlave();
        Node nodeOffline = j.createSlave();
        Node node = new DumbSlave("agent2", "description", agent.getRemoteFS(), "1", Mode.NORMAL, "", agent.getLauncher(), agent.getRetentionStrategy(), agent.getNodeProperties());
        assertNull(node.getChannel(), "Channel of node should be null because node has not assigned computer.");
        assertNull(nodeOffline.getChannel(), "Channel of node should be null because assigned computer is offline.");
        assertNotNull(agent.getChannel(), "Channel of node should not be null.");
    }

    @Test
    void testToComputer() throws Exception {
        Slave agent = j.createOnlineSlave();
        Node node = new DumbSlave("agent2", "description", agent.getRemoteFS(), "1", Mode.NORMAL, "", agent.getLauncher(), agent.getRetentionStrategy(), agent.getNodeProperties());
        assertNull(node.toComputer(), "Agent which is not added into Jenkins list nodes should not have assigned computer.");
        assertNotNull(agent.toComputer(), "Agent which is added into Jenkins list nodes should have assigned computer.");
    }

    @Issue("JENKINS-27188")
    @Test
    void envPropertiesImmutable() throws Exception {
        Slave agent = j.createSlave();

        String propertyKey = "JENKINS-27188";
        EnvVars envVars = agent.getComputer().getEnvironment();
        envVars.put(propertyKey, "huuhaa");
        assertTrue(envVars.containsKey(propertyKey));
        assertFalse(agent.getComputer().getEnvironment().containsKey(propertyKey));

        assertNotSame(agent.getComputer().getEnvironment(), agent.getComputer().getEnvironment());
    }

    /**
     * Create a project with the OR label expression.
     */
    @Issue("JENKINS-26391")
    @Test
    void testGetAssignedLabelWithLabelOrExpression() throws Exception {
        Node node = j.createOnlineSlave();
        node.setLabelString("label1 label2");

        FreeStyleProject project = j.createFreeStyleProject();
        project.setAssignedLabel(new LabelExpression.Or(j.jenkins.getLabel("label1"), j.jenkins.getLabel("label2")));

        TagCloud<LabelAtom> cloud = node.getLabelCloud();
        assertThatCloudLabelContains(cloud, "label1", 0);
        assertThatCloudLabelContains(cloud, "label2", 0);
    }

    @Issue("JENKINS-26391")
    @Test
    void testGetAssignedLabelWithLabelAndExpression() throws Exception {
        Node node = j.createOnlineSlave();
        node.setLabelString("label1 label2");

        FreeStyleProject project = j.createFreeStyleProject();
        project.setAssignedLabel(new LabelExpression.And(j.jenkins.getLabel("label1"), j.jenkins.getLabel("label2")));

        TagCloud<LabelAtom> cloud = node.getLabelCloud();
        assertThatCloudLabelContains(cloud, "label1", 0);
        assertThatCloudLabelContains(cloud, "label2", 0);
    }

    @Issue("JENKINS-26391")
    @Test
    void testGetAssignedLabelWithBothAndOrExpression() throws Exception {
        Node n1 = j.createOnlineSlave();
        Node n2 = j.createOnlineSlave();
        Node n3 = j.createOnlineSlave();
        Node n4 = j.createOnlineSlave();

        n1.setLabelString("label1 label2 label3");
        n2.setLabelString("label1");
        n3.setLabelString("label1 label2");
        n4.setLabelString("label1 label");

        FreeStyleProject p = j.createFreeStyleProject();
        p.setAssignedLabel(Label.parseExpression("label1 && (label2 || label3)"));

        // Node 1 should not be tied to any labels
        TagCloud<LabelAtom> n1LabelCloud = n1.getLabelCloud();
        assertThatCloudLabelContains(n1LabelCloud, "label1", 0);
        assertThatCloudLabelContains(n1LabelCloud, "label2", 0);
        assertThatCloudLabelContains(n1LabelCloud, "label3", 0);

        // Node 2 should not be tied to any labels
        TagCloud<LabelAtom> n2LabelCloud = n1.getLabelCloud();
        assertThatCloudLabelContains(n2LabelCloud, "label1", 0);

        // Node 3 should not be tied to any labels
        TagCloud<LabelAtom> n3LabelCloud = n1.getLabelCloud();
        assertThatCloudLabelContains(n3LabelCloud, "label1", 0);
        assertThatCloudLabelContains(n3LabelCloud, "label2", 0);

        // Node 4 should not be tied to any labels
        TagCloud<LabelAtom> n4LabelCloud = n1.getLabelCloud();
        assertThatCloudLabelContains(n4LabelCloud, "label1", 0);
    }

    @Issue("JENKINS-26391")
    @Test
    void testGetAssignedLabelWithSpaceOnly() throws Exception {
        Node n = j.createOnlineSlave();
        n.setLabelString("label1 label2");

        FreeStyleProject p = j.createFreeStyleProject();
        p.setAssignedLabel(j.jenkins.getLabel("label1 label2"));

        TagCloud<LabelAtom> cloud = n.getLabelCloud();
        assertThatCloudLabelDoesNotContain(cloud, "label1 label2", 0);
    }

    @Issue("SECURITY-281")
    @Test
    void builtInComputerConfigDotXml() throws Exception {
        JenkinsRule.WebClient wc = j.createWebClient();
        wc.assertFails("computer/(built-in)/config.xml", HttpURLConnection.HTTP_BAD_REQUEST);
        WebRequest settings = new WebRequest(wc.createCrumbedUrl("computer/(built-in)/config.xml"));
        settings.setHttpMethod(HttpMethod.POST);
        settings.setRequestBody("<hudson/>");

        wc.setThrowExceptionOnFailingStatusCode(false);
        Page page = wc.getPage(settings);
        assertEquals(HttpURLConnection.HTTP_BAD_REQUEST, page.getWebResponse().getStatusCode());
    }

    /**
     * Assert that a tag cloud contains label name and weight.
     */
    public void assertThatCloudLabel(boolean contains, TagCloud<LabelAtom> tagCloud, String expectedLabel, int expectedWeight) {
        StringBuilder containsFailureMessage = new StringBuilder().append("Unable to find label cloud. Expected: [")
                .append(expectedLabel).append(", ").append(expectedWeight).append("]").append(" Actual: [");

        for (TagCloud.Entry entry : tagCloud) {
            if (expectedLabel.equals(((LabelAtom) entry.item).getName())) {
                if (expectedWeight == entry.weight) {
                    if (!contains) {
                        fail("Cloud label should not contain [" + expectedLabel + ", " + expectedWeight + "]");
                    } else {
                        return;
                    }
                }
            }

            // Gather information for failure message just in case.
            containsFailureMessage.append("{").append(entry.item).append(", ").append(entry.weight).append("}");
        }

        // If a label should be part of the cloud label then fail.
        assertFalse(contains, containsFailureMessage + "]");
    }

    /**
     * Assert that a tag cloud does not contain the label name and weight.
     */
    public void assertThatCloudLabelDoesNotContain(TagCloud<LabelAtom> tagCloud, String expectedLabel, int expectedWeight) {
        assertThatCloudLabel(false, tagCloud, expectedLabel, expectedWeight);
    }

    /**
     * Assert that a tag cloud contains label name and weight.
     */
    public void assertThatCloudLabelContains(TagCloud<LabelAtom> tagCloud, String expectedLabel, int expectedWeight) {
        assertThatCloudLabel(true, tagCloud, expectedLabel, expectedWeight);
    }

    @TestExtension
    public static class LabelFinderImpl extends LabelFinder {

        @NonNull
        @Override
        public Collection<LabelAtom> findLabels(@NonNull Node node) {
            List<LabelAtom> atoms = new ArrayList<>();
            if (addDynamicLabel) {
                atoms.add(Jenkins.get().getLabelAtom("dynamicLabel"));
            }
            return atoms;

        }

    }

    @TestExtension
    public static class NodePropertyImpl extends NodeProperty {

        @Override
        public CauseOfBlockage canTake(Queue.BuildableItem item) {
            if (notTake)
                return new CauseOfBlockage.BecauseLabelIsBusy(item.getAssignedLabel());
            return null;
        }
    }

}
