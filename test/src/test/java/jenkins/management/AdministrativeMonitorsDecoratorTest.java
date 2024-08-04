/*
 * The MIT License
 *
 * Copyright (c) 2020 CloudBees, Inc.
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

package jenkins.management;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertEquals;

import hudson.ExtensionList;
import hudson.model.AdministrativeMonitor;
import hudson.model.User;
import hudson.security.ACL;
import hudson.security.Permission;
import jenkins.model.Jenkins;
import org.jenkinsci.Symbol;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.FlagRule;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MockAuthorizationStrategy;
import org.jvnet.hudson.test.TestExtension;

public class AdministrativeMonitorsDecoratorTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Rule
    public final FlagRule<String> managePermissionRule = FlagRule.systemProperty("jenkins.security.ManagePermission", "true");

    @Test
    public void ensureAdminMonitorsAreNotRunPerNonAdminPage() throws Exception {
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        String nonAdminLogin = "nonAdmin";
        User.getById(nonAdminLogin, true);

        j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy()
                .grant(Jenkins.READ).everywhere().to(nonAdminLogin)
        );

        JenkinsRule.WebClient wc = j.createWebClient();
        wc.login(nonAdminLogin);

        ExtensionList<AdministrativeMonitor> extensionList = j.jenkins.getExtensionList(AdministrativeMonitor.class);
        ExecutionCounterNonSecAdministrativeMonitor nonSecCounter = extensionList.get(ExecutionCounterNonSecAdministrativeMonitor.class);
        ExecutionCounterSecAdministrativeMonitor secCounter = extensionList.get(ExecutionCounterSecAdministrativeMonitor.class);

        assertEquals(0, nonSecCounter.count);
        assertEquals(0, secCounter.count);
    }

    @Test
    @Issue("JENKINS-63977")
    public void ensureAdminMonitorsAreRunOnlyOncePerAdminPage() throws Exception {
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        String adminLogin = "admin";
        User.getById(adminLogin, true);

        j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy()
                .grant(Jenkins.ADMINISTER).everywhere().to(adminLogin)
        );

        JenkinsRule.WebClient wc = j.createWebClient();
        wc.login(adminLogin);

        ExtensionList<AdministrativeMonitor> extensionList = j.jenkins.getExtensionList(AdministrativeMonitor.class);
        ExecutionCounterNonSecAdministrativeMonitor nonSecCounter = extensionList.get(ExecutionCounterNonSecAdministrativeMonitor.class);
        ExecutionCounterSecAdministrativeMonitor secCounter = extensionList.get(ExecutionCounterSecAdministrativeMonitor.class);

        assertEquals(1, nonSecCounter.count);
        assertEquals(1, secCounter.count);
    }

    @TestExtension({"ensureAdminMonitorsAreNotRunPerNonAdminPage", "ensureAdminMonitorsAreRunOnlyOncePerAdminPage"})
    @Symbol("non_sec_counting")
    public static class ExecutionCounterNonSecAdministrativeMonitor extends AdministrativeMonitor {

        public int count = 0;

        @Override
        public String getDisplayName() {
            return "NonSecCounter";
        }

        @Override
        public boolean isActivated() {
            count++;
            return true;
        }

        @Override
        public boolean isSecurity() {
            return false;
        }
    }

    @TestExtension({"ensureAdminMonitorsAreNotRunPerNonAdminPage", "ensureAdminMonitorsAreRunOnlyOncePerAdminPage"})
    @Symbol("sec_counting")
    public static class ExecutionCounterSecAdministrativeMonitor extends AdministrativeMonitor {

        public int count = 0;

        @Override
        public String getDisplayName() {
            return "SecCounter";
        }

        @Override
        public boolean isActivated() {
            count++;
            return true;
        }

        @Override
        public boolean isSecurity() {
            return true;
        }
    }

    @Test
    public void ensureAdminMonitorsCanBeSeenByManagers() {
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        var managerLogin = "manager";
        var systemReadLogin = "system-reader";
        var managerUser = User.getById(managerLogin, true);
        var systemReadUser = User.getById(systemReadLogin, true);

        j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy()
                .grant(Jenkins.MANAGE, Jenkins.READ).everywhere().to(managerLogin)
                .grant(Jenkins.READ, Jenkins.SYSTEM_READ).everywhere().to(systemReadLogin)
        );

        try (var ignored = ACL.as2(managerUser.impersonate2())) {
            assertThat(Jenkins.get().getActiveAdministrativeMonitors(), hasItem(instanceOf(ManagerAdministrativeMonitor.class)));
        }
        try (var ignored = ACL.as2(systemReadUser.impersonate2())) {
            assertThat(Jenkins.get().getActiveAdministrativeMonitors(), not(hasItem(instanceOf(ManagerAdministrativeMonitor.class))));
        }
    }

    @TestExtension("ensureAdminMonitorsCanBeSeenByManagers")
    public static class ManagerAdministrativeMonitor extends AdministrativeMonitor {
        @Override
        public Permission getRequiredPermission() {
            return Jenkins.MANAGE;
        }

        @Override
        public boolean isActivated() {
            return true;
        }
    }

    @Test
    public void ensureAdminMonitorsCanBeSeenByManagersOrSystemReaders() {
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        var managerLogin = "manager";
        var systemReadLogin = "system-reader";
        var managerUser = User.getById(managerLogin, true);
        var systemReadUser = User.getById(systemReadLogin, true);

        j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy()
                .grant(Jenkins.MANAGE, Jenkins.READ).everywhere().to(managerLogin)
                .grant(Jenkins.READ, Jenkins.SYSTEM_READ).everywhere().to(systemReadLogin)
        );

        try (var ignored = ACL.as2(managerUser.impersonate2())) {
            assertThat(Jenkins.get().getActiveAdministrativeMonitors(), hasItem(instanceOf(ManagerOrSystemReaderAdministrativeMonitor.class)));
        }
        try (var ignored = ACL.as2(systemReadUser.impersonate2())) {
            assertThat(Jenkins.get().getActiveAdministrativeMonitors(), hasItem(instanceOf(ManagerOrSystemReaderAdministrativeMonitor.class)));
        }
    }

    @TestExtension("ensureAdminMonitorsCanBeSeenByManagersOrSystemReaders")
    public static class ManagerOrSystemReaderAdministrativeMonitor extends AdministrativeMonitor {

        private static final Permission[] REQUIRED_ANY_PERMISSIONS = {Jenkins.MANAGE, Jenkins.SYSTEM_READ};

        @Override
        public void checkRequiredPermission() {
            Jenkins.get().checkAnyPermission(REQUIRED_ANY_PERMISSIONS);
        }

        @Override
        public boolean hasRequiredPermission() {
            return Jenkins.get().hasAnyPermission(REQUIRED_ANY_PERMISSIONS);
        }

        @Override
        public boolean isActivated() {
            return true;
        }
    }
}
