/*
 * The MIT License
 *
 * Copyright (c) 2026, CloudBees, Inc. and others
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

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import hudson.model.User;
import jenkins.model.Jenkins;
import org.htmlunit.FailingHttpStatusCodeException;
import org.htmlunit.html.HtmlPage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MockAuthorizationStrategy;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

/**
 * Part of the JENKINS-12548 "Read-only system configuration browsing" epic (JEP-224).
 *
 * <p>The "Manage Users" page ({@code hudson/security/HudsonPrivateSecurityRealm/index.jelly},
 * reachable via {@link HudsonPrivateSecurityRealm.ManageUserLinks}) previously required
 * {@link Jenkins#ADMINISTER} both for the {@link hudson.model.ManagementLink} to be shown and
 * for the page itself to render, even though everything on the page that actually mutates state
 * (the "Create User" button, gated by {@code l:isAdmin}; the delete action, gated by
 * {@link User#canDelete()}; and the {@code doDoDelete}/{@code doSubmitDescription} POST handlers)
 * already independently requires {@link Jenkins#ADMINISTER}. This left the page fully blocked for
 * viewers with only {@link Jenkins#SYSTEM_READ}, instead of rendering it read-only like the rest
 * of JEP-224.
 */
@WithJenkins
class ManageUserLinksReadOnlyModeTest {

    private JenkinsRule j;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        j = rule;
    }

    @Issue("JENKINS-62430")
    @Test
    void systemReadViewerCanSeePageWithoutMutatingControls() throws Exception {
        HudsonPrivateSecurityRealm realm = new HudsonPrivateSecurityRealm(false, false, null);
        j.jenkins.setSecurityRealm(realm);
        realm.createAccount("viewer", "viewer");
        j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy()
                .grant(Jenkins.ADMINISTER).everywhere().to("admin")
                .grant(Jenkins.READ, Jenkins.SYSTEM_READ).everywhere().to("viewer"));

        JenkinsRule.WebClient wc = j.createWebClient();
        wc.withBasicCredentials("viewer", "viewer");
        HtmlPage page = wc.goTo("manage/securityRealm/");

        assertNull(page.querySelector("[data-dialog-url='addUserDialog']"),
                "a SYSTEM_READ-only viewer must not see the 'Create User' button");
        assertNull(page.querySelector("a[class*='destructive-color']"),
                "a SYSTEM_READ-only viewer must not see any delete button");
        assertTrue(page.asNormalizedText().contains("viewer"),
                "the user list itself should still be visible to the SYSTEM_READ viewer");
    }

    @Issue("JENKINS-62430")
    @Test
    void viewerWithoutSystemReadIsDenied() throws Exception {
        HudsonPrivateSecurityRealm realm = new HudsonPrivateSecurityRealm(false, false, null);
        j.jenkins.setSecurityRealm(realm);
        realm.createAccount("nobody", "nobody");
        j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy()
                .grant(Jenkins.ADMINISTER).everywhere().to("admin")
                .grant(Jenkins.READ).everywhere().to("nobody"));

        JenkinsRule.WebClient wc = j.createWebClient();
        wc.withBasicCredentials("nobody", "nobody");

        FailingHttpStatusCodeException ex = assertThrows(FailingHttpStatusCodeException.class,
                () -> wc.goTo("manage/securityRealm/"));
        assertTrue(ex.getStatusCode() == 403, "expected 403 for a viewer without SYSTEM_READ or ADMINISTER");
    }
}
