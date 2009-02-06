/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi, Erik Ramfelt
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
import hudson.matrix.MatrixProject;
import hudson.maven.MavenModuleSet;
import org.jvnet.hudson.test.HudsonTestCase;

public class JobPropertyTest extends HudsonTestCase {

    /**
     * Asserts that rfe#2398 is fixed.
     */
    public void testJobPropertySummaryIsShownInMavenModuleSetIndexPage() throws Exception {
        assertJobPropertySummaryIsShownInIndexPage(MavenModuleSet.DESCRIPTOR);
    }
    public void testJobPropertySummaryIsShownInMatrixProjectIndexPage() throws Exception {
        assertJobPropertySummaryIsShownInIndexPage(MatrixProject.DESCRIPTOR);
    }
    public void testJobPropertySummaryIsShownInFreeStyleProjectIndexPage() throws Exception {
        assertJobPropertySummaryIsShownInIndexPage(FreeStyleProject.DESCRIPTOR);
    }

    private void assertJobPropertySummaryIsShownInIndexPage(TopLevelItemDescriptor type) throws Exception {
        JobPropertyImpl jp = new JobPropertyImpl("NeedleInPage");
        Job<?,?> project = (Job<?, ?>) hudson.createProject(type, "job-test-case");
        project.addProperty(jp);
        
        HtmlPage page = new WebClient().goTo("job/job-test-case");
        WebAssert.assertTextPresent(page, "NeedleInPage");
    }
    
    public static class JobPropertyImpl extends JobProperty<Job<?,?>> {
        public static DescriptorImpl DESCRIPTOR = new DescriptorImpl();
        private final String propertyString;
        public JobPropertyImpl(String propertyString) {
            this.propertyString = propertyString;
        }

        public String getPropertyString() {
            return propertyString;
        }

        @Override
        public JobPropertyDescriptor getDescriptor() {
            return DESCRIPTOR;
        }

        @SuppressWarnings("unchecked")        
        private static class DescriptorImpl extends JobPropertyDescriptor {
            @Override
            public boolean isApplicable(Class<? extends Job> jobType) {
                return false;
            }

            @Override
            public String getDisplayName() {
                return "Fake job property";
            }
        }
    }
}
