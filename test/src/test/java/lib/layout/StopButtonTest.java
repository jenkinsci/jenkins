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

import com.gargoylesoftware.htmlunit.html.DomNodeList;
import com.gargoylesoftware.htmlunit.html.HtmlAnchor;
import com.gargoylesoftware.htmlunit.html.HtmlElement;
import com.gargoylesoftware.htmlunit.html.HtmlElementUtil;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import hudson.model.UnprotectedRootAction;
import hudson.util.HttpResponses;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestExtension;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.WebMethod;

import edu.umd.cs.findbugs.annotations.CheckForNull;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class StopButtonTest {
    @Rule
    public JenkinsRule j = new JenkinsRule();
    
    private static final String hrefPayload = "\",document.title='hacked',\"";
    private static final String postPayload = "\",document.title='hacked',\"";
    
    @Test
    public void noInjectionArePossible() throws Exception {
        TestRootAction testParams = j.jenkins.getExtensionList(UnprotectedRootAction.class).get(TestRootAction.class);
        assertNotNull(testParams);

        checkRegularCase(testParams);
        checkInjectionInHref(testParams);
        checkInjectionInHrefWithConfirm(testParams);
        checkInjectionInConfirm(testParams);
    }
    
    private void checkRegularCase(TestRootAction testParams) throws Exception {
        testParams.paramHref = "#";
        testParams.paramAlt = "Message to confirm the click";
        testParams.paramConfirm = null;
        
        HtmlPage p = j.createWebClient().goTo("test");
        assertTrue(p.getWebResponse().getContentAsString().contains("Message to confirm the click"));
    }
    
    private void checkInjectionInHref(TestRootAction testParams) throws Exception {
        testParams.paramHref = hrefPayload;
        testParams.paramAlt = "Alternative text for icon";
        testParams.paramConfirm = null;
        
        JenkinsRule.WebClient wc = j.createWebClient()
                .withThrowExceptionOnFailingStatusCode(false);
        HtmlPage p = wc.goTo("test");
    
        HtmlElementUtil.click(getStopLink(p));
        assertNotEquals("hacked", p.getTitleText());
        assertTrue(p.getWebResponse().getContentAsString().contains("Alternative text for icon"));
    }
    
    private void checkInjectionInHrefWithConfirm(TestRootAction testParams) throws Exception {
        testParams.paramHref = hrefPayload;
        testParams.paramAlt = "Alternative text for icon";
        testParams.paramConfirm = "Confirm message";
        
        JenkinsRule.WebClient wc = j.createWebClient()
                .withThrowExceptionOnFailingStatusCode(false);
        HtmlPage p = wc.goTo("test");
    
        HtmlElementUtil.click(getStopLink(p));
        assertNotEquals("hacked", p.getTitleText());
        assertTrue(p.getWebResponse().getContentAsString().contains("Alternative text for icon"));
    }
    
    private void checkInjectionInConfirm(TestRootAction testParams) throws Exception {
        testParams.paramHref = "#";
        testParams.paramAlt = "Alternative text for icon";
        testParams.paramConfirm = postPayload;
        
        JenkinsRule.WebClient wc = j.createWebClient()
                .withThrowExceptionOnFailingStatusCode(false);
        HtmlPage p = wc.goTo("test");
    
        HtmlElementUtil.click(getStopLink(p));
        assertNotEquals("hacked", p.getTitleText());
        assertTrue(p.getWebResponse().getContentAsString().contains("Alternative text for icon"));
    }
    
    private HtmlAnchor getStopLink(HtmlPage page){
        DomNodeList<HtmlElement> anchors = page.getElementById("test-panel").getElementsByTagName("a");
        assertEquals(1, anchors.size());
        return (HtmlAnchor) anchors.get(0);
    }
    
    @TestExtension("noInjectionArePossible")
    public static class TestRootAction implements UnprotectedRootAction {
        
        public String paramHref = "";
        public String paramAlt = "";
        public String paramConfirm;
        
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
            return "test";
        }
        
        @WebMethod(name = "submit")
        public HttpResponse doSubmit(StaplerRequest request) {
            return HttpResponses.plainText("method:" + request.getMethod());
        }
    }
}
