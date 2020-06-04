/*
 * The MIT License
 *
 * Copyright (c) 2004-2009, Sun Microsystems, Inc.
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

import com.gargoylesoftware.htmlunit.html.DomNodeList;
import com.gargoylesoftware.htmlunit.html.HtmlButton;
import com.gargoylesoftware.htmlunit.html.HtmlElement;
import com.gargoylesoftware.htmlunit.html.HtmlElementUtil;
import com.gargoylesoftware.htmlunit.html.HtmlFormUtil;
import hudson.model.UnprotectedRootAction;
import jenkins.model.Jenkins;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestExtension;
import org.kohsuke.stapler.QueryParameter;
import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.Extension;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import org.kohsuke.stapler.StaplerRequest;

import edu.umd.cs.findbugs.annotations.CheckForNull;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;

/**
 *
 *
 * @author Kohsuke Kawaguchi
 */
public class ValidateButtonTest {
    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Test
    public void testValidateIsCalled() throws Exception {
        TestValidateIsCalled.DescriptorImpl d = j.jenkins.getDescriptorByType(TestValidateIsCalled.DescriptorImpl.class);
        assertNotNull(d);
        
        d.test1Outcome = new Exception(); // if doValidateTest1() doesn't get invoked, we want to know.
        HtmlPage p = j.createWebClient().goTo("test");
        HtmlButton button = HtmlFormUtil.getButtonByCaption(p.getFormByName("config"), "test");
        HtmlElementUtil.click(button);
        
        if (d.test1Outcome!=null)
            throw d.test1Outcome;
    }
    
    @TestExtension("testValidateIsCalled")
    public static final class TestValidateIsCalled implements Describable<TestValidateIsCalled>, UnprotectedRootAction {
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
    
        public DescriptorImpl getDescriptor() {
            return Jenkins.get().getDescriptorByType(DescriptorImpl.class);
        }
    
        @Extension
        public static final class DescriptorImpl extends Descriptor<TestValidateIsCalled> {
            private Exception test1Outcome;
    
            public void doValidateTest1(@QueryParameter("a") String a, @QueryParameter("b") boolean b,
                                        @QueryParameter("c") boolean c, @QueryParameter("d") String d,
                                        @QueryParameter("e") String e) {
                try {
                    assertEquals("avalue",a);
                    assertTrue(b);
                    assertFalse(c);
                    assertEquals("dvalue",d);
                    assertEquals("e2",e);
                    test1Outcome = null;
                } catch (Exception t) {
                    test1Outcome = t;
                }
            }
        }
    }
    
    @Test
    public void noInjectionArePossible() throws Exception {
        NoInjectionArePossible.DescriptorImpl d = j.jenkins.getDescriptorByType(NoInjectionArePossible.DescriptorImpl.class);
        assertNotNull(d);
    
        checkRegularCase(d);
        checkInjectionInMethod(d);
        checkInjectionInWith(d);
    }
    
    private void checkRegularCase(NoInjectionArePossible.DescriptorImpl descriptor) throws Exception {
        descriptor.paramMethod = "validateInjection";
        descriptor.paramWith = "a,b";
        
        JenkinsRule.WebClient wc = j.createWebClient()
                .withThrowExceptionOnFailingStatusCode(false);
        HtmlPage p = wc.goTo("test");
        
        descriptor.wasCalled = false;
        HtmlElementUtil.click(getValidateButton(p));
        assertNotEquals("hacked", p.getTitleText());
        assertTrue(descriptor.wasCalled);
    }
    
    private void checkInjectionInMethod(NoInjectionArePossible.DescriptorImpl descriptor) throws Exception {
        descriptor.paramMethod = "validateInjection',document.title='hacked'+'";
        descriptor.paramWith = "a,b";
        
        JenkinsRule.WebClient wc = j.createWebClient()
                .withThrowExceptionOnFailingStatusCode(false);
        HtmlPage p = wc.goTo("test");
        
        // no check on wasCalled because the button that is expected by the method is not passed (arguments are shifted due to the injection)
        HtmlElementUtil.click(getValidateButton(p));
        assertNotEquals("hacked", p.getTitleText());
    }
    
    
    private void checkInjectionInWith(NoInjectionArePossible.DescriptorImpl descriptor) throws Exception {
        descriptor.paramMethod = "validateInjection";
        descriptor.paramWith = "a,b',document.title='hacked'+'";
        
        JenkinsRule.WebClient wc = j.createWebClient()
                .withThrowExceptionOnFailingStatusCode(false);
        HtmlPage p = wc.goTo("test");
        
        descriptor.wasCalled = false;
        HtmlElementUtil.click(getValidateButton(p));
        assertNotEquals("hacked", p.getTitleText());
        assertTrue(descriptor.wasCalled);
    }
    
    private HtmlButton getValidateButton(HtmlPage page){
        DomNodeList<HtmlElement> buttons = page.getElementById("test-panel").getElementsByTagName("button");
        assertEquals(1, buttons.size());
        return (HtmlButton) buttons.get(0);
    }
    
    @TestExtension("noInjectionArePossible")
    public static final class NoInjectionArePossible implements Describable<NoInjectionArePossible>, UnprotectedRootAction {
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
        
        public DescriptorImpl getDescriptor() {
            return Jenkins.get().getDescriptorByType(DescriptorImpl.class);
        }
        
        @Extension
        public static final class DescriptorImpl extends Descriptor<NoInjectionArePossible> {
            private boolean wasCalled = false;
            
            public String paramMethod = "validateInjection";
            public String paramWith = null;
            
            public void doValidateInjection(StaplerRequest request) {
                wasCalled = true;
            }
        }
    }
}