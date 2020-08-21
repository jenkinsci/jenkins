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

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.model.Build;
import com.gargoylesoftware.htmlunit.FailingHttpStatusCodeException;
import hudson.model.FreeStyleProject;
import hudson.model.Item;
import hudson.model.UnprotectedRootAction;
import hudson.model.User;
import java.util.Collection;
import java.util.Collections;
import jenkins.model.Jenkins;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import org.junit.Rule;
import org.junit.rules.ExpectedException;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MockAuthorizationStrategy;
import org.jvnet.hudson.test.TestExtension;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;

public class ACLTest {

    @Rule
    public JenkinsRule r = new JenkinsRule();

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    @Issue("JENKINS-20474")
    @Test
    public void bypassStrategyOnSystem() throws Exception {
        FreeStyleProject p = r.createFreeStyleProject();
        r.jenkins.setSecurityRealm(r.createDummySecurityRealm());
        r.jenkins.setAuthorizationStrategy(new DoNotBotherMe());
        assertTrue(p.hasPermission(Item.CONFIGURE));
        assertTrue(p.hasPermission2(ACL.SYSTEM2, Item.CONFIGURE));
        p.checkPermission(Item.CONFIGURE);
        p.checkAbortPermission();
        assertEquals(Collections.singletonList(p), r.jenkins.getAllItems());
    }

    @Test
    public void checkAnyPermissionPassedIfOneIsValid() {
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
    public void checkAnyPermissionThrowsIfPermissionIsMissing() {
        Jenkins jenkins = r.jenkins;
        jenkins.setSecurityRealm(r.createDummySecurityRealm());
        jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy()
                .grant(Jenkins.MANAGE).everywhere().to("manager")
        );

        final User manager = User.getOrCreateByIdOrFullName("manager");

        expectedException.expectMessage("manager is missing the Overall/Administer permission");
        expectedException.expect(AccessDeniedException.class);
        try (ACLContext ignored = ACL.as2(manager.impersonate2())) {
            jenkins.getACL().checkAnyPermission(Jenkins.ADMINISTER);
        }
    }

    @Test
    public void checkAnyPermissionThrowsIfMissingMoreThanOne() {
        Jenkins jenkins = r.jenkins;
        jenkins.setSecurityRealm(r.createDummySecurityRealm());
        jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy()
                .grant(Jenkins.MANAGE).everywhere().to("manager")
        );

        final User manager = User.getOrCreateByIdOrFullName("manager");

        expectedException.expectMessage("manager is missing a permission, one of Overall/Administer, Overall/Read is required");
        expectedException.expect(AccessDeniedException.class);
        try (ACLContext ignored = ACL.as2(manager.impersonate2())) {
            jenkins.getACL().checkAnyPermission(Jenkins.ADMINISTER, Jenkins.READ);
        }
    }

    @Test
    @Issue("JENKINS-61467")
    public void checkAnyPermissionDoesNotShowDisabledPermissionsInError() {
        Jenkins jenkins = r.jenkins;
        jenkins.setSecurityRealm(r.createDummySecurityRealm());
        jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy()
                .grant(Jenkins.READ).everywhere().to("manager")
        );

        final User manager = User.getOrCreateByIdOrFullName("manager");

        expectedException.expectMessage("manager is missing the Overall/Administer permission");
        expectedException.expect(AccessDeniedException.class);
        try (ACLContext ignored = ACL.as2(manager.impersonate2())) {
            jenkins.getACL().checkAnyPermission(Jenkins.MANAGE, Jenkins.SYSTEM_READ);
        }
    }

    @Test
    @Issue("JENKINS-61467")
    public void checkAnyPermissionShouldShowDisabledPermissionsIfNotImplied() {
        Jenkins jenkins = r.jenkins;
        jenkins.setSecurityRealm(r.createDummySecurityRealm());
        jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy()
                .grant(Jenkins.READ).everywhere().to("manager")
        );

        final User manager = User.getOrCreateByIdOrFullName("manager");

        expectedException.expectMessage("manager is missing a permission, one of Job/WipeOut, Run/Artifacts is required");
        expectedException.expect(AccessDeniedException.class);
        try (ACLContext ignored = ACL.as2(manager.impersonate2())) {
            jenkins.getACL().checkAnyPermission(Item.WIPEOUT, Build.ARTIFACTS);
        }
    }

    @Test
    public void hasAnyPermissionThrowsIfNoPermissionProvided() {
        expectedException.expect(IllegalArgumentException.class);
        r.jenkins.getACL().hasAnyPermission();
    }

    @Test
    public void checkAnyPermissionThrowsIfNoPermissionProvided() {
        expectedException.expect(IllegalArgumentException.class);
        r.jenkins.getACL().checkAnyPermission();
    }

    @Test
    @Issue("JENKINS-61465")
    public void checkAnyPermissionOnNonAccessControlled() throws Exception {
        expectedException = ExpectedException.none();

        r.jenkins.setSecurityRealm(r.createDummySecurityRealm());
        r.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy()
                .grant(Jenkins.READ).everywhere().toEveryone());

        JenkinsRule.WebClient wc = r.createWebClient();
        try {
            wc.goTo("either");
            fail();
        } catch (FailingHttpStatusCodeException ex) {
            assertEquals(403, ex.getStatusCode());
        }

        r.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy()
                .grant(Jenkins.ADMINISTER).everywhere().toEveryone());

        wc.goTo("either"); // expected to work
    }

    private static class DoNotBotherMe extends AuthorizationStrategy {

        @Override
        public ACL getRootACL() {
            return new ACL() {
                @Override
                public boolean hasPermission2(Authentication a, Permission permission) {
                    throw new AssertionError("should not have needed to check " + permission + " for " + a);
                }
            };
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
