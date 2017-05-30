/*
 * The MIT License
 *
 * Copyright (c) 2016 IKEDA Yasuyuki
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

package jenkins.security;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.Collections;

import org.acegisecurity.Authentication;
import org.apache.commons.lang.StringUtils;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.JenkinsRule.WebClient;
import org.jvnet.hudson.test.MockFolder;
import org.jvnet.hudson.test.MockQueueItemAuthenticator;
import org.jvnet.hudson.test.TestExtension;

import com.gargoylesoftware.htmlunit.FailingHttpStatusCodeException;

import hudson.model.AbstractItem;
import hudson.model.Computer;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Item;
import hudson.model.Job;
import hudson.model.ListView;
import hudson.model.Node;
import hudson.model.ParametersAction;
import hudson.model.ParametersDefinitionProperty;
import hudson.model.StringParameterDefinition;
import hudson.model.StringParameterValue;
import hudson.model.User;
import hudson.model.View;
import hudson.security.GlobalMatrixAuthorizationStrategy;
import hudson.security.Permission;
import hudson.slaves.Cloud;
import hudson.slaves.DumbSlave;
import hudson.slaves.DummyCloudImpl;
import jenkins.model.Jenkins;

/**
 * Tests for {@link AuthorizationStrategyDenyOverride}
 *
 * Tests in integrated ways as possible.
 */
public class AuthorizationStrategyDenyOverrideTest {
    @Rule
    public JenkinsRule j = new JenkinsRule();

    /**
     * Setups basic securities.
     * Sets up following users:
     * * admin     Administrator
     * * developer Can configure any items.
     */
    @Before
    public void enableSecurity() {
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        GlobalMatrixAuthorizationStrategy auth = new GlobalMatrixAuthorizationStrategy();
        auth.add(Jenkins.ADMINISTER, "admin");
        auth.add(Jenkins.READ, "developer");
        auth.add(Item.READ, "developer");
        auth.add(Item.CONFIGURE, "developer");
        auth.add(Item.BUILD, "developer");
        auth.add(View.READ, "developer");
        auth.add(View.CONFIGURE, "developer");
        auth.add(Computer.BUILD, "developer");
        auth.add(Cloud.PROVISION, "developer");
        j.jenkins.setAuthorizationStrategy(auth);
    }

    /**
     * Denies the specified permission of the specified user.
     */
    @TestExtension
    public static class TestDenyJenkins extends AuthorizationStrategyDenyOverride<Jenkins> {
        public String user = null;
        public String permission = null;

        @Override
        public boolean deny(Jenkins item, Authentication a, Permission p) {
            if (StringUtils.isEmpty(user) || StringUtils.isEmpty(permission)) {
                return false;
            }
            if (!permission.equals(p.getId())) {
                return false;
            }
            if (!Jenkins.getInstance().getSecurityRealm().getUserIdStrategy().equals(user, a.getName())) {
                return false;
            }
            // deny!
            return true;
        }
    }

    @Test
    public void testJenkins() throws Exception {
        WebClient wc = j.createWebClient().login("developer");
        wc.goTo("");

        TestDenyJenkins deny = j.jenkins.getExtensionList(AuthorizationStrategyDenyOverride.class).get(TestDenyJenkins.class);
        deny.user = "developer";
        deny.permission = Jenkins.READ.getId();
        try {
            wc.getOptions().setPrintContentOnFailingStatusCode(false);
            wc.goTo("");
            fail();
        } catch(FailingHttpStatusCodeException e) {
            assertEquals(403, e.getStatusCode());
        }
    }

    /**
     * Denies the specified permission to the specified job of the specified user.
     */
    @TestExtension
    public static class TestDenyJob extends AuthorizationStrategyDenyOverride<Job<?, ?>> {
        public String user = null;
        public String permission = null;
        public String job = null;

        @Override
        public boolean deny(Job<?, ?> item, Authentication a, Permission p) {
            if (StringUtils.isEmpty(user) || StringUtils.isEmpty(permission) || StringUtils.isEmpty(job)) {
                return false;
            }
            if (!permission.equals(p.getId())) {
                return false;
            }
            if (!job.equals(item.getFullName())) {
                return false;
            }
            if (!Jenkins.getInstance().getSecurityRealm().getUserIdStrategy().equals(user, a.getName())) {
                return false;
            }
            // deny!
            return true;
        }
    }

    @Test
    public void testJob() throws Exception {
        WebClient wc = j.createWebClient().login("developer");
        FreeStyleProject p = j.createFreeStyleProject();
        wc.getPage(p, "configure");
        TestDenyJob deny = j.jenkins.getExtensionList(AuthorizationStrategyDenyOverride.class).get(TestDenyJob.class);
        deny.user = "developer";
        deny.permission = Job.EXTENDED_READ.getId();
        deny.job = p.getFullName();
        try {
            wc.getOptions().setPrintContentOnFailingStatusCode(false);
            wc.getPage(p, "configure");
            fail();
        } catch(FailingHttpStatusCodeException e) {
            assertEquals(403, e.getStatusCode());
        }
    }

    /**
     * Denies the specified permission to the specified view of the specified user.
     */
    @TestExtension
    public static class TestDenyView extends AuthorizationStrategyDenyOverride<View> {
        public String user = null;
        public String permission = null;
        public String view = null;

        @Override
        public boolean deny(View item, Authentication a, Permission p) {
            if (StringUtils.isEmpty(user) || StringUtils.isEmpty(permission) || StringUtils.isEmpty(view)) {
                return false;
            }
            if (!permission.equals(p.getId())) {
                return false;
            }
            if (!view.equals(item.getViewName())) {
                return false;
            }
            if (!Jenkins.getInstance().getSecurityRealm().getUserIdStrategy().equals(user, a.getName())) {
                return false;
            }
            // deny!
            return true;
        }
    }

    @Test
    public void testView() throws Exception {
        WebClient wc = j.createWebClient().login("developer");
        View v = new ListView("testView");
        j.jenkins.addView(v);
        wc.getPage(v, "configure");
        TestDenyView deny = j.jenkins.getExtensionList(AuthorizationStrategyDenyOverride.class).get(TestDenyView.class);
        deny.user = "developer";
        deny.permission = View.CONFIGURE.getId();
        deny.view = v.getViewName();
        try {
            wc.getOptions().setPrintContentOnFailingStatusCode(false);
            wc.getPage(v, "configure");
            fail();
        } catch(FailingHttpStatusCodeException e) {
            assertEquals(403, e.getStatusCode());
        }
    }

    /**
     * Denies the specified permission to the specified item of the specified user.
     */
    @TestExtension
    public static class TestDenyAbstractItem extends AuthorizationStrategyDenyOverride<AbstractItem> {
        public String user = null;
        public String permission = null;
        public String itemName = null;

        @Override
        public boolean deny(AbstractItem item, Authentication a, Permission p) {
            if (StringUtils.isEmpty(user) || StringUtils.isEmpty(permission) || StringUtils.isEmpty(itemName)) {
                return false;
            }
            if (!permission.equals(p.getId())) {
                return false;
            }
            if (!itemName.equals(item.getFullName())) {
                return false;
            }
            if (!Jenkins.getInstance().getSecurityRealm().getUserIdStrategy().equals(user, a.getName())) {
                return false;
            }
            // deny!
            return true;
        }
    }

    @Test
    public void testAbstractItem() throws Exception {
        // MockFolder doesn't provide GUI,
        // and cannot test in an integrated way.
        MockFolder f = j.createFolder("testFolder");
        assertTrue(f.getACL().hasPermission(User.get("developer").impersonate(), Item.CONFIGURE));
        TestDenyAbstractItem deny = j.jenkins.getExtensionList(AuthorizationStrategyDenyOverride.class).get(TestDenyAbstractItem.class);
        deny.user = "developer";
        deny.permission = Item.CONFIGURE.getId();
        deny.itemName = f.getFullName();
        assertFalse(f.getACL().hasPermission(User.get("developer").impersonate(), Item.CONFIGURE));
    }

    /**
     * Denies the specified permission to the specified user of the specified user.
     */
    @TestExtension
    public static class TestDenyUser extends AuthorizationStrategyDenyOverride<User> {
        public String user = null;
        public String permission = null;
        public String targetUser = null;

        @Override
        public boolean deny(User item, Authentication a, Permission p) {
            if (StringUtils.isEmpty(user) || StringUtils.isEmpty(permission) || StringUtils.isEmpty(targetUser)) {
                return false;
            }
            if (!permission.equals(p.getId())) {
                return false;
            }
            if (!Jenkins.getInstance().getSecurityRealm().getUserIdStrategy().equals(targetUser, item.getId())) {
                return false;
            }
            if (!Jenkins.getInstance().getSecurityRealm().getUserIdStrategy().equals(user, a.getName())) {
                return false;
            }
            // deny!
            return true;
        }
    }

    @Test
    public void testUser() throws Exception {
        WebClient wc = j.createWebClient().login("admin");
        User u = User.get("test");
        wc.goTo(u.getUrl() + "/configure");
        TestDenyUser deny = j.jenkins.getExtensionList(AuthorizationStrategyDenyOverride.class).get(TestDenyUser.class);
        deny.user = "admin";
        deny.permission = Jenkins.ADMINISTER.getId();
        deny.targetUser = "test";

        try {
            wc.getOptions().setPrintContentOnFailingStatusCode(false);
            wc.goTo(u.getUrl() + "/configure");
            fail();
        } catch(FailingHttpStatusCodeException e) {
            assertEquals(403, e.getStatusCode());
        }
    }

    /**
     * Denies the specified permission to the specified computer of the specified user.
     */
    @TestExtension
    public static class TestDenyComputer extends AuthorizationStrategyDenyOverride<Computer> {
        public String user = null;
        public String permission = null;
        public String computer = null;

        @Override
        public boolean deny(Computer item, Authentication a, Permission p) {
            if (StringUtils.isEmpty(user) || StringUtils.isEmpty(permission) || StringUtils.isEmpty(computer)) {
                return false;
            }
            if (!permission.equals(p.getId())) {
                return false;
            }
            if (!computer.equals(item.getName())) {
                return false;
            }
            if (!Jenkins.getInstance().getSecurityRealm().getUserIdStrategy().equals(user, a.getName())) {
                return false;
            }
            // deny!
            return true;
        }
    }

    @Test
    public void testComputer() throws Exception {
        DumbSlave slave = j.createOnlineSlave();

        WebClient wc = j.createWebClient().login("developer");
        wc.getPage(slave, "configure");
        TestDenyComputer deny = j.jenkins.getExtensionList(AuthorizationStrategyDenyOverride.class).get(TestDenyComputer.class);
        deny.user = "developer";
        deny.permission = Computer.CONFIGURE.getId();
        deny.computer = slave.getNodeName();
        try {
            wc.getOptions().setPrintContentOnFailingStatusCode(false);
            wc.getPage(slave, "configure");
            fail();
        } catch(FailingHttpStatusCodeException e) {
            assertEquals(403, e.getStatusCode());
        }
    }

    /**
     * Denies the specified permission to the specified item of the specified user.
     */
    @TestExtension
    public static class TestDenyCloud extends AuthorizationStrategyDenyOverride<Cloud> {
        public String user = null;
        public String permission = null;
        public String cloud = null;

        @Override
        public boolean deny(Cloud item, Authentication a, Permission p) {
            if (StringUtils.isEmpty(user) || StringUtils.isEmpty(permission) || StringUtils.isEmpty(cloud)) {
                return false;
            }
            if (!permission.equals(p.getId())) {
                return false;
            }
            if (!cloud.equals(item.name)) {
                return false;
            }
            if (!Jenkins.getInstance().getSecurityRealm().getUserIdStrategy().equals(user, a.getName())) {
                return false;
            }
            // deny!
            return true;
        }
    }

    @Test
    public void testCloud() throws Exception {
        Cloud c = new DummyCloudImpl(j, 0);
        assertTrue(c.getACL().hasPermission(User.get("developer").impersonate(), Cloud.PROVISION));
        TestDenyCloud deny = j.jenkins.getExtensionList(AuthorizationStrategyDenyOverride.class).get(TestDenyCloud.class);
        deny.user = "developer";
        deny.permission = Cloud.PROVISION.getId();
        deny.cloud = c.name;
        assertFalse(c.getACL().hasPermission(User.get("developer").impersonate(), Cloud.PROVISION));
    }


    /**
     * Denies the specified permission to the specified node of the specified user.
     */
    @TestExtension
    public static class TestDenyNode extends AuthorizationStrategyDenyOverride<Node> {
        public String user = null;
        public String permission = null;
        public String node = null;

        @Override
        public boolean deny(Node item, Authentication a, Permission p) {
            if (StringUtils.isEmpty(user) || StringUtils.isEmpty(permission) || StringUtils.isEmpty(node)) {
                return false;
            }
            if (!permission.equals(p.getId())) {
                return false;
            }
            if (!node.equals(item.getNodeName())) {
                return false;
            }
            if (!Jenkins.getInstance().getSecurityRealm().getUserIdStrategy().equals(user, a.getName())) {
                return false;
            }
            // deny!
            return true;
        }
    }

    @Test
    public void testNode() throws Exception {
        j.jenkins.setNumExecutors(0);
        DumbSlave slave1 = j.createOnlineSlave();
        DumbSlave slave2 = j.createOnlineSlave();

        FreeStyleProject p = j.createFreeStyleProject();
        p.addProperty(new ParametersDefinitionProperty(
            // a parameter not to builds merged in the queue.
            new StringParameterDefinition("UNIQUE", "")
        ));
        QueueItemAuthenticatorConfiguration.get().getAuthenticators().add(
            new MockQueueItemAuthenticator(Collections.singletonMap(
                p.getFullName(),
                User.get("developer").impersonate()
            ))
        );

        TestDenyNode deny = j.jenkins.getExtensionList(AuthorizationStrategyDenyOverride.class).get(TestDenyNode.class);
        deny.user = "developer";
        deny.permission = Computer.BUILD.getId();
        deny.node = slave1.getNodeName();

        final int NUM_BUILDS = 10;

        for (int i = 0; i < NUM_BUILDS; ++i) {
            p.scheduleBuild2(
                0,
                new ParametersAction(
                    new StringParameterValue("UNIQUE", Integer.toString(i))
                )
            );
        }
        j.waitUntilNoActivity();
        int buildnum = 0;
        for (FreeStyleBuild b = p.getLastBuild(); b != null; b = b.getPreviousBuild()) {
            j.assertBuildStatusSuccess(b);
            assertEquals(slave2, b.getBuiltOn());
            ++buildnum;
        }
        assertEquals(NUM_BUILDS, buildnum);
    }

    /**
     * Test that can deny even permissions of administrators.
     *
     * @throws Exception test fails.
     */
    @Test
    public void testOverrideEvenAdminister() throws Exception {
        WebClient wc = j.createWebClient().login("admin");
        FreeStyleProject p = j.createFreeStyleProject();
        wc.getPage(p, "configure");
        TestDenyJob deny = j.jenkins.getExtensionList(AuthorizationStrategyDenyOverride.class).get(TestDenyJob.class);
        deny.user = "admin";
        deny.permission = Job.EXTENDED_READ.getId();
        deny.job = p.getFullName();
        try {
            wc.getOptions().setPrintContentOnFailingStatusCode(false);
            wc.getPage(p, "configure");
            fail();
        } catch(FailingHttpStatusCodeException e) {
            assertEquals(403, e.getStatusCode());
        }
    }

    @Test
    public void testAbstractItemAffectsJob() throws Exception {
        WebClient wc = j.createWebClient().login("developer");
        FreeStyleProject p = j.createFreeStyleProject();
        wc.getPage(p, "configure");
        TestDenyAbstractItem deny = j.jenkins.getExtensionList(AuthorizationStrategyDenyOverride.class).get(TestDenyAbstractItem.class);
        deny.user = "developer";
        deny.permission = Job.EXTENDED_READ.getId();
        deny.itemName = p.getFullName();
        try {
            wc.getOptions().setPrintContentOnFailingStatusCode(false);
            wc.getPage(p, "configure");
            fail();
        } catch(FailingHttpStatusCodeException e) {
            assertEquals(403, e.getStatusCode());
        }
    }

    /**
     * {@link GlobalMatrixAuthorizationStrategy} inherits Jenkins permissions
     * to Job permissions.
     * But the permission overriding mechanism affects only Jenkins permissions,
     * not Job permissions
     *
     * @throws Exception test failed
     */
    @Test
    public void testJenkinsPermissionIsNotInherited() throws Exception {
        WebClient wc = j.createWebClient().login("developer");
        FreeStyleProject p = j.createFreeStyleProject();
        wc.getPage(p, "configure");
        TestDenyJenkins deny = j.jenkins.getExtensionList(AuthorizationStrategyDenyOverride.class).get(TestDenyJenkins.class);
        deny.user = "developer";
        deny.permission = Job.EXTENDED_READ.getId();
        wc.getPage(p, "configure");
    }

    /**
     * Denies the specified permission to the specified free style project of the specified user.
     *
     * Unfortunately, this doesn't work.
     */
    @TestExtension
    public static class TestDenyFreeStyleProject extends AuthorizationStrategyDenyOverride<FreeStyleProject> {
        public String user = null;
        public String permission = null;
        public String project = null;

        @Override
        public boolean deny(FreeStyleProject item, Authentication a, Permission p) {
            if (StringUtils.isEmpty(user) || StringUtils.isEmpty(permission) || StringUtils.isEmpty(project)) {
                return false;
            }
            if (!permission.equals(p.getId())) {
                return false;
            }
            if (!project.equals(item.getFullName())) {
                return false;
            }
            if (!Jenkins.getInstance().getSecurityRealm().getUserIdStrategy().equals(user, a.getName())) {
                return false;
            }
            // deny!
            return true;
        }
    }

    /**
     * AuthorizationStrategyDenyOverride with specific classes
     * like AuthorizationStrategyDenyOverride<FreeStyleProject>
     * doesn't work.
     *
     * @throws Exception test fails
     */
    @Test
    public void testSpecificClassOverrideDontWork() throws Exception {
        WebClient wc = j.createWebClient().login("developer");
        FreeStyleProject p = j.createFreeStyleProject();
        wc.getPage(p, "configure");
        TestDenyFreeStyleProject deny = j.jenkins.getExtensionList(AuthorizationStrategyDenyOverride.class).get(TestDenyFreeStyleProject.class);
        deny.user = "developer";
        deny.permission = Job.EXTENDED_READ.getId();
        deny.project = p.getFullName();
        wc.getPage(p, "configure");
    }
}
