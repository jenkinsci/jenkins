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

import com.gargoylesoftware.htmlunit.FailingHttpStatusCodeException;
import com.gargoylesoftware.htmlunit.HttpMethod;
import com.gargoylesoftware.htmlunit.Page;
import com.gargoylesoftware.htmlunit.WebRequest;
import hudson.model.UnprotectedRootAction;
import org.apache.commons.lang3.StringUtils;
import org.junit.Before;
import org.junit.ClassRule;
import org.jvnet.hudson.test.JenkinsRule;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.WebApp;
import org.kohsuke.stapler.WebMethod;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import java.awt.*;
import java.io.IOException;
import java.net.URL;

import static org.hamcrest.CoreMatchers.startsWith;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public abstract class StaplerAbstractTest {
    @ClassRule
    public static JenkinsRule rule = new JenkinsRule();
    protected JenkinsRule j;
    
    protected JenkinsRule.WebClient wc;
    
    protected WebApp webApp;
    
    protected static boolean filteredGetMethodTriggered = false;
    protected static boolean filteredDoActionTriggered = false;
    protected static boolean filteredFieldTriggered = false;
    
    @Before
    public void setUp() throws Exception {
        j = rule;
        j.jenkins.setCrumbIssuer(null);
        wc = j.createWebClient();
        
        this.webApp = (WebApp) j.jenkins.servletContext.getAttribute(WebApp.class.getName());
        
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
        
        public void doIndex() {replyOk();}
        
        @WebMethod(name = "valid")
        public void valid() {replyOk();}
    }
    
    protected static class ParentRenderable {
        public Renderable getRenderable(){
            return new Renderable();
        }
    }
    
    protected static class RenderablePoint extends Point {
        public void doIndex() {replyOk();}
    }
    
    //================================= utility methods =================================
    
    protected static void replyOk() {
        StaplerResponse resp = Stapler.getCurrentResponse();
        try {
            resp.getWriter().write("ok");
            resp.flushBuffer();
        } catch (IOException e) {}
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
        try {
            Page page = wc.getPage(new WebRequest(new URL(j.getURL(), url), method));
            assertEquals(200, page.getWebResponse().getStatusCode());
            assertThat(page.getWebResponse().getContentAsString(), startsWith("ok"));
            
            assertDoActionRequestWasNotBlocked();
            assertGetMethodActionRequestWasNotBlocked();
            assertFieldRequestWasNotBlocked();
        } catch (FailingHttpStatusCodeException e) {
            fail("Url " + url + " should be reachable, received " + e.getMessage() + " (" + e.getStatusCode() + ") instead.");
        }
    }
    
    protected void assertReachable(String url) throws IOException {
        assertReachable(url, HttpMethod.GET);
    }
    
    protected void assertReachableWithSettings(WebRequest request) throws IOException {
        Page page = wc.getPage(request);
        assertEquals(200, page.getWebResponse().getStatusCode());
        assertEquals("ok", page.getWebResponse().getContentAsString());
        assertDoActionRequestWasNotBlocked();
    }
    
    protected void assertReachableWithoutOk(String url) throws IOException {
        try {
            Page page = wc.getPage(new URL(j.getURL(), url));
            assertEquals(200, page.getWebResponse().getStatusCode());
        } catch (FailingHttpStatusCodeException e) {
            fail("Url " + url + " should be reachable, received " + e.getMessage() + " (" + e.getStatusCode() + ") instead.");
        }
    }
    
    protected void assertNotReachable(String url) throws IOException {
        try {
            wc.getPage(new URL(j.getURL(), url));
            fail("Url " + url + " is reachable but should not be, an not-found error is expected");
        } catch (FailingHttpStatusCodeException e) {
            assertEquals("Url " + url + " returns an error different from 404", 404, e.getResponse().getStatusCode());
        }
    }
}
