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

package lib.layout;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import hudson.model.UnprotectedRootAction;
import hudson.util.HttpResponses;
import java.io.IOException;
import java.net.HttpURLConnection;
import org.htmlunit.Page;
import org.htmlunit.html.DomNodeList;
import org.htmlunit.html.HtmlAnchor;
import org.htmlunit.html.HtmlButton;
import org.htmlunit.html.HtmlElement;
import org.htmlunit.html.HtmlElementUtil;
import org.htmlunit.html.HtmlPage;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestExtension;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.StaplerRequest2;
import org.kohsuke.stapler.WebMethod;

public class ConfirmationLinkTest {
    @Rule
    public JenkinsRule j = new JenkinsRule();

    private static final String hrefPayload = "',document.title='hacked'+'";
    private static final String messagePayload = "',document.title='hacked'+'";
    private static final String postPayload = "document.title='hacked'";

    @Test
    public void noInjectionArePossible() throws Exception {
        TestRootAction testParams = j.jenkins.getExtensionList(UnprotectedRootAction.class).get(TestRootAction.class);
        assertNotNull(testParams);

        checkRegularCase(testParams);
        checkRegularCasePost(testParams);
        checkInjectionInHref(testParams);
        checkInjectionInMessage(testParams);
        checkInjectionInPost(testParams);
    }

    private void checkRegularCase(TestRootAction testParams) throws Exception {
        testParams.paramHref = "#";
        testParams.paramMessage = "Message to confirm the click";
        testParams.paramClass = null;
        testParams.paramPost = null;

        HtmlPage p = j.createWebClient().goTo("test");
        assertTrue(p.getWebResponse().getContentAsString().contains("Message to confirm the click"));
    }

    private void checkRegularCasePost(TestRootAction testParams) throws Exception {
        testParams.paramHref = "submit";
        testParams.paramMessage = "Message to confirm the click";
        testParams.paramClass = null;

        testParams.paramPost = true;
        assertMethodPostAfterClick();

        testParams.paramPost = "true";
        assertMethodPostAfterClick();

        testParams.paramPost = false;
        assertMethodGetAfterClick();

        testParams.paramPost = "false";
        assertMethodGetAfterClick();

        testParams.paramPost = "any other string";
        assertMethodGetAfterClick();
    }

    private void assertMethodGetAfterClick() throws Exception {
        Page pageAfterClick = getPageAfterClick();
        assertTrue(pageAfterClick.getWebResponse().getContentAsString().contains("method:GET"));
    }

    private void assertMethodPostAfterClick() throws Exception {
        Page pageAfterClick = getPageAfterClick();
        assertTrue(pageAfterClick.getWebResponse().getContentAsString().contains("method:POST"));
    }

    private Page getPageAfterClick() throws Exception {
        JenkinsRule.WebClient wc = j.createWebClient()
                .withThrowExceptionOnFailingStatusCode(false);
        HtmlPage p = wc.goTo("test");

        return HtmlElementUtil.click(getClickableLink(p));
    }

    private void checkInjectionInHref(TestRootAction testParams) throws Exception {
        testParams.paramHref = hrefPayload;
        testParams.paramMessage = "Message to confirm the click";
        testParams.paramClass = null;
        testParams.paramPost = null;

        JenkinsRule.WebClient wc = j.createWebClient()
                .withThrowExceptionOnFailingStatusCode(false);
        HtmlPage p = wc.goTo("test");

        Page pageAfterClick = HtmlElementUtil.click(getClickableLink(p));
        assertNotEquals("hacked", p.getTitleText());
        assertTrue(p.getWebResponse().getContentAsString().contains("Message to confirm the click"));
        // the url it clicks on is escaped and so does not exist
        assertEquals(HttpURLConnection.HTTP_NOT_FOUND, pageAfterClick.getWebResponse().getStatusCode());
    }

    private void checkInjectionInMessage(TestRootAction testParams) throws Exception {
        testParams.paramHref = "#";
        testParams.paramMessage = messagePayload;
        testParams.paramClass = null;
        testParams.paramPost = null;

        JenkinsRule.WebClient wc = j.createWebClient()
                .withThrowExceptionOnFailingStatusCode(false);
        HtmlPage p = wc.goTo("test");

        Page pageAfterClick = HtmlElementUtil.click(getClickableLink(p));
        assertNotEquals("hacked", p.getTitleText());
        // the url is normally the same page so it's ok
        assertEquals(HttpURLConnection.HTTP_OK, pageAfterClick.getWebResponse().getStatusCode());
    }

    private void checkInjectionInPost(TestRootAction testParams) throws Exception {
        testParams.paramHref = "#";
        testParams.paramMessage = "Message to confirm the click";
        testParams.paramClass = null;
        testParams.paramPost = postPayload;

        JenkinsRule.WebClient wc = j.createWebClient()
                .withThrowExceptionOnFailingStatusCode(false);
        HtmlPage p = wc.goTo("test");

        Page pageAfterClick = HtmlElementUtil.click(getClickableLink(p));
        assertNotEquals("hacked", p.getTitleText());
        assertTrue(p.getWebResponse().getContentAsString().contains("Message to confirm the click"));
        // the url is normally the same page so it's ok
        assertEquals(HttpURLConnection.HTTP_OK, pageAfterClick.getWebResponse().getStatusCode());
    }

    private HtmlButton getClickableLink(HtmlPage page) throws IOException {
        HtmlElement document = page.getDocumentElement();
        DomNodeList<HtmlElement> anchors = page.getElementById("test-panel").getElementsByTagName("a");
        assertEquals(1, anchors.size());
        HtmlAnchor anchor = (HtmlAnchor) anchors.get(0);
        HtmlElementUtil.click(anchor);
        HtmlButton revokeButtonSelected = document.getOneHtmlElementByAttribute("button", "data-id", "ok");
        return revokeButtonSelected;
    }

    @TestExtension("noInjectionArePossible")
    public static final class TestRootAction implements UnprotectedRootAction {

        public String paramHref = "";
        public String paramMessage = "";
        public String paramClass;
        public Object paramPost;

        @Override
        public @CheckForNull String getIconFileName() {
            return null;
        }

        @Override
        public @CheckForNull String getDisplayName() {
            return null;
        }

        @Override
        public String getUrlName() {
            return "test";
        }

        @WebMethod(name = "submit")
        public HttpResponse doSubmit(StaplerRequest2 request) {
            return HttpResponses.plainText("method:" + request.getMethod());
        }
    }
}
