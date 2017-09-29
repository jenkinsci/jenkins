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
package hudson.util;


import com.gargoylesoftware.htmlunit.WebResponseListener;
import hudson.model.FreeStyleProject;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Publisher;
import hudson.util.FormFieldValidatorTest.BrokenFormValidatorBuilder.DescriptorImpl;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.recipes.WithPlugin;

/**
 * @author Kohsuke Kawaguchi
 */
public class FormFieldValidatorTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Test
    @Issue("JENKINS-2771")
    @WithPlugin("tasks.jpi")
    public void configure() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject();
        j.createWebClient().getPage(p, "configure");
    }

    public static class BrokenFormValidatorBuilder extends Publisher {
        public static final class DescriptorImpl extends BuildStepDescriptor {
            public boolean isApplicable(Class jobType) {
                return true;
            }

            public void doCheckXyz() {
                throw new Error("doCheckXyz is broken");
            }
        }

        public BuildStepMonitor getRequiredMonitorService() {
            return BuildStepMonitor.BUILD;
        }
    }

    /**
     * Make sure that the validation methods are really called by testing a negative case.
     */
    @Test
    @Issue("JENKINS-3382")
    public void negative() throws Exception {
        DescriptorImpl d = new DescriptorImpl();
        Publisher.all().add(d);
        try {
            FreeStyleProject p = j.createFreeStyleProject();
            p.getPublishersList().add(new BrokenFormValidatorBuilder());

            JenkinsRule.WebClient webclient = j.createWebClient();
            WebResponseListener.StatusListener statusListener = new WebResponseListener.StatusListener(500);
            webclient.addWebResponseListener(statusListener);

            webclient.getPage(p, "configure");

            statusListener.assertHasResponses();
            String contentAsString = statusListener.getResponses().get(0).getContentAsString();
            Assert.assertTrue(contentAsString.contains("doCheckXyz is broken"));
        } finally {
            Publisher.all().remove(d);
        }
    }
}
