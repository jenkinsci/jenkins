/*
 * The MIT License
 *
 * Copyright 2018 CloudBees, Inc.
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

package hudson;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.HttpURLConnection;
import jenkins.model.Jenkins;
import org.htmlunit.html.HtmlPage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MockAuthorizationStrategy;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@Tag("SmokeTest")
@WithJenkins
class AboutJenkinsTest {

    private JenkinsRule j;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        j = rule;
        setupTestAuthorization();
    }

    private void setupTestAuthorization() {
        final String ADMIN = "admin";
        final String MANAGER = "manager";
        final String MANAGER_READONLY = "manager-readonly";
        final String USER = "user";
        final String READONLY = "readonly";

        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy()
                // admin full access
                .grant(Jenkins.ADMINISTER).everywhere().to(ADMIN)

                // Read and Manage
                .grant(Jenkins.READ).everywhere().to(MANAGER)
                .grant(Jenkins.MANAGE).everywhere().to(MANAGER)

                // Read, Manage and System read
                .grant(Jenkins.READ).everywhere().to(MANAGER_READONLY)
                .grant(Jenkins.MANAGE).everywhere().to(MANAGER_READONLY)
                .grant(Jenkins.SYSTEM_READ).everywhere().to(MANAGER_READONLY)

                // Read access only (should NOT access About Jenkins page)
                .grant(Jenkins.READ).everywhere().to(USER)

                // System read only (should NOT access About Jenkins page)
                .grant(Jenkins.SYSTEM_READ).everywhere().to(READONLY)
        );
    }

    private HtmlPage accessAsUser(String username) throws Exception {
        return j.createWebClient()
                .withThrowExceptionOnFailingStatusCode(false)
                .login(username)
                .goTo("about/");
    }

    @Test
    @Issue("SECURITY-771")
    void onlyAdminOrManagerOrSystemReadManagerCanSeeAboutPage() throws Exception {
        // ADMINISTER permission: admin can see About Jenkins page
        HtmlPage adminPage = accessAsUser("admin");
        assertEquals(HttpURLConnection.HTTP_OK, adminPage.getWebResponse().getStatusCode());
        assertThat(adminPage.getWebResponse().getContentAsString(), containsString("Mavenized dependencies"));
        assertThat(adminPage.getTitleText(), containsString("About Jenkins"));

        // MANAGE permission: manager can see About Jenkins page
        HtmlPage managerPage = accessAsUser("manager");
        assertEquals(HttpURLConnection.HTTP_OK, managerPage.getWebResponse().getStatusCode());
        assertThat(managerPage.getWebResponse().getContentAsString(), containsString("Mavenized dependencies"));
        assertThat(managerPage.getTitleText(), containsString("About Jenkins"));

        // MANAGE + SYSTEM_READ permissions: manager-readonly can see About Jenkins page
        HtmlPage managerReadonlyPage = accessAsUser("manager-readonly");
        assertEquals(HttpURLConnection.HTTP_OK, managerReadonlyPage.getWebResponse().getStatusCode());
        assertThat(managerReadonlyPage.getWebResponse().getContentAsString(), containsString("Mavenized dependencies"));
        assertThat(managerReadonlyPage.getTitleText(), containsString("About Jenkins"));
    }

    @Test
    void anonymousOrReadUserOrSystemReadUserCannotSeeAboutPage() throws Exception {
        // anonymous user cannot see About Jenkins page -> redirect to sign in page
        HtmlPage anonymousPage = j.createWebClient()
                .withThrowExceptionOnFailingStatusCode(false)
                .goTo("about/");
        assertEquals(HttpURLConnection.HTTP_OK, anonymousPage.getWebResponse().getStatusCode());
        assertThat(anonymousPage.getTitleText(), containsString("Sign in - Jenkins"));

        // only READ permission: user cannot see About Jenkins page -> redirect to Access Denied Jenkins page
        HtmlPage userPage = accessAsUser("user");
        assertEquals(HttpURLConnection.HTTP_FORBIDDEN, userPage.getWebResponse().getStatusCode());
        assertThat(userPage.getTitleText(), containsString("Jenkins"));

        // SYSTEM_READ permission: readonly cannot see About Jenkins page -> redirect to Access Denied Jenkins page
        HtmlPage readonlyPage = accessAsUser("readonly");
        assertEquals(HttpURLConnection.HTTP_FORBIDDEN, readonlyPage.getWebResponse().getStatusCode());
        assertThat(readonlyPage.getTitleText(), containsString("Jenkins"));
    }
}