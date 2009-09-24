/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi
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
package hudson.model;

import com.gargoylesoftware.htmlunit.WebAssert;
import com.gargoylesoftware.htmlunit.html.HtmlPage;

import org.jvnet.hudson.test.HudsonTestCase;
import hudson.Extension;
import hudson.model.JobTest.JobPropertyImpl.DescriptorImpl;

/**
 * @author Kohsuke Kawaguchi
 */
public class JobTest extends HudsonTestCase {

    @SuppressWarnings("unchecked")
    public void testJobPropertySummaryIsShownInMainPage() throws Exception {
        AbstractProject project = createFreeStyleProject();
        project.addProperty(new JobPropertyImpl("NeedleInPage"));
                
        HtmlPage page = new WebClient().getPage(project);
        WebAssert.assertTextPresent(page, "NeedleInPage");
    }
    
    @SuppressWarnings("unchecked")
    public static class JobPropertyImpl extends JobProperty {
        private final String testString;
        
        public JobPropertyImpl(String testString) {
            this.testString = testString;
        }
        
        public String getTestString() {
            return testString;
        }

        @Extension
        public static final class DescriptorImpl extends JobPropertyDescriptor {
            public String getDisplayName() {
                return "";
            }
        }
    }
}
