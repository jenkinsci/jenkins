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


import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;

import hudson.model.AbstractProject;
import hudson.model.FreeStyleProject;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Builder;
import hudson.tasks.Publisher;
import hudson.tasks.Recorder;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;
import org.htmlunit.ScriptResult;
import org.htmlunit.WebResponseListener;
import org.htmlunit.html.HtmlPage;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestExtension;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.xml.sax.SAXException;

/**
 * @author Kohsuke Kawaguchi
 */
public class FormFieldValidatorTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Test
    @Issue("JENKINS-2771")
    public void configure() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject();
        j.createWebClient().getPage(p, "configure");
    }

    public static class BrokenFormValidatorBuilder extends Publisher {
        public static final class DescriptorImpl extends BuildStepDescriptor {
            @Override
            public boolean isApplicable(Class jobType) {
                return true;
            }

            public FormValidation doCheckXyz() {
                throw new Error("doCheckXyz is broken");
            }
        }

        @Override
        public BuildStepMonitor getRequiredMonitorService() {
            return BuildStepMonitor.BUILD;
        }
    }

    @Test
    @Issue("JENKINS-15604")
    public void testCodeMirrorBlur() throws IOException, SAXException {
        final FreeStyleProject freeStyleProject = j.createFreeStyleProject();
        freeStyleProject.getBuildersList().add(new CodeMirrorStep(""));
        freeStyleProject.save();
        final JenkinsRule.WebClient wc = j.createWebClient();
        final HtmlPage page = wc.getPage(freeStyleProject, "configure");

        // get initial value
        final ScriptResult scriptResult1 = page.executeJavaScript("document.querySelectorAll('.validation-error-area--visible .ok')[1].textContent");
        final long javaScriptResult1 = Long.parseLong((String) scriptResult1.getJavaScriptResult());
        Assert.assertEquals(System.currentTimeMillis(), javaScriptResult1, 5000); // value is expected to be roughly "now"

        // focus then blur to update
        page.executeJavaScript("document.querySelector('.CodeMirror textarea').dispatchEvent(new Event(\"focus\"))");
        page.executeJavaScript("document.querySelector('.CodeMirror textarea').dispatchEvent(new Event(\"blur\"))");
        wc.waitForBackgroundJavaScript(1000); // Unsure whether this is needed

        // get updated value
        final ScriptResult scriptResult2 = page.executeJavaScript("document.querySelectorAll('.validation-error-area--visible .ok')[1].textContent");
        final long javaScriptResult2 = Long.parseLong((String) scriptResult2.getJavaScriptResult());
        Assert.assertEquals(System.currentTimeMillis(), javaScriptResult2, 5000); // value is expected to be roughly "now"

        // value should have changed
        Assert.assertNotEquals(javaScriptResult1, javaScriptResult2);
    }

    public static class CodeMirrorStep extends Builder {
        private String command;

        @DataBoundConstructor
        public CodeMirrorStep(String command) {
            this.command = command;
        }

        @TestExtension
        public static class DescriptorImpl extends BuildStepDescriptor<Builder> {
            public FormValidation doCheckCommand(@QueryParameter String command) {
                return FormValidation.ok("" + System.currentTimeMillis());
            }

            @Override
            public boolean isApplicable(Class<? extends AbstractProject> jobType) {
                return true;
            }
        }
    }

    /**
     * Make sure that the validation methods are really called by testing a negative case.
     */
    @Test
    @Issue("JENKINS-3382")
    public void negative() throws Exception {
        BrokenFormValidatorBuilder.DescriptorImpl d = new BrokenFormValidatorBuilder.DescriptorImpl();
        Recorder.all().add(d);
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

    @Issue("JENKINS-73404")
    @Test
    public void testValidationforComponents() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject();
        p.getBuildersList().add(new ValidatingDescribable());
        try (JenkinsRule.WebClient wc = j.createWebClient()) {
            HtmlPage page = wc.getPage(p, "configure");
            assertThat(page.asNormalizedText(), allOf(
                    containsString("FormValidation: Password (empty)"),
                    containsString("FormValidation: Password (populated)"),
                    containsString("FormValidation: Textarea"),
                    containsString("FormValidation: SecretTextarea (empty)"),
                    containsString("FormValidation: SecretTextarea (populated)")));

        }

    }

    public static class ValidatingDescribable extends Builder {

        private Secret emptyPassword;
        // give the secret some data so that it is hidden and not a regular field!
        private Secret populatedPassword = Secret.fromString("secret!");
        private String textarea;
        private Secret emptySecretTextarea;
        private Secret populatedSecretTextarea = Secret.fromString("sensitive!");;

        @DataBoundConstructor
        public ValidatingDescribable() {
        }

        public Secret getEmptyPassword() {
            return emptyPassword;
        }

        @DataBoundSetter
        public void setEmptyPassword(Secret emptyPassword) {
            this.emptyPassword = emptyPassword;
        }

        public Secret getPopulatedPassword() {
            return populatedPassword;
        }

        @DataBoundSetter
        public void setPopulatedPassword(Secret populatedPassword) {
            this.populatedPassword = populatedPassword;
        }

        public String getTextarea() {
            return textarea;
        }

        @DataBoundSetter
        public void setTextarea(String textarea) {
            this.textarea = textarea;
        }

        public Secret getEmptySecretTextarea() {
            return emptySecretTextarea;
        }

        @DataBoundSetter
        public void setEmptySecretTextarea(Secret emptySecretTextarea) {
            this.emptySecretTextarea = emptySecretTextarea;
        }

        public Secret getPopulatedSecretTextarea() {
            return populatedSecretTextarea;
        }

        @DataBoundSetter
        public void setPopulatedSecretTextarea(Secret populatedSecretTextarea) {
            this.populatedSecretTextarea = populatedSecretTextarea;
        }

        @TestExtension
        public static class DescriptorImpl extends BuildStepDescriptor<Builder> {
            // not used for the test class but useful for interactive debugging to check the validation has been called
            AtomicInteger i = new AtomicInteger();

            @Override
            public String getDisplayName() {
                return "Validation Testing";
            }

            public FormValidation doCheckEmptyPassword(@QueryParameter String value) {
                return FormValidation.ok("FormValidation: Password (empty)" + i.getAndIncrement());
            }

            public FormValidation doCheckPopulatedPassword(@QueryParameter String value) {
                return FormValidation.ok("FormValidation: Password (populated)" + i.getAndIncrement());
            }

            public FormValidation doCheckTextarea(@QueryParameter String value) {
                return FormValidation.ok("FormValidation: Textarea" + i.getAndIncrement());
            }

            public FormValidation doCheckEmptySecretTextarea(@QueryParameter String value) {
                return FormValidation.ok("FormValidation: SecretTextarea (empty)" + i.getAndIncrement());
            }

            public FormValidation doCheckPopulatedSecretTextarea(@QueryParameter String value) {
                return FormValidation.ok("FormValidation: SecretTextarea (populated)" + i.getAndIncrement());
            }

            @Override
            public boolean isApplicable(Class jobType) {
                return true;
            }

        }

    }

}
