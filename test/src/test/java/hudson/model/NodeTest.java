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
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.EnvVars;
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
import hudson.agents.ComputerListener;
import hudson.agents.DumbAgent;
import hudson.agents.NodeProperty;
import hudson.agents.OfflineCause;
import hudson.util.TagCloud;
import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.Collection;
import java.util.GregorianCalendar;
import java.util.List;
import jenkins.model.Jenkins;
import jenkins.security.QueueItemAuthenticatorConfiguration;
import org.htmlunit.HttpMethod;
import org.htmlunit.Page;
import org.htmlunit.WebRequest;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MockQueueItemAuthenticator;
import org.jvnet.hudson.test.TestExtension;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 *
 * @author Lucie Votypkova
 */
public class NodeTest {

    @Rule public JenkinsRule j = new JenkinsRule();
    public static boolean addDynamicLabel = false;
    public static boolean notTake = false;

    @Before
    public void before() {
       addDynamicLabel = false;
       notTake = false;
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
    }

    @Test
    public void testSetTemporaryOfflineCause() throws Exception {
        Node node = j.createOnlineAgent();
        FreeStyleProject project = j.createFreeStyleProject();
        project.setAssignedLabel(j.jenkins.getLabel(node.getDisplayName()));
        OfflineCause cause = new OfflineCause.ByCLI("message");
        node.setTemporaryOfflineCause(cause);
        for (ComputerListener l : ComputerListener.all()) {
            l.onOnline(node.toComputer(), TaskListener.NULL);
        }
        assertEquals("Node should have offline cause which was set.", cause, node.toComputer().getOfflineCause());
        OfflineCause cause2 = new OfflineCause.ByCLI("another message");
        node.setTemporaryOfflineCause(cause2);
        assertEquals("Node should have the new offline cause.", cause2, node.toComputer().getOfflineCause());
        // Exists in some plugins
        node.toComputer().setTemporarilyOffline(false, new OfflineCause.ByCLI("A third message"));
        assertThat(node.getTemporaryOfflineCause(), nullValue());
    }

    @Test
    public void testOfflineCause() throws Exception {
        Node node = j.createOnlineAgent();
        Computer computer = node.toComputer();
        OfflineCause.UserCause cause;

        final User someone = User.getOrCreateByIdOrFullName("someone@somewhere.com");
        try (ACLContext ignored = ACL.as2(someone.impersonate2())) {
            computer.doToggleOffline("original message");
            cause = (OfflineCause.UserCause) computer.getOfflineCause();
            assertThat(computer.getOfflineCauseReason(), is("original message"));
            assertThat(computer.getTemporaryOfflineCauseReason(), is("original message"));
            assertTrue(cause.toString(), cause.toString().matches("^.*?Disconnected by someone@somewhere.com : original message"));
            assertEquals(someone, cause.getUser());
        }
        final User root = User.getOrCreateByIdOrFullName("root@localhost");
        try (ACLContext ignored = ACL.as2(root.impersonate2())) {
            computer.doChangeOfflineCause("new message");
            cause = (OfflineCause.UserCause) computer.getOfflineCause();
            assertThat(computer.getTemporaryOfflineCauseReason(), is("new message"));
            assertTrue(cause.toString(), cause.toString().matches("^.*?Disconnected by root@localhost : new message"));
            assertEquals(root, cause.getUser());

            computer.doToggleOffline(null);
            assertNull(computer.getOfflineCause());
        }
    }

    @Test
    public void testOfflineCauseAsAnonymous() throws Exception {
        Node node = j.createOnlineAgent();
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
    public void testGetLabelCloud() throws Exception {
        Node node = j.createOnlineAgent();
        node.setLabelString("label1 label2");
        FreeStyleProject project = j.createFreeStyleProject();
        final Label label = j.jenkins.getLabel("label1");
        project.setAssignedLabel(label);
        label.reset(); // Make sure cached value is not used
        TagCloud<LabelAtom> cloud = node.getLabelCloud();
        for (TagCloud.Entry e : cloud) {
            if (e.item.equals(label)) {
                assertEquals("Label label1 should have one tied project.", 1, e.weight, 0);
            } else {
                assertEquals("Label " + e.item + " should not have any tied project.", 0, e.weight, 0);
            }
        }

    }

    @Test
    public void testGetAssignedLabels() throws Exception {
        Node node = j.createOnlineAgent();
        node.setLabelString("label1 label2");
        LabelAtom notContained = j.jenkins.getLabelAtom("notContained");
        addDynamicLabel = true;
        assertTrue("Node should have label1.", node.getAssignedLabels().contains(j.jenkins.getLabelAtom("label1")));
        assertTrue("Node should have label2.", node.getAssignedLabels().contains(j.jenkins.getLabelAtom("label2")));
        assertTrue("Node should have dynamically added dynamicLabel.", node.getAssignedLabels().contains(j.jenkins.getLabelAtom("dynamicLabel")));
        assertFalse("Node should not have label notContained.", node.getAssignedLabels().contains(notContained));
        assertTrue("Node should have self label.", node.getAssignedLabels().contains(node.getSelfLabel()));
    }

    @Test
    public void testCanTake() throws Exception {
        Agent node = j.createOnlineAgent();
        node.setLabelString("label1 label2");
        FreeStyleProject project = j.createFreeStyleProject();
        project.setAssignedLabel(j.jenkins.getLabel("label1"));
        FreeStyleProject project2 = j.createFreeStyleProject();
        FreeStyleProject project3 = j.createFreeStyleProject();
        project3.setAssignedLabel(j.jenkins.getLabel("notContained"));
        Queue.BuildableItem item = new Queue.BuildableItem(new WaitingItem(new GregorianCalendar(), project, new ArrayList<>()));
        Queue.BuildableItem item2 = new Queue.BuildableItem(new WaitingItem(new GregorianCalendar(), project2, new ArrayList<>()));
        Queue.BuildableItem item3 = new Queue.BuildableItem(new WaitingItem(new GregorianCalendar(), project3, new ArrayList<>()));
        assertNull("Node should take project which is assigned to its label.", node.canTake(item));
        assertNull("Node should take project which is assigned to its label.", node.canTake(item2));
        assertNotNull("Node should not take project which is not assigned to its label.", node.canTake(item3));
        String message = Messages._Node_LabelMissing(node.getNodeName(), j.jenkins.getLabel("notContained")).toString();
        assertEquals("Cause of blockage should be missing label.", message, node.canTake(item3).getShortDescription());
        node.setMode(Node.Mode.EXCLUSIVE);
        assertNotNull("Node should not take project which has null label because it is in exclusive mode.", node.canTake(item2));
        message = Messages._Node_BecauseNodeIsReserved(node.getNodeName()).toString();
        assertEquals("Cause of blockage should be reserved label.", message, node.canTake(item2).getShortDescription());
        node.getNodeProperties().add(new NodePropertyImpl());
        notTake = true;
        assertNotNull("Node should not take project because node property does not allow it.", node.canTake(item));
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
        assertNotNull("Node should not take project because user does not have build permission.", node.canTake(item));
        message = Messages._Node_LackingBuildPermission(item.authenticate2().getName(), node.getNodeName()).toString();
        assertEquals("Cause of blockage should be build permission label.", message, node.canTake(item).getShortDescription());
    }

    @Test
    public void testCreatePath() throws Exception {
        Agent node = j.createOnlineAgent();
        Node node2 = j.createAgent();
        String absolutePath = node.remoteFS;
        FilePath path = node.createPath(absolutePath);
        assertNotNull("Path should be created.", path);
        assertNotNull("Channel should be set.", path.getChannel());
        assertEquals("Channel should be equals to channel of node.", node.getChannel(), path.getChannel());
        path = node2.createPath(absolutePath);
        assertNull("Path should be null if agent have channel null.", path);
    }

    @Test
    public void testHasPermission() throws Exception {
        Node node = j.createOnlineAgent();
        GlobalMatrixAuthorizationStrategy auth = new GlobalMatrixAuthorizationStrategy();
        j.jenkins.setAuthorizationStrategy(auth);
        j.jenkins.setCrumbIssuer(null);
        HudsonPrivateSecurityRealm realm = new HudsonPrivateSecurityRealm(false);
        j.jenkins.setSecurityRealm(realm);
        User user = realm.createAccount("John Smith", "abcdef");
        SecurityContextHolder.getContext().setAuthentication(user.impersonate2());
        assertFalse("Current user should not have permission read.", node.hasPermission(Permission.READ));
        auth.add(Computer.CONFIGURE, user.getId());
        assertTrue("Current user should have permission CONFIGURE.", user.hasPermission(Permission.CONFIGURE));
        auth.add(Jenkins.ADMINISTER, user.getId());
        assertTrue("Current user should have permission read, because he has permission administer.", user.hasPermission(Permission.READ));
        SecurityContextHolder.getContext().setAuthentication(Jenkins.ANONYMOUS2);

        user = User.getOrCreateByIdOrFullName("anonymous");
        assertFalse("Current user should not have permission read, because does not have global permission read and authentication is anonymous.", user.hasPermission(Permission.READ));
    }

    @Test
    public void testGetChannel() throws Exception {
        Agent agent = j.createOnlineAgent();
        Node nodeOffline = j.createAgent();
        Node node = new DumbAgent("agent2", "description", agent.getRemoteFS(), "1", Mode.NORMAL, "", agent.getLauncher(), agent.getRetentionStrategy(), agent.getNodeProperties());
        assertNull("Channel of node should be null because node has not assigned computer.", node.getChannel());
        assertNull("Channel of node should be null because assigned computer is offline.", nodeOffline.getChannel());
        assertNotNull("Channel of node should not be null.", agent.getChannel());
    }

    @Test
    public void testToComputer() throws Exception {
        Agent agent = j.createOnlineAgent();
        Node node = new DumbAgent("agent2", "description", agent.getRemoteFS(), "1", Mode.NORMAL, "", agent.getLauncher(), agent.getRetentionStrategy(), agent.getNodeProperties());
        assertNull("Agent which is not added into Jenkins list nodes should not have assigned computer.", node.toComputer());
        assertNotNull("Agent which is added into Jenkins list nodes should have assigned computer.", agent.toComputer());
    }

    @Issue("JENKINS-27188")
    @Test public void envPropertiesImmutable() throws Exception {
        Agent agent = j.createAgent();

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
    public void testGetAssignedLabelWithLabelOrExpression() throws Exception {
        Node node = j.createOnlineAgent();
        node.setLabelString("label1 label2");

        FreeStyleProject project = j.createFreeStyleProject();
        project.setAssignedLabel(new LabelExpression.Or(j.jenkins.getLabel("label1"), j.jenkins.getLabel("label2")));

        TagCloud<LabelAtom> cloud = node.getLabelCloud();
        assertThatCloudLabelContains(cloud, "label1", 0);
        assertThatCloudLabelContains(cloud, "label2", 0);
    }

    @Issue("JENKINS-26391")
    @Test
    public void testGetAssignedLabelWithLabelAndExpression() throws Exception {
        Node node = j.createOnlineAgent();
        node.setLabelString("label1 label2");

        FreeStyleProject project = j.createFreeStyleProject();
        project.setAssignedLabel(new LabelExpression.And(j.jenkins.getLabel("label1"), j.jenkins.getLabel("label2")));

        TagCloud<LabelAtom> cloud = node.getLabelCloud();
        assertThatCloudLabelContains(cloud, "label1", 0);
        assertThatCloudLabelContains(cloud, "label2", 0);
    }

    @Issue("JENKINS-26391")
    @Test
    public void testGetAssignedLabelWithBothAndOrExpression() throws Exception {
        Node n1 = j.createOnlineAgent();
        Node n2 = j.createOnlineAgent();
        Node n3 = j.createOnlineAgent();
        Node n4 = j.createOnlineAgent();

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
    public void testGetAssignedLabelWithSpaceOnly() throws Exception {
        Node n = j.createOnlineAgent();
        n.setLabelString("label1 label2");

        FreeStyleProject p = j.createFreeStyleProject();
        p.setAssignedLabel(j.jenkins.getLabel("label1 label2"));

        TagCloud<LabelAtom> cloud = n.getLabelCloud();
        assertThatCloudLabelDoesNotContain(cloud, "label1 label2", 0);
    }

    @Issue("SECURITY-281")
    @Test
    public void builtInComputerConfigDotXml() throws Exception {
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
        assertFalse(containsFailureMessage + "]", contains);
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
