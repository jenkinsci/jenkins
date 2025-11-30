/*
 * The MIT License
 *
 * Copyright (c) 2010, CloudBees, Inc.
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

package hudson.markup;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;

import hudson.security.AuthorizationStrategy.Unsecured;
import hudson.security.HudsonPrivateSecurityRealm;
import java.io.IOException;
import java.io.Writer;
import java.net.URI;
import org.htmlunit.HttpMethod;
import org.htmlunit.Page;
import org.htmlunit.WebRequest;
import org.htmlunit.WebResponse;
import org.htmlunit.html.HtmlPage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestExtension;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * @author Kohsuke Kawaguchi
 */
@WithJenkins
class MarkupFormatterTest {

    private JenkinsRule j;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        j = rule;
    }

    @Test
    void configRoundtrip() throws Exception {
        j.jenkins.setSecurityRealm(new HudsonPrivateSecurityRealm(false));
        j.jenkins.setAuthorizationStrategy(new Unsecured());
        j.jenkins.setMarkupFormatter(new DummyMarkupImpl("hello"));
        j.configRoundtrip();

        assertEquals("hello", ((DummyMarkupImpl) j.jenkins.getMarkupFormatter()).prefix);
    }

    public static class DummyMarkupImpl extends MarkupFormatter {
        public final String prefix;

        @SuppressWarnings("checkstyle:redundantmodifier")
        @DataBoundConstructor
        public DummyMarkupImpl(String prefix) {
            this.prefix = prefix;
        }

        @Override
        public void translate(String markup, Writer output) throws IOException {
            output.write(prefix + "[" + markup + "]");
        }

        @TestExtension
        public static class DescriptorImpl extends MarkupFormatterDescriptor {}
    }

    @Test
    void defaultEscaped() throws Exception {
        assertEquals("&lt;your thing here&gt;", j.jenkins.getMarkupFormatter().translate("<your thing here>"));
        assertEquals("", j.jenkins.getMarkupFormatter().translate(""));
        assertEquals("", j.jenkins.getMarkupFormatter().translate(null));
    }

    @Test
    @Issue("SECURITY-2153")
    void security2153RequiresPOST() throws Exception {
        final JenkinsRule.WebClient wc = j.createWebClient();
        wc.setThrowExceptionOnFailingStatusCode(false);
        final HtmlPage htmlPage = wc.goTo("markupFormatter/previewDescription?text=lolwut");
        final WebResponse response = htmlPage.getWebResponse();
        assertEquals(405, response.getStatusCode());
        assertThat(response.getContentAsString(), containsString("This endpoint now requires that POST requests are sent"));
        assertThat(response.getContentAsString(), not(containsString("lolwut")));
    }

    @Test
    @Issue("SECURITY-2153")
    void security2153SetsCSP() throws Exception {
        final JenkinsRule.WebClient wc = j.createWebClient();
        final Page htmlPage = wc.getPage(wc.addCrumb(new WebRequest(new URI(j.jenkins.getRootUrl() + "/markupFormatter/previewDescription?text=lolwut").toURL(), HttpMethod.POST)));
        final WebResponse response = htmlPage.getWebResponse();
        assertEquals(200, response.getStatusCode());
        assertThat(response.getContentAsString(), containsString("lolwut"));
        assertThat(response.getResponseHeaderValue("Content-Security-Policy"), containsString("default-src 'none';"));
    }
}
