/*
 * The MIT License
 *
 * Copyright (c) 2018, CloudBees, Inc.
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

package jenkins.security.stapler;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import hudson.model.UnprotectedRootAction;
import java.awt.Point;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URL;
import org.apache.commons.lang3.StringUtils;
import org.htmlunit.FailingHttpStatusCodeException;
import org.htmlunit.HttpMethod;
import org.htmlunit.Page;
import org.htmlunit.WebRequest;
import org.junit.Before;
import org.junit.ClassRule;
import org.jvnet.hudson.test.JenkinsRule;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.StaplerResponse2;
import org.kohsuke.stapler.WebApp;
import org.kohsuke.stapler.WebMethod;

public abstract class StaplerAbstractTest {
    @ClassRule
    public static JenkinsRule rule = new JenkinsRule();
    protected JenkinsRule j;

    protected WebApp webApp;

    protected static boolean filteredGetMethodTriggered = false;
    protected static boolean filteredDoActionTriggered = false;
    protected static boolean filteredFieldTriggered = false;

    @Before
    public void setUp() throws Exception {
        j = rule;
        j.jenkins.setCrumbIssuer(null);

        this.webApp = (WebApp) j.jenkins.getServletContext().getAttribute(WebApp.class.getName());

        webApp.setFilteredGetterTriggerListener((f, req, rst, node, expression) -> {
            filteredGetMethodTriggered = true;
            return false;
        });
        webApp.setFilteredDoActionTriggerListener((f, req, rsp, node) -> {
            filteredDoActionTriggered = true;
            return false;
        });
        webApp.setFilteredFieldTriggerListener((f, req, rsp, node, expression) -> {
            filteredFieldTriggered = true;
            return false;
        });

        filteredGetMethodTriggered = false;
        filteredDoActionTriggered = false;
        filteredFieldTriggered = false;
    }

    //================================= utility class =================================

    protected static class AbstractUnprotectedRootAction implements UnprotectedRootAction {
        @Override
        public @CheckForNull String getIconFileName() {
            return null;
        }

        @Override
        public @CheckForNull String getDisplayName() {
            return null;
        }

        @Override
        public @CheckForNull String getUrlName() {
            return StringUtils.uncapitalize(this.getClass().getSimpleName());
        }
    }

    public static final String RENDERABLE_CLASS_SIGNATURE = "class jenkins.security.stapler.StaplerAbstractTest.Renderable";

    protected static class Renderable {

        public void doIndex() {
            replyOk();
        }

        @WebMethod(name = "valid")
        public void valid() {
            replyOk();
        }
    }

    protected static class ParentRenderable {
        public Renderable getRenderable() {
            return new Renderable();
        }
    }

    protected static class RenderablePoint extends Point {
        public void doIndex() {
            replyOk();
        }
    }

    //================================= utility methods =================================

    protected static void replyOk() {
        StaplerResponse2 resp = Stapler.getCurrentResponse2();
        try {
            resp.getWriter().write("ok");
            resp.flushBuffer();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    //================================= testing methods =================================

    protected void assertGetMethodRequestWasBlockedAndResetFlag() {
        assertTrue("No get method request was blocked", filteredGetMethodTriggered);
        filteredGetMethodTriggered = false;
    }

    protected void assertDoActionRequestWasBlockedAndResetFlag() {
        assertTrue("No do action request was blocked", filteredDoActionTriggered);
        filteredDoActionTriggered = false;
    }

    protected void assertFieldRequestWasBlockedAndResetFlag() {
        assertTrue("No field request was blocked", filteredFieldTriggered);
        filteredFieldTriggered = false;
    }

    protected void assertGetMethodActionRequestWasNotBlocked() {
        assertFalse("There was at least one get method request that was blocked", filteredGetMethodTriggered);
    }

    protected void assertDoActionRequestWasNotBlocked() {
        assertFalse("There was at least one do action request that was blocked", filteredDoActionTriggered);
    }

    protected void assertFieldRequestWasNotBlocked() {
        assertFalse("There was at least one field request that was blocked", filteredFieldTriggered);
    }

    protected void assertReachable(String url, HttpMethod method) throws IOException {
        try (JenkinsRule.WebClient wc = j.createWebClient()) {
            Page page = wc.getPage(new WebRequest(new URL(j.getURL(), url), method));
            assertEquals(200, page.getWebResponse().getStatusCode());
            assertThat(page.getWebResponse().getContentAsString(), startsWith("ok"));

            assertDoActionRequestWasNotBlocked();
            assertGetMethodActionRequestWasNotBlocked();
            assertFieldRequestWasNotBlocked();
        } catch (FailingHttpStatusCodeException e) {
            throw new AssertionError("Url " + url + " should be reachable, received " + e.getMessage() + " (" + e.getStatusCode() + ") instead.", e);
        }
    }

    protected void assertReachable(String url) throws IOException {
        assertReachable(url, HttpMethod.GET);
    }

    protected void assertReachableWithSettings(WebRequest request) throws IOException {
        try (JenkinsRule.WebClient wc = j.createWebClient()) {
            Page page = wc.getPage(request);
            assertEquals(200, page.getWebResponse().getStatusCode());
            assertEquals("ok", page.getWebResponse().getContentAsString());
        }
        assertDoActionRequestWasNotBlocked();
    }

    protected void assertReachableWithoutOk(String url) throws IOException {
        try (JenkinsRule.WebClient wc = j.createWebClient()) {
            Page page = wc.getPage(new URL(j.getURL(), url));
            assertEquals(200, page.getWebResponse().getStatusCode());
        } catch (FailingHttpStatusCodeException e) {
            throw new AssertionError("Url " + url + " should be reachable, received " + e.getMessage() + " (" + e.getStatusCode() + ") instead.", e);
        }
    }

    protected void assertNotReachable(String url) throws IOException {
        try (JenkinsRule.WebClient wc = j.createWebClient()) {
            FailingHttpStatusCodeException e = assertThrows("Url " + url + " is reachable but should not be, a not-found error is expected", FailingHttpStatusCodeException.class, () -> wc.getPage(new URL(j.getURL(), url)));
            assertEquals("Url " + url + " returns an error different from 404", 404, e.getResponse().getStatusCode());
        }
    }
}
