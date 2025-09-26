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
    }

    @Test
    @Issue("SECURITY-771")
    void onlyAdminOrManageOrSystemReadCanReadAbout() throws Exception {
        final String ADMIN = "admin";
        final String USER = "user";
        final String MANAGER = "manager";
        final String READONLY = "readonly";
        final String MANAGER_READONLY = "manager-readonly";

        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy()
                // full access
                .grant(Jenkins.ADMINISTER).everywhere().to(ADMIN)

                // Read access
                .grant(Jenkins.READ).everywhere().to(USER)

                // Read and Manage
                .grant(Jenkins.READ).everywhere().to(MANAGER)
                .grant(Jenkins.MANAGE).everywhere().to(MANAGER)

                // Read and System read
                .grant(Jenkins.READ).everywhere().to(READONLY)
                .grant(Jenkins.SYSTEM_READ).everywhere().to(READONLY)

                // Read, Manage and System read
                .grant(Jenkins.READ).everywhere().to(MANAGER_READONLY)
                .grant(Jenkins.MANAGE).everywhere().to(MANAGER_READONLY)
                .grant(Jenkins.SYSTEM_READ).everywhere().to(MANAGER_READONLY)
        );

        JenkinsRule.WebClient wc = j.createWebClient()
                .withThrowExceptionOnFailingStatusCode(false);

        { // anonymous user cannot see About Jenkins page -> redirect to sign in page
            HtmlPage page = wc.goTo("about/");
            assertEquals(HttpURLConnection.HTTP_OK, page.getWebResponse().getStatusCode());
            assertThat(page.getTitleText(), containsString("Sign in - Jenkins"));
        }

        { // user cannot see About Jenkins page -> redirect to Access Denied Jenkins page
            wc.login(USER);
            HtmlPage page = wc.goTo("about/");
            assertEquals(HttpURLConnection.HTTP_FORBIDDEN, page.getWebResponse().getStatusCode());
            assertThat(page.getTitleText(), containsString("Jenkins"));
        }

        { // admin can access About Jenkins page
            wc.login(ADMIN);
            HtmlPage page = wc.goTo("about/");
            assertEquals(HttpURLConnection.HTTP_OK, page.getWebResponse().getStatusCode());
            assertThat(page.getWebResponse().getContentAsString(), containsString("Mavenized dependencies"));
            assertThat(page.getTitleText(), containsString("About Jenkins"));
        }

        { // manager can access About Jenkins page
            wc.login(MANAGER);
            HtmlPage page = wc.goTo("about/");
            assertEquals(HttpURLConnection.HTTP_OK, page.getWebResponse().getStatusCode());
            assertThat(page.getTitleText(), containsString("About Jenkins"));
        }

        { // readonly can access About Jenkins page
            wc.login(READONLY);
            HtmlPage page = wc.goTo("about/");
            assertEquals(HttpURLConnection.HTTP_OK, page.getWebResponse().getStatusCode());
            assertThat(page.getTitleText(), containsString("About Jenkins"));
        }

        { // manager-readonly can access About Jenkins page
            wc.login(MANAGER_READONLY);
            HtmlPage page = wc.goTo("about/");
            assertEquals(HttpURLConnection.HTTP_OK, page.getWebResponse().getStatusCode());
            assertThat(page.getTitleText(), containsString("About Jenkins"));
        }
    }

}
