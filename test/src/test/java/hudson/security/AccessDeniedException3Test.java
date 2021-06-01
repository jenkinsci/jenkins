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

import com.gargoylesoftware.htmlunit.FailingHttpStatusCodeException;
import java.net.HttpURLConnection;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import com.gargoylesoftware.htmlunit.html.HtmlPage;
import hudson.model.InvisibleAction;
import hudson.model.Item;
import hudson.model.RootAction;
import jenkins.model.Jenkins;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MockAuthorizationStrategy;
import org.jvnet.hudson.test.TestExtension;
import org.kohsuke.stapler.HttpResponse;

public class AccessDeniedException3Test {

    @Rule
    public JenkinsRule r = new JenkinsRule();

    @Issue("JENKINS-39402")
    @Test
    public void youAreInGroupHeaders() throws Exception {
        JenkinsRule.DummySecurityRealm realm = r.createDummySecurityRealm();
        String[] groups = new String[1000];
        for (int i = 0; i < groups.length; i++) {
            groups[i] = "group" + i;
        }
        realm.addGroups("user", groups);
        r.jenkins.setSecurityRealm(realm);
        r.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy());
        try {
            r.createWebClient().login("user");
            fail("should not have been allowed to access anything");
        } catch (FailingHttpStatusCodeException x) {
            assertEquals(HttpURLConnection.HTTP_FORBIDDEN, x.getStatusCode());
            assertNotNull(x.getResponse().getResponseHeaderValue("X-You-Are-In-Group-Disabled"));
        }
    }

    @Test
    @Issue("JENKINS-61905")
    public void redirectPermissionErrorsToLogin() throws Exception {
        JenkinsRule.DummySecurityRealm realm = r.createDummySecurityRealm();
        r.jenkins.setSecurityRealm(realm);
        r.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy().grant(Jenkins.READ).everywhere().toEveryone());
        JenkinsRule.WebClient wc = r.createWebClient();
        wc.setRedirectEnabled(true);
        wc.setThrowExceptionOnFailingStatusCode(false);
        final HtmlPage configure = wc.goTo("configure");
        Assert.assertTrue(configure.getUrl().getPath().contains("login"));
        Assert.assertTrue(configure.getUrl().getQuery().startsWith("from"));

        final HtmlPage configureSecurity = wc.goTo("configureSecurity/");
        Assert.assertTrue(configureSecurity.getUrl().getPath().contains("login"));
        Assert.assertTrue(configureSecurity.getUrl().getQuery().startsWith("from"));
    }

    @Issue("JENKINS-5303")
    @Test
    public void captureException() throws Exception {
        r.jenkins.setSecurityRealm(r.createDummySecurityRealm());
        r.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy().grant(Jenkins.READ).everywhere().toEveryone());
        JenkinsRule.WebClient wc = r.createWebClient().login("user");
        try {
            wc.goTo("fails/accessDeniedException3");
            fail("should report an error from AccessDeniedException3");
        } catch (FailingHttpStatusCodeException x) {
            assertEquals("should send a 403 from AccessDeniedException3", HttpURLConnection.HTTP_FORBIDDEN, x.getStatusCode());
            assertEquals("should report X-You-Are-Authenticated-As from AccessDeniedException3", "user", x.getResponse().getResponseHeaderValue("X-You-Are-Authenticated-As"));
        }
        try {
            wc.goTo("fails/accessDeniedException2");
            fail("should report an error from AccessDeniedException2");
        } catch (FailingHttpStatusCodeException x) {
            assertEquals("should send a 403 from AccessDeniedException2", HttpURLConnection.HTTP_FORBIDDEN, x.getStatusCode());
            assertEquals("should report X-You-Are-Authenticated-As from AccessDeniedException2", "user", x.getResponse().getResponseHeaderValue("X-You-Are-Authenticated-As"));
        }
    }
    @TestExtension("captureException")
    public static final class Fails extends InvisibleAction implements RootAction {
        @Override
        public String getUrlName() {
            return "fails";
        }
        public HttpResponse doAccessDeniedException3() {
            throw new AccessDeniedException3(Jenkins.getAuthentication2(), Item.READ);
        }
        @SuppressWarnings("deprecation")
        public HttpResponse doAccessDeniedException2() {
            throw new AccessDeniedException2(Jenkins.getAuthentication(), Item.READ);
        }
    }

}
