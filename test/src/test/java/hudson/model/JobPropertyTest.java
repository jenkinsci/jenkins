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

import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import hudson.matrix.MatrixProject;
import hudson.model.Descriptor.FormException;
import java.util.logging.Level;
import net.sf.json.JSONObject;
import org.htmlunit.WebAssert;
import org.htmlunit.html.HtmlPage;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.LoggerRule;
import org.jvnet.hudson.test.TestExtension;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest2;

public class JobPropertyTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Rule
    public LoggerRule logs = new LoggerRule();

    @Test
    @Issue("JENKINS-2398")
    public void jobPropertySummaryIsShownInMatrixProjectIndexPage() throws Exception {
        assertJobPropertySummaryIsShownInIndexPage(MatrixProject.class);
    }

    @Test
    @Issue("JENKINS-2398")
    public void jobPropertySummaryIsShownInFreeStyleProjectIndexPage() throws Exception {
        assertJobPropertySummaryIsShownInIndexPage(FreeStyleProject.class);
    }

    private void assertJobPropertySummaryIsShownInIndexPage(Class<? extends TopLevelItem> type) throws Exception {
        JobPropertyImpl jp = new JobPropertyImpl("NeedleInPage");
        Job<?, ?> project = (Job<?, ?>) j.jenkins.createProject(type, "job-test-case");
        project.addProperty(jp);

        HtmlPage page = j.createWebClient().goTo("job/job-test-case");
        WebAssert.assertTextPresent(page, "NeedleInPage");
    }

    public static class JobPropertyImpl extends JobProperty<Job<?, ?>> {
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
        }
    }

    /**
     * Make sure that the UI rendering works as designed.
     */
    @Test
    public void configRoundtrip() throws Exception {
        logs.record(Descriptor.class, Level.ALL);
        FreeStyleProject p = j.createFreeStyleProject();
        JobPropertyWithConfigImpl before = new JobPropertyWithConfigImpl("Duke");
        p.addProperty(before);
        j.configRoundtrip((Item) p);
        JobPropertyWithConfigImpl after = p.getProperty(JobPropertyWithConfigImpl.class);
        assertNotSame(after, before);
        j.assertEqualDataBoundBeans(before, after);
        p.removeProperty(after);
        JobPropertyWithConfigImpl empty = new JobPropertyWithConfigImpl("");
        p.addProperty(empty);
        j.configRoundtrip((Item) p);
        assertNull(p.getProperty(JobPropertyWithConfigImpl.class));
    }

    public static class JobPropertyWithConfigImpl extends JobProperty<Job<?, ?>> {
        public String name;

        @DataBoundConstructor
        public JobPropertyWithConfigImpl(String name) {
            this.name = name;
        }

        @TestExtension("configRoundtrip")
        public static class DescriptorImpl extends JobPropertyDescriptor {
            @Override
            public JobProperty<?> newInstance(StaplerRequest2 req, JSONObject formData) throws FormException {
                JobPropertyWithConfigImpl prop = (JobPropertyWithConfigImpl) super.newInstance(req, formData);
                return prop.name.isEmpty() ? null : prop;
            }
        }
    }

    @Test
    public void invisibleProperty() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject();
        InvisibleImpl before = new InvisibleImpl();
        p.addProperty(before);
        j.configRoundtrip((Item) p);
        InvisibleImpl after = p.getProperty(InvisibleImpl.class);
        assertSame(after, before);
    }

    public static class InvisibleImpl extends JobProperty<Job<?, ?>> {
        public String name;

        InvisibleImpl() {}

        @Override
        public JobProperty<?> reconfigure(StaplerRequest2 req, JSONObject form) {
            return this;
        }

        @TestExtension("invisibleProperty")
        public static class DescriptorImpl extends JobPropertyDescriptor {}
    }
}
