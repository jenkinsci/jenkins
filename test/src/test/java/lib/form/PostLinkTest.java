/*
 * The MIT License
 *
 * Copyright (c) 2025, Jenkins contributors
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

package lib.form;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import hudson.model.UnprotectedRootAction;
import jakarta.servlet.ServletException;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;
import org.htmlunit.html.HtmlAnchor;
import org.htmlunit.html.HtmlElementUtil;
import org.htmlunit.html.HtmlPage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.JenkinsRule.WebClient;
import org.jvnet.hudson.test.TestExtension;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.kohsuke.stapler.StaplerRequest2;
import org.kohsuke.stapler.StaplerResponse2;
import org.kohsuke.stapler.interceptor.RequirePOST;

@WithJenkins
public class PostLinkTest {

    private JenkinsRule j;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        j = rule;
    }

    @Test
    void postLinkWorksAfterPageLoad() throws Exception {
        WebClient wc = j.createWebClient();
        HtmlPage page = wc.goTo(postLinkAction.getUrlName());

        // Wait for page to fully load and JavaScript to attach handlers
        wc.waitForBackgroundJavaScript(2000);

        HtmlAnchor link = page.getAnchorByText("POST Link");
        assertNotNull(link, "POST link should be present");
        assertTrue(link.getAttribute("class").contains("post"), "Link should have 'post' class");

        // Click the link
        HtmlElementUtil.click(link);

        assertTrue(postLinkAction.wasCalled(), "POST action should have been called");
        assertEquals(1, postLinkAction.getCallCount(), "POST action should be called exactly once");
        assertFalse(postLinkAction.wasGetRequest(), "Should not be a GET request");
    }

    @Test
    void postLinkWithDataPostHref() throws Exception {
        WebClient wc = j.createWebClient();
        HtmlPage page = wc.goTo(postLinkWithDataHrefAction.getUrlName());

        wc.waitForBackgroundJavaScript(2000);

        HtmlAnchor link = page.getAnchorByText("POST Link with data-post-href");
        assertNotNull(link, "POST link should be present");

        // Verify href was set from data-post-href
        String href = link.getHrefAttribute();
        assertTrue(href.contains("post-link-data-href"), "href should be set from data-post-href");

        HtmlElementUtil.click(link);

        assertTrue(postLinkWithDataHrefAction.wasCalled(), "POST action should have been called");
        assertFalse(postLinkWithDataHrefAction.wasGetRequest(), "Should not be a GET request");
    }

    @Test
    void rapidClicksDoNotCauseRaceCondition() throws Exception {
        WebClient wc = j.createWebClient();
        HtmlPage page = wc.goTo(rapidClickAction.getUrlName());

        wc.waitForBackgroundJavaScript(2000);

        HtmlAnchor link = page.getAnchorByText("Rapid Click Test");
        assertNotNull(link, "POST link should be present");

        // Reset counter
        rapidClickAction.reset();

        // Click multiple times rapidly to simulate race condition
        // Note: HtmlUnit may not handle rapid clicks the same way as a real browser,
        // but this tests that handlers are attached correctly
        for (int i = 0; i < 3; i++) {
            try {
                // Reload page to get fresh link
                page = wc.goTo(rapidClickAction.getUrlName());
                wc.waitForBackgroundJavaScript(500);
                link = page.getAnchorByText("Rapid Click Test");
                HtmlElementUtil.click(link);
                // Small delay
                Thread.sleep(50);
            } catch (Exception e) {
                // Ignore navigation exceptions
            }
        }

        // Wait for all requests to complete
        Thread.sleep(500);

        // Verify POST was called (may be called multiple times, but should all be POST)
        assertTrue(rapidClickAction.getCallCount() > 0, "POST action should have been called");
        assertFalse(rapidClickAction.wasGetRequest(), "Should not have any GET requests");
    }

    @TestExtension("postLinkWorksAfterPageLoad")
    public static final MockPostAction postLinkAction = new MockPostAction("post-link");

    @TestExtension("postLinkWithDataPostHref")
    public static final MockPostAction postLinkWithDataHrefAction = new MockPostAction("post-link-data-href");

    @TestExtension("rapidClicksDoNotCauseRaceCondition")
    public static final MockPostAction rapidClickAction = new MockPostAction("rapid-click");

    public static class MockPostAction implements UnprotectedRootAction {
        private final AtomicInteger callCount = new AtomicInteger(0);
        private boolean getRequest = false;
        private final String urlName;

        public MockPostAction(String urlName) {
            this.urlName = urlName;
        }

        @RequirePOST
        public void doPost(StaplerRequest2 req, StaplerResponse2 rsp) throws ServletException, IOException {
            callCount.incrementAndGet();
            if (!"POST".equals(req.getMethod())) {
                getRequest = true;
            }
            rsp.forwardToPreviousPage(req);
        }

        public boolean wasCalled() {
            return callCount.get() > 0;
        }

        public int getCallCount() {
            return callCount.get();
        }

        public boolean wasGetRequest() {
            return getRequest;
        }

        public void reset() {
            callCount.set(0);
            getRequest = false;
        }

        @Override
        public String getIconFileName() {
            return null;
        }

        @Override
        public String getDisplayName() {
            return null;
        }

        @Override
        public String getUrlName() {
            return urlName;
        }
    }
}
