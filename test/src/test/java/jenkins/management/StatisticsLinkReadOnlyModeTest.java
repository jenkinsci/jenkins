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

package jenkins.management;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import hudson.security.HudsonPrivateSecurityRealm;
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
 * <p>The "Load Statistics" page ({@code jenkins/model/Jenkins/load-statistics.jelly}, reachable
 * via {@link StatisticsLink}) previously required {@link Jenkins#MANAGE} both for the
 * {@link hudson.model.ManagementLink} to be shown on "Manage Jenkins" and, once the class of the
 * URL was actually reached through a resolved link, was rendered with no permission check of its
 * own at all. This left the page unreachable for viewers with only {@link Jenkins#SYSTEM_READ}
 * even though the page is purely informational (load statistics graphs), mirroring how
 * {@code jenkins/model/Jenkins/systemInfo.jelly} already gates itself on
 * {@code Jenkins#MANAGE_AND_SYSTEM_READ}.
 */
@WithJenkins
class StatisticsLinkReadOnlyModeTest {

    private JenkinsRule j;
    private HudsonPrivateSecurityRealm realm;

    @BeforeEach
    void setUp(JenkinsRule rule) throws Exception {
        j = rule;
        realm = new HudsonPrivateSecurityRealm(false, false, null);
        j.jenkins.setSecurityRealm(realm);
    }

    @Issue("JENKINS-62431")
    @Test
    void systemReadViewerCanAccessPage() throws Exception {
        realm.createAccount("viewer", "viewer");
        j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy()
                .grant(Jenkins.READ, Jenkins.SYSTEM_READ).everywhere().to("viewer"));

        JenkinsRule.WebClient wc = j.createWebClient();
        wc.login("viewer", "viewer");
        HtmlPage page = wc.goTo("manage/load-statistics");
        wc.waitForBackgroundJavaScript(2000);

        assertTrue(page.asNormalizedText().contains("Timespan"),
                "a SYSTEM_READ-only viewer should see the deferred load statistics content, not just the page shell");
    }

    @Issue("JENKINS-62431")
    @Test
    void systemReadViewerCanAccessPageViaBareUrl() throws Exception {
        // jenkins/model/Jenkins/load-statistics.jelly also resolves off the bare root URL,
        // not just under manage/ - both paths must honor the same permission gate.
        realm.createAccount("viewer", "viewer");
        j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy()
                .grant(Jenkins.READ, Jenkins.SYSTEM_READ).everywhere().to("viewer"));

        JenkinsRule.WebClient wc = j.createWebClient();
        wc.login("viewer", "viewer");
        HtmlPage page = wc.goTo("load-statistics");
        wc.waitForBackgroundJavaScript(2000);

        assertTrue(page.asNormalizedText().contains("Timespan"),
                "a SYSTEM_READ-only viewer should be able to reach the Load Statistics page via the bare URL too");
    }

    @Issue("JENKINS-62431")
    @Test
    void manageViewerCanStillAccessPage() throws Exception {
        realm.createAccount("manager", "manager");
        j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy()
                .grant(Jenkins.READ, Jenkins.MANAGE).everywhere().to("manager"));

        JenkinsRule.WebClient wc = j.createWebClient();
        wc.login("manager", "manager");
        HtmlPage page = wc.goTo("manage/load-statistics");
        wc.waitForBackgroundJavaScript(2000);

        assertTrue(page.asNormalizedText().contains("Timespan"),
                "a MANAGE-only viewer must keep the access it already had before this change");
    }

    @Issue("JENKINS-62431")
    @Test
    void viewerWithoutRequiredPermissionIsDenied() throws Exception {
        realm.createAccount("nobody", "nobody");
        j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy()
                .grant(Jenkins.READ).everywhere().to("nobody"));

        JenkinsRule.WebClient wc = j.createWebClient();
        wc.login("nobody", "nobody");

        FailingHttpStatusCodeException ex = assertThrows(FailingHttpStatusCodeException.class,
                () -> wc.goTo("manage/load-statistics"));
        assertEquals(403, ex.getStatusCode(), "expected 403 for a viewer without MANAGE or SYSTEM_READ");
    }
}
