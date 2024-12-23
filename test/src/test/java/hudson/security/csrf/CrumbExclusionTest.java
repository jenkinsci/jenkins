/*
 * The MIT License
 *
 * Copyright 2020 CloudBees, Inc.
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

package hudson.security.csrf;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.fail;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import hudson.ExtensionList;
import hudson.model.UnprotectedRootAction;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URL;
import jenkins.model.Jenkins;
import org.htmlunit.FailingHttpStatusCodeException;
import org.htmlunit.HttpMethod;
import org.htmlunit.WebRequest;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MockAuthorizationStrategy;
import org.jvnet.hudson.test.TestExtension;
import org.kohsuke.stapler.verb.POST;

public class CrumbExclusionTest {

    @Rule
    public JenkinsRule r = new JenkinsRule();

    @Issue("SECURITY-1774")
    @Test
    public void pathInfo() throws Exception {
        r.jenkins.setSecurityRealm(r.createDummySecurityRealm());
        r.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy().grant(Jenkins.ADMINISTER).everywhere().to("admin"));
        for (String path : new String[] {/* control */ "scriptText", /* test */ "scriptText/..;/cli"}) {
            try {
                fail(path + " should have been rejected: " + r.createWebClient().login("admin").getPage(new WebRequest(new URL(r.getURL(), path + "?script=11*11"), HttpMethod.POST)).getWebResponse().getContentAsString());
            } catch (FailingHttpStatusCodeException x) {
                switch (x.getStatusCode()) {
                case 403:
                    assertThat("error message using " + path, x.getResponse().getContentAsString(), containsString("No valid crumb was included in the request"));
                    break;
                case 400: // from Jetty
                    assertThat("error message using " + path, x.getResponse().getContentAsString(), containsString("path parameter"));
                    break;
                default:
                    fail("unexpected error code");
                }
            }
        }
    }

    @Test
    public void regular() throws Exception {
        r.createWebClient().getPage(new WebRequest(new URL(r.getURL(), "root/"), HttpMethod.POST));
        Assert.assertTrue(ExtensionList.lookupSingleton(RootActionImpl.class).posted);
    }

    @TestExtension
    public static class RootActionImpl implements UnprotectedRootAction {

        public boolean posted = false;

        @Override
        @CheckForNull
        public String getIconFileName() {
            return null;
        }

        @Override
        @CheckForNull
        public String getDisplayName() {
            return null;
        }

        @Override
        @CheckForNull
        public String getUrlName() {
            return "root";
        }

        @POST
        public void doIndex() {
            posted = true;
        }
    }

    @TestExtension
    public static class CrumbExclusionImpl extends CrumbExclusion {

        @Override
        public boolean process(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws IOException, ServletException {
            String pathInfo = request.getPathInfo();
            if (pathInfo != null && pathInfo.startsWith("/root/")) {
                chain.doFilter(request, response);
                return true;
            }
            return false;
        }
    }
}
