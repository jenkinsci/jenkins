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
import org.jvnet.hudson.test.MockFolder;
import org.jvnet.hudson.test.MockQueueItemAuthenticator;
import org.jvnet.hudson.test.TestExtension;
import org.jvnet.hudson.test.JenkinsRule.WebClient;
import org.kohsuke.stapler.DataBoundConstructor;

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
 * Tests for {@link AuthorizationStrategyOverride} and {@link AuthorizationStrategyOverrideConfiguration}
 */
public class AuthorizationStrategyOverrideTest {
    @Rule
    public JenkinsRule j = new JenkinsRule();

    /**
     * Setups basic securities.
     * Sets up following users:
     * * admin          Administrator
     * * developer      Can configure any items.
     * * user           Can read any items.
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
        auth.add(Jenkins.READ, "user");
        auth.add(Item.READ, "user");
        auth.add(Item.BUILD, "user");
        auth.add(View.READ, "user");
        j.jenkins.setAuthorizationStrategy(auth);
    }

    /**
     * Allows or denies the specified permission of the specified user.
     */
    public static class TestOverrideJenkins extends AuthorizationStrategyOverride<Jenkins> {
        private final String user;
        private final String permission;
        private final boolean result;

        @DataBoundConstructor
        public TestOverrideJenkins(String user, String permission, boolean result) {
            this.user = user;
            this.permission = permission;
            this.result = result;
        }

        @Override
        public Boolean hasPermission(Jenkins item, Authentication a, Permission p) {
            if (StringUtils.isEmpty(user) || StringUtils.isEmpty(permission)) {
                return null;
            }
            if (!permission.equals(p.getId())) {
                return null;
            }
            if (!Jenkins.getInstance().getSecurityRealm().getUserIdStrategy().equals(user, a.getName())) {
                return null;
            }
            // decide the permission
            return result;
        }

        @TestExtension
        public static class DescriptorImpl extends AuthorizationStrategyOverrideDescriptor {
        }
    }

    @Test
    public void testJenkinsAllow() throws Exception {
        WebClient wc = j.createWebClient().login("user");
        try {
            wc.getOptions().setPrintContentOnFailingStatusCode(false);
            wc.goTo("configure");
        } catch(FailingHttpStatusCodeException e) {
            assertEquals(403, e.getStatusCode());
        }

        AuthorizationStrategyOverrideConfiguration.get().getOverrides().
            add(new TestOverrideJenkins("user", Jenkins.ADMINISTER.getId(), true));
        wc.getOptions().setPrintContentOnFailingStatusCode(true);
        wc.goTo("configure");
    }

    @Test
    public void testJenkinsDeny() throws Exception {
        WebClient wc = j.createWebClient().login("admin");
        wc.goTo("configure");

        AuthorizationStrategyOverrideConfiguration.get().getOverrides().
            add(new TestOverrideJenkins("admin", Jenkins.ADMINISTER.getId(), false));

        try {
            wc.getOptions().setPrintContentOnFailingStatusCode(false);
            wc.goTo("configure");
        } catch(FailingHttpStatusCodeException e) {
            assertEquals(403, e.getStatusCode());
        }
    }

    /**
     * Allows or denies the specified permission to the specified job of the specified user.
     */
    public static class TestOverrideJob extends AuthorizationStrategyOverride<Job<?, ?>> {
        private final String user;
        private final String job;
        private final String permission;
        private final boolean result;

        @DataBoundConstructor
        public TestOverrideJob(String user, String job, String permission, boolean result) {
            this.user = user;
            this.job = job;
            this.permission = permission;
            this.result = result;
        }

        @Override
        public Boolean hasPermission(Job<?, ?> item, Authentication a, Permission p) {
            if (StringUtils.isEmpty(user) || StringUtils.isEmpty(permission) || StringUtils.isEmpty(job)) {
                return null;
            }
            if (!permission.equals(p.getId())) {
                return null;
            }
            if (!job.equals(item.getFullName())) {
                return null;
            }
            if (!Jenkins.getInstance().getSecurityRealm().getUserIdStrategy().equals(user, a.getName())) {
                return null;
            }
            // decide the permission
            return result;
        }

        @TestExtension
        public static class DescriptorImpl extends AuthorizationStrategyOverrideDescriptor {
        }
    }

    @Test
    public void testJobAllow() throws Exception {
        WebClient wc = j.createWebClient().login("user");
        FreeStyleProject p = j.createFreeStyleProject();
        try {
            wc.getOptions().setPrintContentOnFailingStatusCode(false);
            wc.getPage(p, "configure");
            fail();
        } catch(FailingHttpStatusCodeException e) {
            assertEquals(403, e.getStatusCode());
        }

        AuthorizationStrategyOverrideConfiguration.get().getOverrides().
            add(new TestOverrideJob("user", p.getFullName(), Job.EXTENDED_READ.getId(), true));
        AuthorizationStrategyOverrideConfiguration.get().getOverrides().
            add(new TestOverrideJob("user", p.getFullName(), Job.CONFIGURE.getId(), true));

        wc.getOptions().setPrintContentOnFailingStatusCode(true);
        wc.getPage(p, "configure");
    }

    @Test
    public void testJobDeny() throws Exception {
        WebClient wc = j.createWebClient().login("developer");
        FreeStyleProject p = j.createFreeStyleProject();
        wc.getPage(p, "configure");

        AuthorizationStrategyOverrideConfiguration.get().getOverrides().
            add(new TestOverrideJob("developer", p.getFullName(), Job.EXTENDED_READ.getId(), false));

        try {
            wc.getOptions().setPrintContentOnFailingStatusCode(false);
            wc.getPage(p, "configure");
            fail();
        } catch(FailingHttpStatusCodeException e) {
            assertEquals(403, e.getStatusCode());
        }
    }

    /**
     * Allows or denies the specified permission to the specified view of the specified user.
     */
    public static class TestOverrideView extends AuthorizationStrategyOverride<View> {
        private final String user;
        private final String view;
        private final String permission;
        private final boolean result;

        @DataBoundConstructor
        public TestOverrideView(String user, String view, String permission, boolean result) {
            this.user = user;
            this.view = view;
            this.permission = permission;
            this.result = result;
        }

        @Override
        public Boolean hasPermission(View item, Authentication a, Permission p) {
            if (StringUtils.isEmpty(user) || StringUtils.isEmpty(permission) || StringUtils.isEmpty(view)) {
                return null;
            }
            if (!permission.equals(p.getId())) {
                return null;
            }
            if (!view.equals(item.getViewName())) {
                return null;
            }
            if (!Jenkins.getInstance().getSecurityRealm().getUserIdStrategy().equals(user, a.getName())) {
                return null;
            }
            // decide the permission
            return result;
        }

        @TestExtension
        public static class DescriptorImpl extends AuthorizationStrategyOverrideDescriptor {
        }
    }

    @Test
    public void testViewAllow() throws Exception {
        WebClient wc = j.createWebClient().login("user");
        View v = new ListView("testView");
        j.jenkins.addView(v);
        try {
            wc.getOptions().setPrintContentOnFailingStatusCode(false);
            wc.getPage(v, "configure");
            fail();
        } catch(FailingHttpStatusCodeException e) {
            assertEquals(403, e.getStatusCode());
        }

        AuthorizationStrategyOverrideConfiguration.get().getOverrides().
            add(new TestOverrideView("user", v.getViewName(), View.CONFIGURE.getId(), true));

        wc.getOptions().setPrintContentOnFailingStatusCode(true);
        wc.getPage(v, "configure");
    }

    @Test
    public void testViewDeny() throws Exception {
        WebClient wc = j.createWebClient().login("developer");
        View v = new ListView("testView");
        j.jenkins.addView(v);
        wc.getPage(v, "configure");

        AuthorizationStrategyOverrideConfiguration.get().getOverrides().
            add(new TestOverrideView("developer", v.getViewName(), View.CONFIGURE.getId(), false));

        try {
            wc.getOptions().setPrintContentOnFailingStatusCode(false);
            wc.getPage(v, "configure");
            fail();
        } catch(FailingHttpStatusCodeException e) {
            assertEquals(403, e.getStatusCode());
        }
    }

    /**
     * Allows or denies the specified permission to the specified item of the specified user.
     */
    public static class TestOverrideAbstractItem extends AuthorizationStrategyOverride<AbstractItem> {
        private final String user;
        private final String itemName;
        private final String permission;
        private final boolean result;

        @DataBoundConstructor
        public TestOverrideAbstractItem(String user, String itemName, String permission, boolean result) {
            this.user = user;
            this.itemName = itemName;
            this.permission = permission;
            this.result = result;
        }

        @Override
        public Boolean hasPermission(AbstractItem item, Authentication a, Permission p) {
            if (StringUtils.isEmpty(user) || StringUtils.isEmpty(permission) || StringUtils.isEmpty(itemName)) {
                return null;
            }
            if (!permission.equals(p.getId())) {
                return null;
            }
            if (!itemName.equals(item.getFullName())) {
                return null;
            }
            if (!Jenkins.getInstance().getSecurityRealm().getUserIdStrategy().equals(user, a.getName())) {
                return null;
            }
            // decide the permission
            return result;
        }

        @TestExtension
        public static class DescriptorImpl extends AuthorizationStrategyOverrideDescriptor {
        }
    }

    @Test
    public void testAbstractItemAllow() throws Exception {
        // MockFolder doesn't provide GUI,
        // and cannot test in an integrated way.
        MockFolder f = j.createFolder("testFolder");
        assertFalse(f.getACL().hasPermission(User.get("user").impersonate(), Item.CONFIGURE));

        AuthorizationStrategyOverrideConfiguration.get().getOverrides().
            add(new TestOverrideAbstractItem("user", f.getFullName(), Item.CONFIGURE.getId(), true));

        assertTrue(f.getACL().hasPermission(User.get("user").impersonate(), Item.CONFIGURE));
    }

    @Test
    public void testAbstractItemDeny() throws Exception {
        // MockFolder doesn't provide GUI,
        // and cannot test in an integrated way.
        MockFolder f = j.createFolder("testFolder");
        assertTrue(f.getACL().hasPermission(User.get("developer").impersonate(), Item.CONFIGURE));

        AuthorizationStrategyOverrideConfiguration.get().getOverrides().
            add(new TestOverrideAbstractItem("developer", f.getFullName(), Item.CONFIGURE.getId(), false));

        assertFalse(f.getACL().hasPermission(User.get("developer").impersonate(), Item.CONFIGURE));
    }

    /**
     * Allows or denies the specified permission to the specified user of the specified user.
     */
    public static class TestOverrideUser extends AuthorizationStrategyOverride<User> {
        private final String user;
        private final String targetUser;
        private final String permission;
        private final boolean result;

        @DataBoundConstructor
        public TestOverrideUser(String user, String targetUser, String permission, boolean result) {
            this.user = user;
            this.targetUser = targetUser;
            this.permission = permission;
            this.result = result;
        }

        @Override
        public Boolean hasPermission(User item, Authentication a, Permission p) {
            if (StringUtils.isEmpty(user) || StringUtils.isEmpty(permission) || StringUtils.isEmpty(targetUser)) {
                return null;
            }
            if (!permission.equals(p.getId())) {
                return null;
            }
            if (!Jenkins.getInstance().getSecurityRealm().getUserIdStrategy().equals(targetUser, item.getId())) {
                return null;
            }
            if (!Jenkins.getInstance().getSecurityRealm().getUserIdStrategy().equals(user, a.getName())) {
                return null;
            }
            // decide the permission
            return result;
        }

        @TestExtension
        public static class DescriptorImpl extends AuthorizationStrategyOverrideDescriptor {
        }
    }

    @Test
    public void testUserAllow() throws Exception {
        WebClient wc = j.createWebClient().login("user");
        User u = User.get("test");
        try {
            wc.getOptions().setPrintContentOnFailingStatusCode(false);
            wc.goTo(u.getUrl() + "/configure");
            fail();
        } catch(FailingHttpStatusCodeException e) {
            assertEquals(403, e.getStatusCode());
        }

        AuthorizationStrategyOverrideConfiguration.get().getOverrides().
            add(new TestOverrideUser("user", "test", Jenkins.ADMINISTER.getId(), true));

        wc.getOptions().setPrintContentOnFailingStatusCode(true);
        wc.goTo(u.getUrl() + "/configure");
    }

    @Test
    public void testUserDeny() throws Exception {
        WebClient wc = j.createWebClient().login("admin");
        User u = User.get("test");
        wc.goTo(u.getUrl() + "/configure");

        AuthorizationStrategyOverrideConfiguration.get().getOverrides().
            add(new TestOverrideUser("admin", "test", Jenkins.ADMINISTER.getId(), false));

        try {
            wc.getOptions().setPrintContentOnFailingStatusCode(false);
            wc.goTo(u.getUrl() + "/configure");
            fail();
        } catch(FailingHttpStatusCodeException e) {
            assertEquals(403, e.getStatusCode());
        }
    }

    /**
     * Allows or denies the specified permission to the specified computer of the specified user.
     */
    public static class TestOverrideComputer extends AuthorizationStrategyOverride<Computer> {
        private final String user;
        private final String computer;
        private final String permission;
        private final boolean result;

        @DataBoundConstructor
        public TestOverrideComputer(String user, String computer, String permission, boolean result) {
            this.user = user;
            this.computer = computer;
            this.permission = permission;
            this.result = result;
        }

        @Override
        public Boolean hasPermission(Computer item, Authentication a, Permission p) {
            if (StringUtils.isEmpty(user) || StringUtils.isEmpty(permission) || StringUtils.isEmpty(computer)) {
                return null;
            }
            if (!permission.equals(p.getId())) {
                return null;
            }
            if (!computer.equals(item.getName())) {
                return null;
            }
            if (!Jenkins.getInstance().getSecurityRealm().getUserIdStrategy().equals(user, a.getName())) {
                return null;
            }
            // decide the permission
            return result;
        }

        @TestExtension
        public static class DescriptorImpl extends AuthorizationStrategyOverrideDescriptor {
        }
    }

    @Test
    public void testComputerAllow() throws Exception {
        DumbSlave slave = j.createOnlineSlave();

        WebClient wc = j.createWebClient().login("user");
        try {
            wc.getOptions().setPrintContentOnFailingStatusCode(false);
            wc.getPage(slave, "configure");
            fail();
        } catch(FailingHttpStatusCodeException e) {
            assertEquals(403, e.getStatusCode());
        }

        AuthorizationStrategyOverrideConfiguration.get().getOverrides().
            add(new TestOverrideComputer("user", slave.getNodeName(), Computer.CONFIGURE.getId(), true));

        wc.getOptions().setPrintContentOnFailingStatusCode(true);
        wc.getPage(slave, "configure");
    }

    @Test
    public void testComputerDeny() throws Exception {
        DumbSlave slave = j.createOnlineSlave();

        WebClient wc = j.createWebClient().login("developer");
        wc.getPage(slave, "configure");

        AuthorizationStrategyOverrideConfiguration.get().getOverrides().
            add(new TestOverrideComputer("developer", slave.getNodeName(), Computer.CONFIGURE.getId(), false));

        try {
            wc.getOptions().setPrintContentOnFailingStatusCode(false);
            wc.getPage(slave, "configure");
            fail();
        } catch(FailingHttpStatusCodeException e) {
            assertEquals(403, e.getStatusCode());
        }
    }

    /**
     * Allows or denies the specified permission to the specified item of the specified user.
     */
    public static class TestOverrideCloud extends AuthorizationStrategyOverride<Cloud> {
        private final String user;
        private final String cloud;
        private final String permission;
        private final boolean result;

        @DataBoundConstructor
        public TestOverrideCloud(String user, String cloud, String permission, boolean result) {
            this.user = user;
            this.cloud = cloud;
            this.permission = permission;
            this.result = result;
        }

        @Override
        public Boolean hasPermission(Cloud item, Authentication a, Permission p) {
            if (StringUtils.isEmpty(user) || StringUtils.isEmpty(permission) || StringUtils.isEmpty(cloud)) {
                return null;
            }
            if (!permission.equals(p.getId())) {
                return null;
            }
            if (!cloud.equals(item.name)) {
                return null;
            }
            if (!Jenkins.getInstance().getSecurityRealm().getUserIdStrategy().equals(user, a.getName())) {
                return null;
            }
            // decide the permission
            return result;
        }

        @TestExtension
        public static class DescriptorImpl extends AuthorizationStrategyOverrideDescriptor {
        }
    }

    @Test
    public void testCloudAllow() throws Exception {
        Cloud c = new DummyCloudImpl(j, 0);
        assertFalse(c.getACL().hasPermission(User.get("user").impersonate(), Cloud.PROVISION));
        AuthorizationStrategyOverrideConfiguration.get().getOverrides().
            add(new TestOverrideCloud("user", c.name, Cloud.PROVISION.getId(), true));
        assertTrue(c.getACL().hasPermission(User.get("user").impersonate(), Cloud.PROVISION));
    }

    @Test
    public void testCloudDeny() throws Exception {
        Cloud c = new DummyCloudImpl(j, 0);
        assertTrue(c.getACL().hasPermission(User.get("developer").impersonate(), Cloud.PROVISION));
        AuthorizationStrategyOverrideConfiguration.get().getOverrides().
            add(new TestOverrideCloud("developer", c.name, Cloud.PROVISION.getId(), false));
        assertFalse(c.getACL().hasPermission(User.get("developer").impersonate(), Cloud.PROVISION));
    }

    /**
     * Allows or denies the specified permission to the specified node of the specified user.
     */
    public static class TestOverrideNode extends AuthorizationStrategyOverride<Node> {
        private final String user;
        private final String node;
        private final String permission;
        private final boolean result;

        @DataBoundConstructor
        public TestOverrideNode(String user, String node, String permission, boolean result) {
            this.user = user;
            this.node = node;
            this.permission = permission;
            this.result = result;
        }

        @Override
        public Boolean hasPermission(Node item, Authentication a, Permission p) {
            if (StringUtils.isEmpty(user) || StringUtils.isEmpty(permission) || StringUtils.isEmpty(node)) {
                return null;
            }
            if (!permission.equals(p.getId())) {
                return null;
            }
            if (!node.equals(item.getNodeName())) {
                return null;
            }
            if (!Jenkins.getInstance().getSecurityRealm().getUserIdStrategy().equals(user, a.getName())) {
                return null;
            }
            // decide the result
            return result;
        }

        @TestExtension
        public static class DescriptorImpl extends AuthorizationStrategyOverrideDescriptor {
        }
    }

    @Test
    public void testNodeAllow() throws Exception {
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
                User.get("user").impersonate()
            ))
        );

        AuthorizationStrategyOverrideConfiguration.get().getOverrides().
        add(new TestOverrideNode("user", slave1.getNodeName(), Computer.BUILD.getId(), true));

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
            assertEquals(slave1, b.getBuiltOn());
            ++buildnum;
        }
        assertEquals(NUM_BUILDS, buildnum);
    }

    @Test
    public void testNodeDeny() throws Exception {
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

        AuthorizationStrategyOverrideConfiguration.get().getOverrides().
            add(new TestOverrideNode("developer", slave1.getNodeName(), Computer.BUILD.getId(), false));

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
        AuthorizationStrategyOverrideConfiguration.get().getOverrides().
            add(new TestOverrideJob("admin", p.getFullName(), Job.EXTENDED_READ.getId(), false));
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
        WebClient wc = j.createWebClient().login("user");
        FreeStyleProject p = j.createFreeStyleProject();
        try {
            wc.getOptions().setPrintContentOnFailingStatusCode(false);
            wc.getPage(p, "configure");
            fail();
        } catch(FailingHttpStatusCodeException e) {
            assertEquals(403, e.getStatusCode());
        }
        AuthorizationStrategyOverrideConfiguration.get().getOverrides().
            add(new TestOverrideAbstractItem("user", p.getFullName(), Job.EXTENDED_READ.getId(), true));
        wc.getOptions().setPrintContentOnFailingStatusCode(true);
        wc.getPage(p, "configure");
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
        WebClient wc = j.createWebClient().login("user");
        FreeStyleProject p = j.createFreeStyleProject();
        try {
            wc.getOptions().setPrintContentOnFailingStatusCode(false);
            wc.getPage(p, "configure");
            fail();
        } catch(FailingHttpStatusCodeException e) {
            assertEquals(403, e.getStatusCode());
        }
        AuthorizationStrategyOverrideConfiguration.get().getOverrides().
            add(new TestOverrideJenkins("user", Job.EXTENDED_READ.getId(), true));
        try {
            wc.getOptions().setPrintContentOnFailingStatusCode(false);
            wc.getPage(p, "configure");
            fail();
        } catch(FailingHttpStatusCodeException e) {
            assertEquals(403, e.getStatusCode());
        }
    }

    /**
     * Denies the specified permission to the specified free style project of the specified user.
     *
     * Unfortunately, this doesn't work.
     */
    public static class TestOverrideFreeStyleProject extends AuthorizationStrategyOverride<FreeStyleProject> {
        private final  String user;
        private final  String project;
        private final  String permission;
        private final boolean result;

        @DataBoundConstructor
        public TestOverrideFreeStyleProject(String user, String project, String permission, boolean result) {
            this.user = user;
            this.project = project;
            this.permission = permission;
            this.result = result;
        }

        @Override
        public Boolean hasPermission(FreeStyleProject item, Authentication a, Permission p) {
            if (StringUtils.isEmpty(user) || StringUtils.isEmpty(permission) || StringUtils.isEmpty(project)) {
                return null;
            }
            if (!permission.equals(p.getId())) {
                return null;
            }
            if (!project.equals(item.getFullName())) {
                return null;
            }
            if (!Jenkins.getInstance().getSecurityRealm().getUserIdStrategy().equals(user, a.getName())) {
                return null;
            }
            // decide the permission
            return result;
        }

        @TestExtension
        public static class DescriptorImpl extends AuthorizationStrategyOverrideDescriptor {
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
        WebClient wc = j.createWebClient().login("user");
        FreeStyleProject p = j.createFreeStyleProject();
        try {
            wc.getOptions().setPrintContentOnFailingStatusCode(false);
            wc.getPage(p, "configure");
            fail();
        } catch(FailingHttpStatusCodeException e) {
            assertEquals(403, e.getStatusCode());
        }
        AuthorizationStrategyOverrideConfiguration.get().getOverrides().
            add(new TestOverrideFreeStyleProject("user", p.getFullName(), Job.EXTENDED_READ.getId(), true));
        try {
            wc.getOptions().setPrintContentOnFailingStatusCode(false);
            wc.getPage(p, "configure");
            fail();
        } catch(FailingHttpStatusCodeException e) {
            assertEquals(403, e.getStatusCode());
        }
    }

    @Test
    public void testEvaluationOrder() throws Exception {
        WebClient wc = j.createWebClient().login("user");
        FreeStyleProject p = j.createFreeStyleProject();
        AuthorizationStrategyOverrideConfiguration.get().getOverrides().
            add(new TestOverrideFreeStyleProject("user", p.getFullName(), Job.EXTENDED_READ.getId(), false));
        AuthorizationStrategyOverrideConfiguration.get().getOverrides().
            add(new TestOverrideFreeStyleProject("user", p.getFullName(), Job.EXTENDED_READ.getId(), true));
        try {
            wc.getOptions().setPrintContentOnFailingStatusCode(false);
            wc.getPage(p, "configure");
            fail();
        } catch(FailingHttpStatusCodeException e) {
            assertEquals(403, e.getStatusCode());
        }
    }
}
