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

import com.gargoylesoftware.htmlunit.FailingHttpStatusCodeException;
import com.gargoylesoftware.htmlunit.HttpMethod;
import com.gargoylesoftware.htmlunit.Page;
import com.gargoylesoftware.htmlunit.WebRequestSettings;
import hudson.EnvVars;
import hudson.FilePath;
import hudson.maven.MavenModuleSet;
import hudson.model.Node.Mode;
import hudson.model.Queue.WaitingItem;
import hudson.model.labels.*;
import hudson.model.queue.CauseOfBlockage;
import hudson.security.ACL;
import hudson.security.GlobalMatrixAuthorizationStrategy;
import hudson.security.HudsonPrivateSecurityRealm;
import hudson.security.Permission;
import hudson.slaves.ComputerListener;
import hudson.slaves.DumbSlave;
import hudson.slaves.NodeProperty;
import hudson.slaves.OfflineCause;
import hudson.slaves.OfflineCause.ByCLI;
import hudson.slaves.OfflineCause.UserCause;
import hudson.util.TagCloud;
import java.net.HttpURLConnection;

import java.util.*;
import java.util.concurrent.Callable;

import jenkins.model.Jenkins;
import jenkins.security.QueueItemAuthenticatorConfiguration;
import org.acegisecurity.context.SecurityContextHolder;
import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.RunLoadCounter;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MockQueueItemAuthenticator;
import org.jvnet.hudson.test.TestExtension;

/**
 *
 * @author Lucie Votypkova
 */
public class NodeTest {

    @Rule public JenkinsRule j = new JenkinsRule();
    public static boolean addDynamicLabel = false;
    public static boolean notTake = false;

    @Before
    public void before(){
       addDynamicLabel = false;
       notTake = false;
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
    }

    @Test
    public void testSetTemporaryOfflineCause() throws Exception {
        Node node = j.createOnlineSlave();
        FreeStyleProject project = j.createFreeStyleProject();
        project.setAssignedLabel(j.jenkins.getLabel(node.getDisplayName()));
        OfflineCause cause = new ByCLI("message");
        node.setTemporaryOfflineCause(cause);
        for(ComputerListener l : ComputerListener.all()){
            l.onOnline(node.toComputer(), TaskListener.NULL);
        }
        assertEquals("Node should have offline cause which was set.", cause, node.toComputer().getOfflineCause());
        OfflineCause cause2 = new ByCLI("another message");
        node.setTemporaryOfflineCause(cause2);
        assertEquals("Node should have original offline cause after setting another.", cause, node.toComputer().getOfflineCause());
    }

    @Test
    public void testOfflineCause() throws Exception {
        Node node = j.createOnlineSlave();
        Computer computer = node.toComputer();
        OfflineCause.UserCause cause;

        final User someone = User.get("someone@somewhere.com");
        ACL.impersonate(someone.impersonate());

        computer.doToggleOffline("original message");
        cause = (UserCause) computer.getOfflineCause();
        assertTrue(cause.toString(), cause.toString().matches("^.*?Disconnected by someone@somewhere.com : original message"));
        assertEquals(someone, cause.getUser());

        final User root = User.get("root@localhost");
        ACL.impersonate(root.impersonate());

        computer.doChangeOfflineCause("new message");
        cause = (UserCause) computer.getOfflineCause();
        assertTrue(cause.toString(), cause.toString().matches("^.*?Disconnected by root@localhost : new message"));
        assertEquals(root, cause.getUser());

        computer.doToggleOffline(null);
        assertNull(computer.getOfflineCause());
    }

    @Test
    public void testGetLabelCloud() throws Exception {
        Node node = j.createOnlineSlave();
        node.setLabelString("label1 label2");
        FreeStyleProject project = j.createFreeStyleProject();
        final Label label = j.jenkins.getLabel("label1");
        project.setAssignedLabel(label);
        label.reset(); // Make sure cached value is not used
        TagCloud<LabelAtom> cloud = node.getLabelCloud();
        for(int i =0; i< cloud.size(); i ++){
            TagCloud.Entry e = cloud.get(i);
            if(e.item.equals(label)){
                assertEquals("Label label1 should have one tied project.", 1, e.weight, 0);
            }
            else{
                assertEquals("Label " + e.item + " should not have any tied project.", 0, e.weight, 0);
            }
        }

    }

    @Test
    public void testGetAssignedLabels() throws Exception {
        Node node = j.createOnlineSlave();
        node.setLabelString("label1 label2");
        LabelAtom notContained = j.jenkins.getLabelAtom("notContained");
        addDynamicLabel = true;
        assertTrue("Node should have label1.", node.getAssignedLabels().contains(j.jenkins.getLabelAtom("label1")));
        assertTrue("Node should have label2.", node.getAssignedLabels().contains(j.jenkins.getLabelAtom("label2")));
        assertTrue("Node should have dynamicly added dynamicLabel.", node.getAssignedLabels().contains(j.jenkins.getLabelAtom("dynamicLabel")));
        assertFalse("Node should not have label notContained.", node.getAssignedLabels().contains(notContained));
        assertTrue("Node should have self label.", node.getAssignedLabels().contains(node.getSelfLabel()));
    }

    @Test
    public void testCanTake() throws Exception {
        Node node = j.createOnlineSlave();
        node.setLabelString("label1 label2");
        FreeStyleProject project = j.createFreeStyleProject();
        project.setAssignedLabel(j.jenkins.getLabel("label1"));
        FreeStyleProject project2 = j.createFreeStyleProject();
        FreeStyleProject project3 = j.createFreeStyleProject();
        project3.setAssignedLabel(j.jenkins.getLabel("notContained"));
        Queue.BuildableItem item = new Queue.BuildableItem(new WaitingItem(new GregorianCalendar(), project, new ArrayList<Action>()));
        Queue.BuildableItem item2 = new Queue.BuildableItem(new WaitingItem(new GregorianCalendar(), project2, new ArrayList<Action>()));
        Queue.BuildableItem item3 = new Queue.BuildableItem(new WaitingItem(new GregorianCalendar(), project3, new ArrayList<Action>()));
        assertNull("Node should take project which is assigned to its label.", node.canTake(item));
        assertNull("Node should take project which is assigned to its label.", node.canTake(item2));
        assertNotNull("Node should not take project which is not assigned to its label.", node.canTake(item3));
        String message = Messages._Node_LabelMissing(node.getNodeName(),j.jenkins.getLabel("notContained")).toString();
        assertEquals("Cause of blockage should be missing label.", message, node.canTake(item3).getShortDescription());
        ((Slave)node).setMode(Node.Mode.EXCLUSIVE);
        assertNotNull("Node should not take project which has null label bacause it is in exclusive mode.", node.canTake(item2));
        message = Messages._Node_BecauseNodeIsReserved(node.getNodeName()).toString();
        assertEquals("Cause of blockage should be reserved label.", message, node.canTake(item2).getShortDescription());
        node.getNodeProperties().add(new NodePropertyImpl());
        notTake = true;
        assertNotNull("Node should not take project because node property not alow it.", node.canTake(item));
        assertTrue("Cause of blockage should be bussy label.", node.canTake(item) instanceof CauseOfBlockage.BecauseLabelIsBusy);
        User user = User.get("John");
        GlobalMatrixAuthorizationStrategy auth = new GlobalMatrixAuthorizationStrategy();
        j.jenkins.setAuthorizationStrategy(auth);
        j.jenkins.setCrumbIssuer(null);
        HudsonPrivateSecurityRealm realm = new HudsonPrivateSecurityRealm(false);
        j.jenkins.setSecurityRealm(realm);
        realm.createAccount("John", "");
        notTake = false;
        QueueItemAuthenticatorConfiguration.get().getAuthenticators().add(new MockQueueItemAuthenticator(Collections.singletonMap(project.getFullName(), user.impersonate())));
        assertNotNull("Node should not take project because user does not have build permission.", node.canTake(item));
        message = Messages._Node_LackingBuildPermission(item.authenticate().getName(),node.getNodeName()).toString();
        assertEquals("Cause of blockage should be bussy label.", message, node.canTake(item).getShortDescription());
    }

    @Test
    public void testCreatePath() throws Exception {
        Node node = j.createOnlineSlave();
        Node node2 = j.createSlave();
        String absolutPath = ((Slave)node).remoteFS;
        FilePath path = node.createPath(absolutPath);
        assertNotNull("Path should be created.", path);
        assertNotNull("Channel should be set.", path.getChannel());
        assertEquals("Channel should be equals to channel of node.", node.getChannel(), path.getChannel());
        path = node2.createPath(absolutPath);
        assertNull("Path should be null if slave have channel null.", path);
    }

    @Test
    public void testHasPermission() throws Exception {
        Node node = j.createOnlineSlave();
        GlobalMatrixAuthorizationStrategy auth = new GlobalMatrixAuthorizationStrategy();
        j.jenkins.setAuthorizationStrategy(auth);
        j.jenkins.setCrumbIssuer(null);
        HudsonPrivateSecurityRealm realm = new HudsonPrivateSecurityRealm(false);
        j.jenkins.setSecurityRealm(realm);
        User user = realm.createAccount("John Smith","abcdef");
        SecurityContextHolder.getContext().setAuthentication(user.impersonate());
        assertFalse("Current user should not have permission read.", node.hasPermission(Permission.READ));
        auth.add(Computer.CONFIGURE, user.getId());
        assertTrue("Current user should have permission CONFIGURE.", user.hasPermission(Permission.CONFIGURE));
        auth.add(Jenkins.ADMINISTER, user.getId());
        assertTrue("Current user should have permission read, because he has permission administer.", user.hasPermission(Permission.READ));
        SecurityContextHolder.getContext().setAuthentication(Jenkins.ANONYMOUS);

        user = User.get("anonymous");
        assertFalse("Current user should not have permission read, because does not have global permission read and authentication is anonymous.", user.hasPermission(Permission.READ));
    }

    @Test
    public void testGetChannel() throws Exception {
        Slave slave = j.createOnlineSlave();
        Node nodeOffline = j.createSlave();
        Node node = new DumbSlave("slave2", "description", slave.getRemoteFS(), "1", Mode.NORMAL, "", slave.getLauncher(), slave.getRetentionStrategy(), slave.getNodeProperties());
        assertNull("Channel of node should be null because node has not assigned computer.", node.getChannel());
        assertNull("Channel of node should be null because assigned computer is offline.", nodeOffline.getChannel());
        assertNotNull("Channel of node should not be null.", slave.getChannel());
    }

    @Test
    public void testToComputer() throws Exception {
        Slave slave = j.createOnlineSlave();
        Node node = new DumbSlave("slave2", "description", slave.getRemoteFS(), "1", Mode.NORMAL, "", slave.getLauncher(), slave.getRetentionStrategy(), slave.getNodeProperties());
        assertNull("Slave which is not added into Jenkins list nodes should not have assigned computer.", node.toComputer());
        assertNotNull("Slave which is added into Jenkins list nodes should have assigned computer.", slave.toComputer());
    }

    /**
     * Verify that the Label#getTiedJobCount does not perform a lazy loading operation.
     */
    @Issue("JENKINS-26391")
    @Test
    public void testGetAssignedLabelWithJobs() throws Exception {
        final Node node = j.createOnlineSlave();
        node.setLabelString("label1 label2");
        MavenModuleSet mavenProject = j.jenkins.createProject(MavenModuleSet.class, "p");
        mavenProject.setAssignedLabel(j.jenkins.getLabel("label1"));
        RunLoadCounter.prepare(mavenProject);
        j.assertBuildStatus(Result.FAILURE, mavenProject.scheduleBuild2(0).get());
        Integer labelCount = RunLoadCounter.assertMaxLoads(mavenProject, 0, new Callable<Integer>() {
            @Override
            public Integer call() throws Exception {
                final Label label = j.jenkins.getLabel("label1");
                label.reset(); // Make sure cached value is not used
                return label.getTiedJobCount();
            }
        });

        assertEquals("Should have only one job tied to label.",
                1, labelCount.intValue());
    }

    @Issue("JENKINS-27188")
    @Test public void envPropertiesImmutable() throws Exception {
        Slave slave = j.createSlave();

        String propertyKey = "JENKINS-27188";
        EnvVars envVars = slave.getComputer().getEnvironment();
        envVars.put(propertyKey, "huuhaa");
        assertTrue(envVars.containsKey(propertyKey));
        assertFalse(slave.getComputer().getEnvironment().containsKey(propertyKey));

        assertNotSame(slave.getComputer().getEnvironment(), slave.getComputer().getEnvironment());
    }

    /**
     * Create two projects which have the same label and verify that both are accounted for when getting a count
     * of the jobs tied to the current label.
     *
     */
    @Issue("JENKINS-26391")
    @Test
    public void testGetAssignedLabelMultipleSlaves() throws Exception {
        final Node node1 = j.createOnlineSlave();
        node1.setLabelString("label1");
        final Node node2 = j.createOnlineSlave();
        node1.setLabelString("label1");

        MavenModuleSet project = j.jenkins.createProject(MavenModuleSet.class, "p1");
        final Label label = j.jenkins.getLabel("label1");
        project.setAssignedLabel(label);
        j.assertBuildStatus(Result.FAILURE, project.scheduleBuild2(0).get());

        MavenModuleSet project2 = j.jenkins.createProject(MavenModuleSet.class, "p2");
        project2.setAssignedLabel(label);
        j.assertBuildStatus(Result.FAILURE, project2.scheduleBuild2(0).get());

        label.reset(); // Make sure cached value is not used
        assertEquals("Two jobs should be tied to this label.", 2, label.getTiedJobCount());
    }

    /**
     * Verify that when a label is removed from a job that the tied job count does not include the removed job.
     */
    @Issue("JENKINS-26391")
    @Test
    public void testGetAssignedLabelWhenLabelRemoveFromProject() throws Exception {
        final Node node = j.createOnlineSlave();
        node.setLabelString("label1");

        MavenModuleSet project = j.jenkins.createProject(MavenModuleSet.class, "p");
        final Label label = j.jenkins.getLabel("label1");
        project.setAssignedLabel(label);
        j.assertBuildStatus(Result.FAILURE, project.scheduleBuild2(0).get());

        project.setAssignedLabel(null);
        label.reset(); // Make sure cached value is not used
        assertEquals("Label1 should have no tied jobs after the job label was removed.", 0, label.getTiedJobCount());
    }

    /**
     * Create a project with the OR label expression.
     */
    @Issue("JENKINS-26391")
    @Test
    public void testGetAssignedLabelWithLabelOrExpression() throws Exception {
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
    public void testGetAssignedLabelWithLabelAndExpression() throws Exception {
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
    public void testGetAssignedLabelWithBothAndOrExpression() throws Exception {
        Node n1 = j.createOnlineSlave();
        Node n2 = j.createOnlineSlave();
        Node n3 = j.createOnlineSlave();
        Node n4 = j.createOnlineSlave();

        n1.setLabelString("label1 label2 label3");
        n2.setLabelString("label1");
        n3.setLabelString("label1 label2");
        n4.setLabelString("label1 label");

        FreeStyleProject p = j.createFreeStyleProject();
        p.setAssignedLabel(LabelExpression.parseExpression("label1 && (label2 || label3)"));

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
        Node n = j.createOnlineSlave();
        n.setLabelString("label1 label2");

        FreeStyleProject p = j.createFreeStyleProject();
        p.setAssignedLabel(j.jenkins.getLabel("label1 label2"));

        TagCloud<LabelAtom> cloud = n.getLabelCloud();
        assertThatCloudLabelDoesNotContain(cloud, "label1 label2", 0);
    }

    @Issue("SECURITY-281")
    @Test
    public void masterComputerConfigDotXml() throws Exception {
        JenkinsRule.WebClient wc = j.createWebClient();
        wc.assertFails("computer/(master)/config.xml", HttpURLConnection.HTTP_BAD_REQUEST);
        WebRequestSettings settings = new WebRequestSettings(wc.createCrumbedUrl("computer/(master)/config.xml"));
        settings.setHttpMethod(HttpMethod.POST);
        settings.setRequestBody("<hudson/>");
        try {
            Page page = wc.getPage(settings);
            fail(page.getWebResponse().getContentAsString());
        } catch (FailingHttpStatusCodeException x) {
            assertEquals(HttpURLConnection.HTTP_BAD_REQUEST, x.getStatusCode());
        }
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
            containsFailureMessage.append("{").append(entry.item.toString()).append(", ").append(entry.weight).append("}");
        }

        // If a label should be part of the cloud label then fail.
        if (contains) {
            fail(containsFailureMessage.toString() + "]");
        }
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
    public static class LabelFinderImpl extends LabelFinder{

        @Override
        public Collection<LabelAtom> findLabels(Node node) {
            List<LabelAtom> atoms = new ArrayList<LabelAtom>();
            if(addDynamicLabel){
                atoms.add(Jenkins.getInstance().getLabelAtom("dynamicLabel"));
            }
            return atoms;

        }

    }

    @TestExtension
    public static class NodePropertyImpl extends NodeProperty{

        @Override
        public CauseOfBlockage canTake(Queue.BuildableItem item){
            if(notTake)
                return new CauseOfBlockage.BecauseLabelIsBusy(item.getAssignedLabel());
            return null;
        }
    }

}
