/*
 * The MIT License
 *
 * Copyright (c) 2017, CloudBees, Inc.
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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;

import hudson.model.Descriptor;
import hudson.security.captcha.CaptchaSupport;
import java.io.OutputStream;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.Random;
import jenkins.model.Jenkins;
import org.htmlunit.CookieManager;
import org.htmlunit.WebResponse;
import org.htmlunit.html.HtmlForm;
import org.htmlunit.html.HtmlPage;
import org.htmlunit.util.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestExtension;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.jelly.JellyFacet;

@WithJenkins
class SecurityRealmTest {

    private JenkinsRule j;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        j = rule;
    }

    @Test
    @Issue("JENKINS-43852")
    void testCacheHeaderInResponse() throws Exception {
        SecurityRealm securityRealm = j.createDummySecurityRealm();
        j.jenkins.setSecurityRealm(securityRealm);

        WebResponse response = j.createWebClient()
                .goTo("securityRealm/captcha", "")
                .getWebResponse();
        assertEquals("", response.getContentAsString());

        securityRealm.setCaptchaSupport(new DummyCaptcha());

        response = j.createWebClient()
                .goTo("securityRealm/captcha", "image/png")
                .getWebResponse();

        assertThat(response.getResponseHeaderValue("Cache-Control"), is("no-cache, no-store, must-revalidate"));
        assertThat(response.getResponseHeaderValue("Pragma"), is("no-cache"));
        assertThat(response.getResponseHeaderValue("Expires"), is("0"));
    }

    private static class DummyCaptcha extends CaptchaSupport {
        @Override
        public boolean validateCaptcha(String id, String text) {
            return false;
        }

        @Override
        public void generateImage(String id, OutputStream ios) {
        }
    }

    static void addSessionCookie(CookieManager manager, String domain, String path, Date date) {
        manager.addCookie(new Cookie(domain, "JSESSIONID." + Integer.toHexString(new Random().nextInt()),
                "xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx",
                path,
                date,
                false));
    }

    @Test
    void many_sessions_logout() throws Exception {
        final String WILL_NOT_BE_SENT = "/will-not-be-sent";
        final String LOCALHOST = "localhost";
        final String JSESSIONID = "JSESSIONID";

        JenkinsRule.WebClient wc = j.createWebClient();
        CookieManager manager = wc.getCookieManager();
        manager.setCookiesEnabled(true);
        wc.goTo("login");

        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.DAY_OF_YEAR, 1);
        Date tomorrow = calendar.getTime();
        Collections.nCopies(8, 1)
                .forEach(i -> addSessionCookie(manager, LOCALHOST, "/jenkins", tomorrow));
        addSessionCookie(manager, LOCALHOST, WILL_NOT_BE_SENT, tomorrow);

        HtmlPage page = wc.goTo("logout");

        int unexpectedSessionCookies = 2;

        StringBuilder builder = new StringBuilder();
        builder.append("Session cookies: ");

        for (Cookie cookie : manager.getCookies()) {
            if (cookie.getName().startsWith(JSESSIONID)) {
                String path = cookie.getPath();

                builder.append(cookie.getName());
                if (path != null)
                    builder.append("; Path=").append(path);
                builder.append("\n");

                if (WILL_NOT_BE_SENT.equals(path)) {
                    // Because it wasn't sent and thus wasn't deleted.
                    --unexpectedSessionCookies;
                } else if (JSESSIONID.equals(cookie.getName())) {
                    // Because this test harness isn't winstone and the cleaning
                    // code is only responsible for deleting "JSESSIONID." cookies.
                    --unexpectedSessionCookies;
                }
            }
        }
        System.err.println(builder);
        assertThat(unexpectedSessionCookies, is(0));
    }

    @SuppressWarnings("deprecation")
    @Test
    void getPostLogOutUrl() throws Exception {
        OldSecurityRealm osr = new OldSecurityRealm();
        j.jenkins.setSecurityRealm(osr);
        j.executeOnServer(() -> {
            assertEquals("/jenkins/", j.jenkins.getSecurityRealm().getPostLogOutUrl(Stapler.getCurrentRequest(), Jenkins.ANONYMOUS));
            assertEquals("/jenkins/", j.jenkins.getSecurityRealm().getPostLogOutUrl2(Stapler.getCurrentRequest2(), Jenkins.ANONYMOUS2));
            osr.special = true;
            assertEquals("/jenkins/custom", j.jenkins.getSecurityRealm().getPostLogOutUrl(Stapler.getCurrentRequest(), Jenkins.ANONYMOUS));
            assertEquals("/jenkins/custom", j.jenkins.getSecurityRealm().getPostLogOutUrl2(Stapler.getCurrentRequest2(), Jenkins.ANONYMOUS2));
            return null;
        });
    }

    @SuppressWarnings("deprecation")
    public static final class OldSecurityRealm extends SecurityRealm {
        boolean special;

        @SuppressWarnings("checkstyle:redundantmodifier")
        @DataBoundConstructor
        public OldSecurityRealm() {}

        @Override
        public SecurityRealm.SecurityComponents createSecurityComponents() {
            return new SecurityComponents();
        }

        @Override
        protected String getPostLogOutUrl(StaplerRequest req, org.acegisecurity.Authentication auth) {
            return special ? req.getContextPath() + "/custom" : super.getPostLogOutUrl(req, auth);
        }

        @TestExtension("getPostLogOutUrl")
        public static final class DescriptorImpl extends Descriptor<SecurityRealm> {}
    }

    @Test
    @Issue("JENKINS-65288")
    void submitPossibleWithoutJellyTrace() throws Exception {
        JenkinsRule.WebClient wc = j.createWebClient();
        HtmlPage htmlPage = wc.goTo("configureSecurity");
        HtmlForm configForm = htmlPage.getFormByName("config");
        j.assertGoodStatus(j.submit(configForm));
    }

    /**
     * Ensure the form is still working when using {@link org.kohsuke.stapler.jelly.JellyFacet#TRACE}=true
     */
    @Test
    @Issue("JENKINS-65288")
    void submitPossibleWithJellyTrace() throws Exception {
        boolean currentValue = JellyFacet.TRACE;
        try {
            JellyFacet.TRACE = true;

            JenkinsRule.WebClient wc = j.createWebClient();
            HtmlPage htmlPage = wc.goTo("configureSecurity");
            HtmlForm configForm = htmlPage.getFormByName("config");
            j.assertGoodStatus(j.submit(configForm));
        } finally {
            JellyFacet.TRACE = currentValue;
        }
    }
}
