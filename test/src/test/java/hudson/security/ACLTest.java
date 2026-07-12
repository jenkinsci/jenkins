/*
 * The MIT License
 *
 * Copyright 2017 CloudBees, Inc.
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

package hudson.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.model.FreeStyleProject;
import hudson.model.Item;
import hudson.model.Job;
import hudson.model.Run;
import hudson.model.UnprotectedRootAction;
import hudson.model.User;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import jenkins.model.Jenkins;
import org.htmlunit.FailingHttpStatusCodeException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MockAuthorizationStrategy;
import org.jvnet.hudson.test.TestExtension;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;

@WithJenkins
class ACLTest {

    private JenkinsRule r;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        r = rule;
    }

    @Issue("JENKINS-20474")
    @Test
    void bypassStrategyOnSystem() throws Exception {
        FreeStyleProject p = r.createFreeStyleProject();
        r.jenkins.setSecurityRealm(r.createDummySecurityRealm());
        r.jenkins.setAuthorizationStrategy(new DoNotBotherMe());
        assertTrue(p.hasPermission(Item.CONFIGURE));
        assertTrue(p.hasPermission2(ACL.SYSTEM2, Item.CONFIGURE));
        p.checkPermission(Item.CONFIGURE);
        p.checkAbortPermission();
        assertEquals(List.of(p), r.jenkins.getAllItems());
    }

    @Test
    void checkAnyPermissionPassedIfOneIsValid() {
        Jenkins jenkins = r.jenkins;
        jenkins.setSecurityRealm(r.createDummySecurityRealm());
        jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy()
                .grant(Jenkins.MANAGE).everywhere().to("manager")
        );

        final User manager = User.getOrCreateByIdOrFullName("manager");
        try (ACLContext ignored = ACL.as2(manager.impersonate2())) {
            jenkins.getACL().checkAnyPermission(Jenkins.MANAGE);
        }
    }

    @Test
    void checkAnyPermissionThrowsIfPermissionIsMissing() {
        Jenkins jenkins = r.jenkins;
        jenkins.setSecurityRealm(r.createDummySecurityRealm());
        jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy()
                .grant(Jenkins.MANAGE).everywhere().to("manager")
        );

        final User manager = User.getOrCreateByIdOrFullName("manager");

        try (ACLContext ignored = ACL.as2(manager.impersonate2())) {
            Exception e = assertThrows(AccessDeniedException.class,
                    () -> jenkins.getACL().checkAnyPermission(Jenkins.ADMINISTER));
            assertEquals("manager is missing the Overall/Administer permission", e.getMessage());
        }
    }

    @Test
    void checkAnyPermissionThrowsIfMissingMoreThanOne() {
        Jenkins jenkins = r.jenkins;
        jenkins.setSecurityRealm(r.createDummySecurityRealm());
        jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy()
                .grant(Jenkins.MANAGE).everywhere().to("manager")
        );

        final User manager = User.getOrCreateByIdOrFullName("manager");

        try (ACLContext ignored = ACL.as2(manager.impersonate2())) {
            Exception e = assertThrows(AccessDeniedException.class,
                    () -> jenkins.getACL().checkAnyPermission(Jenkins.ADMINISTER, Jenkins.READ));
            assertEquals("manager is missing a permission, one of Overall/Administer, Overall/Read is required", e.getMessage());
        }
    }

    @Test
    @Issue("JENKINS-61467")
    void checkAnyPermissionDoesNotShowDisabledPermissionsInError() {
        Jenkins jenkins = r.jenkins;
        jenkins.setSecurityRealm(r.createDummySecurityRealm());
        jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy()
                .grant(Jenkins.READ).everywhere().to("manager")
        );

        final User manager = User.getOrCreateByIdOrFullName("manager");

        try (ACLContext ignored = ACL.as2(manager.impersonate2())) {
            Exception e = assertThrows(AccessDeniedException.class,
                    () -> jenkins.getACL().checkAnyPermission(Jenkins.MANAGE, Jenkins.SYSTEM_READ));
            assertEquals("manager is missing a permission, one of Overall/Manage, Overall/Administer is required", e.getMessage());
        }
    }

    @Test
    @Issue("JENKINS-61467")
    void checkAnyPermissionShouldShowDisabledPermissionsIfNotImplied() {
        Jenkins jenkins = r.jenkins;
        jenkins.setSecurityRealm(r.createDummySecurityRealm());
        jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy()
                .grant(Jenkins.READ).everywhere().to("manager")
        );

        final User manager = User.getOrCreateByIdOrFullName("manager");

        try (ACLContext ignored = ACL.as2(manager.impersonate2())) {
            Exception e = assertThrows(AccessDeniedException.class,
                    () -> jenkins.getACL().checkAnyPermission(Item.WIPEOUT, Run.ARTIFACTS));
            assertEquals("manager is missing a permission, one of Job/WipeOut, Run/Artifacts is required", e.getMessage());
        }
    }

    @Test
    void hasAnyPermissionThrowsIfNoPermissionProvided() {
        assertThrows(IllegalArgumentException.class, () -> r.jenkins.getACL().hasAnyPermission());
    }

    @Test
    void checkAnyPermissionThrowsIfNoPermissionProvided() {
        assertThrows(IllegalArgumentException.class, () -> r.jenkins.getACL().checkAnyPermission());
    }

    @Test
    @Issue("JENKINS-61465")
    void checkAnyPermissionOnNonAccessControlled() throws Exception {
        r.jenkins.setSecurityRealm(r.createDummySecurityRealm());
        r.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy()
                .grant(Jenkins.READ).everywhere().toEveryone());

        JenkinsRule.WebClient wc = r.createWebClient();
        FailingHttpStatusCodeException ex = assertThrows(FailingHttpStatusCodeException.class, () -> wc.goTo("either"));
        assertEquals(403, ex.getStatusCode());

        r.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy()
                .grant(Jenkins.ADMINISTER).everywhere().toEveryone());

        wc.goTo("either"); // expected to work
    }

    private static class DoNotBotherMe extends AuthorizationStrategy {

        @NonNull
        @Override
        public ACL getRootACL() {
            return new ACL() {
                @Override
                public boolean hasPermission2(Authentication a, Permission permission) {
                    throw new AssertionError("should not have needed to check " + permission + " for " + a);
                }
            };
        }

        @Override
        public ACL getACL(Job<?, ?> project) {
            throw new AssertionError("should not have even needed to call getACL");
        }

        @NonNull
        @Override
        public Collection<String> getGroups() {
            return Collections.emptySet();
        }

    }

    @TestExtension
    public static class EitherPermission implements UnprotectedRootAction {

        @CheckForNull
        @Override
        public String getIconFileName() {
            return null;
        }

        @CheckForNull
        @Override
        public String getDisplayName() {
            return null;
        }

        @CheckForNull
        @Override
        public String getUrlName() {
            return "either";
        }
    }

}
