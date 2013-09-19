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

import hudson.model.Queue.WaitingItem;
import org.jvnet.hudson.test.TestExtension;
import java.util.List;
import java.util.ArrayList;
import hudson.util.TagCloud.Entry;
import hudson.util.TagCloud;
import hudson.model.labels.LabelAtom;
import hudson.model.queue.CauseOfBlockage;
import hudson.security.GlobalMatrixAuthorizationStrategy;
import hudson.security.HudsonPrivateSecurityRealm;
import hudson.slaves.ComputerListener;
import hudson.slaves.NodeProperty;
import hudson.slaves.OfflineCause;
import hudson.slaves.OfflineCause.ByCLI;
import java.util.Collection;
import java.util.GregorianCalendar;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import static org.junit.Assert.*;
import jenkins.model.Jenkins;
import org.acegisecurity.context.SecurityContextHolder;
import jenkins.security.QueueItemAuthenticator;
import jenkins.security.QueueItemAuthenticatorConfiguration;
import org.acegisecurity.Authentication;
import org.acegisecurity.GrantedAuthority;
import org.acegisecurity.providers.UsernamePasswordAuthenticationToken;
import hudson.FilePath;
import hudson.model.Node.Mode;
import hudson.security.Permission;
import hudson.slaves.DumbSlave;
import org.junit.Before;

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
    public void testGetLabelCloud() throws Exception {
        Node node = j.createOnlineSlave();
        node.setLabelString("label1 label2");
        FreeStyleProject project = j.createFreeStyleProject();
        project.setAssignedLabel(j.jenkins.getLabel("label1"));
        TagCloud<LabelAtom> cloud = node.getLabelCloud();
        for(int i =0; i< cloud.size(); i ++){
            Entry e = cloud.get(i);
            if(e.item.equals(j.jenkins.getLabel("label1"))){
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
        notTake = false;
        QueueItemAuthenticatorConfiguration.get().getAuthenticators().add(new QueueItemAuthenticatorImpl());
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
        User user = User.get("John Smith");
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
    
    @TestExtension
    public static class QueueItemAuthenticatorImpl extends QueueItemAuthenticator {
        @Override
        public Authentication authenticate(Queue.Item item) {
            return new UsernamePasswordAuthenticationToken("John ","John",new GrantedAuthority[0]);
        }
    }
}
