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

package hudson.tools;

import static org.junit.Assert.assertEquals;

import hudson.model.User;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import jenkins.model.Jenkins;
import org.htmlunit.HttpMethod;
import org.htmlunit.WebRequest;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MockAuthorizationStrategy;

public class ZipExtractionInstallerTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @Test
    @Issue("SECURITY-794")
    public void onlyAdminCanReachTheDoCheck() throws Exception {
        final String ADMIN = "admin";
        final String USER = "user";

        j.jenkins.setCrumbIssuer(null);
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy()
                .grant(Jenkins.ADMINISTER).everywhere().to(ADMIN)
                .grant(Jenkins.READ).everywhere().to(USER)
        );

        User.getById(ADMIN, true);
        User.getById(USER, true);

        WebRequest request = new WebRequest(new URL(j.getURL() + "descriptorByName/hudson.tools.ZipExtractionInstaller/checkUrl"), HttpMethod.POST);
        request.setRequestBody(URLEncoder.encode("value=https://www.google.com", StandardCharsets.UTF_8));

        JenkinsRule.WebClient adminWc = j.createWebClient();
        adminWc.login(ADMIN);
        assertEquals(HttpURLConnection.HTTP_OK, adminWc.getPage(request).getWebResponse().getStatusCode());

        JenkinsRule.WebClient userWc = j.createWebClient()
                .withThrowExceptionOnFailingStatusCode(false);
        userWc.login(USER);
        assertEquals(HttpURLConnection.HTTP_FORBIDDEN, userWc.getPage(request).getWebResponse().getStatusCode());
    }
}
