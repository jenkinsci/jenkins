/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi, Erik Ramfelt, CloudBees, Inc.
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
import hudson.model.Descriptor.FormException;
import net.sf.json.JSONObject;
import org.jvnet.hudson.test.Bug;
import org.jvnet.hudson.test.HudsonTestCase;
import org.jvnet.hudson.test.TestExtension;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;

public class JobPropertyTest extends HudsonTestCase {

    /**
     * Asserts that rfe#2398 is fixed.
     */
    @Bug(2398)
    public void testJobPropertySummaryIsShownInMavenModuleSetIndexPage() throws Exception {
        assertJobPropertySummaryIsShownInIndexPage(MavenModuleSet.class);
    }
    public void testJobPropertySummaryIsShownInMatrixProjectIndexPage() throws Exception {
        assertJobPropertySummaryIsShownInIndexPage(MatrixProject.class);
    }
    public void testJobPropertySummaryIsShownInFreeStyleProjectIndexPage() throws Exception {
        assertJobPropertySummaryIsShownInIndexPage(FreeStyleProject.class);
    }

    private void assertJobPropertySummaryIsShownInIndexPage(Class<? extends TopLevelItem> type) throws Exception {
        JobPropertyImpl jp = new JobPropertyImpl("NeedleInPage");
        Job<?,?> project = (Job<?, ?>) jenkins.createProject(type, "job-test-case");
        project.addProperty(jp);
        
        HtmlPage page = new WebClient().goTo("job/job-test-case");
        WebAssert.assertTextPresent(page, "NeedleInPage");
    }
    
    public static class JobPropertyImpl extends JobProperty<Job<?,?>> {
        private final String propertyString;
        public JobPropertyImpl(String propertyString) {
            this.propertyString = propertyString;
        }

        public String getPropertyString() {
            return propertyString;
        }

        @TestExtension
        public static class DescriptorImpl extends JobPropertyDescriptor {
            @SuppressWarnings("rawtypes")
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

    /**
     * Make sure that the UI rendering works as designed.
     */
    public void testConfigRoundtrip() throws Exception {
        FreeStyleProject p = createFreeStyleProject();
        JobPropertyWithConfigImpl before = new JobPropertyWithConfigImpl("Duke");
        p.addProperty(before);
        configRoundtrip((Item)p);
        JobPropertyWithConfigImpl after = p.getProperty(JobPropertyWithConfigImpl.class);
        assertNotSame(after,before);
        assertEqualDataBoundBeans(before, after);
    }

    public static class JobPropertyWithConfigImpl extends JobProperty<Job<?,?>> {
        public String name;

        @DataBoundConstructor
        public JobPropertyWithConfigImpl(String name) {
            this.name = name;
        }

        @TestExtension("testConfigRoundtrip")
        public static class DescriptorImpl extends JobPropertyDescriptor {
            @Override
            public String getDisplayName() { return null; }
        }
    }



    public void testInvisibleProperty() throws Exception {
        FreeStyleProject p = createFreeStyleProject();
        InvisibleImpl before = new InvisibleImpl();
        p.addProperty(before);
        configRoundtrip((Item)p);
        InvisibleImpl after = p.getProperty(InvisibleImpl.class);
        assertSame(after,before);
    }

    public static class InvisibleImpl extends JobProperty<Job<?,?>> {
        public String name;

        InvisibleImpl() {}

        @Override
        public JobProperty<?> reconfigure(StaplerRequest req, JSONObject form) throws FormException {
            return this;
        }

        @TestExtension("testInvisibleProperty")
        public static class DescriptorImpl extends JobPropertyDescriptor {
            @Override
            public String getDisplayName() { return null; }
        }
    }
}
