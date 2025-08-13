/*
 * The MIT License
 *
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi
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

package hudson.bugs;

import static org.junit.jupiter.api.Assertions.assertEquals;

import hudson.security.Permission;
import java.net.HttpURLConnection;
import org.htmlunit.html.HtmlForm;
import org.htmlunit.html.HtmlPage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.JenkinsRule.WebClient;
import org.jvnet.hudson.test.MockAuthorizationStrategy;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

/**
 * Login redirection ignores the context path
 *
 * @author Kohsuke Kawaguchi
 */
@Issue("JENKINS-2290")
@WithJenkins
class LoginRedirectTest {

    private JenkinsRule j;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        j = rule;
    }

    /*
     * First sends HTTP 403, then redirects to the login page.
     */
    @Test
    void redirect1() throws Exception {
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy().grant(Permission.READ).everywhere().toAuthenticated());

        WebClient wc = j.createWebClient();
        wc.getOptions().setThrowExceptionOnFailingStatusCode(false);

        HtmlPage loginPage = wc.goTo("");
        assertEquals(HttpURLConnection.HTTP_OK, loginPage.getWebResponse().getStatusCode());
        assertEquals(j.contextPath + "/login", loginPage.getUrl().getPath());

        HtmlForm form = loginPage.getFormByName("login");
        form.getInputByName("j_username").setValue("alice");
        form.getInputByName("j_password").setValue("alice");
        HtmlPage mainPage = j.submit(form);
        assertEquals(j.contextPath + "/", mainPage.getUrl().getPath());
    }

    /*
     * Verifies that HTTP 403 is sent first. This is important for machine agents.
     */
    @Test
    void redirect2() throws Exception {
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy().grant(Permission.READ).everywhere().toAuthenticated());

        WebClient wc = j.createWebClient();
        wc.assertFails("", HttpURLConnection.HTTP_FORBIDDEN);
    }
}
