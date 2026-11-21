/*
 * The MIT License
 *
 * Copyright (c) 2026, Jenkins Contributors
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
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;

import java.util.Map;
import jenkins.model.Jenkins;
import jenkins.model.experimentalflags.UserExperimentalFlagsProperty;
import org.htmlunit.html.HtmlPage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MockAuthorizationStrategy;
import org.jvnet.hudson.test.MockFolder;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class NewViewPageDeleteTest {

    private static final String NEW_DASHBOARD_FLAG = "new-dashboard-page.flag";

    private JenkinsRule j;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        j = rule;
    }

    @Test
    void deleteShownForFolder() throws Exception {
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        enableNewDashboard("alice");
        MockFolder folder = j.createFolder("f");

        HtmlPage page = j.createWebClient().withBasicCredentials("alice").goTo(folder.getUrl());

        assertThat(page.getWebResponse().getContentAsString(), containsString(folder.getUrl() + "doDelete"));
    }

    @Test
    void deleteHiddenWithoutPermission() throws Exception {
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy()
                .grant(Jenkins.READ, Item.READ).everywhere().to("bob"));
        enableNewDashboard("bob");
        MockFolder folder = j.createFolder("f");

        HtmlPage page = j.createWebClient().withBasicCredentials("bob").goTo(folder.getUrl());

        assertThat(page.getWebResponse().getContentAsString(), not(containsString(folder.getUrl() + "doDelete")));
    }

    @Test
    void deleteHiddenForRootDashboard() throws Exception {
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        enableNewDashboard("carol");

        HtmlPage page = j.createWebClient().withBasicCredentials("carol").goTo("");

        // The root dashboard must never offer to delete Jenkins itself.
        assertThat(page.getWebResponse().getContentAsString(), not(containsString("/doDelete")));
    }

    private void enableNewDashboard(String userId) throws Exception {
        User user = User.getOrCreateByIdOrFullName(userId);
        user.addProperty(new UserExperimentalFlagsProperty(Map.of(NEW_DASHBOARD_FLAG, "true")));
    }
}
