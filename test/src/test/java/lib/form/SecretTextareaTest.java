/*
 * The MIT License
 *
 * Copyright (c) 2019 CloudBees, Inc.
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.model.AbstractProject;
import hudson.model.Project;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.util.Secret;
import java.io.IOException;
import org.htmlunit.html.HtmlElement;
import org.htmlunit.html.HtmlForm;
import org.htmlunit.html.HtmlTextArea;
import org.htmlunit.html.HtmlTextInput;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.JenkinsRule.WebClient;
import org.jvnet.hudson.test.TestExtension;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.xml.sax.SAXException;

public class SecretTextareaTest {

    private Project<?, ?> project;
    private WebClient wc;

    @Rule public JenkinsRule j = new JenkinsRule();

    @Before
    public void setUp() throws IOException {
        project = j.createFreeStyleProject();
        project.getBuildersList().add(TestBuilder.newDefault());
        wc = j.createWebClient();
    }

    @Test
    public void addEmptySecret() throws Exception {
        j.configRoundtrip(project);
        assertTestBuilderDataBoundEqual(TestBuilder.newDefault());
    }

    @Test
    public void addSecret() throws Exception {
        setProjectSecret("testValue");
        assertTestBuilderDataBoundEqual(TestBuilder.fromString("testValue"));
    }

    @Test
    public void addSecretAndUpdateDescription() throws Exception {
        setProjectSecret("Original Value");
        assertTestBuilderDataBoundEqual(TestBuilder.fromString("Original Value"));
        HtmlForm configForm = goToConfigForm();
        HtmlTextInput description = configForm.getInputByName("_.description");
        description.setText("New description");
        j.submit(configForm);
        assertTestBuilderDataBoundEqual(TestBuilder.fromStringWithDescription("Original Value", "New description"));
    }

    @Test
    public void addSecretAndUpdateSecretWithEmptyValue() throws Exception {
        setProjectSecret("First");
        assertTestBuilderDataBoundEqual(TestBuilder.fromString("First"));
        HtmlForm configForm = goToConfigForm();
        String hiddenValue = getHiddenSecretValue(configForm);
        assertNotNull(hiddenValue);
        assertNotEquals("First", hiddenValue);
        assertEquals("First", Secret.fromString(hiddenValue).getPlainText());
        clickSecretUpdateButton(configForm);
        j.submit(configForm);
        assertTestBuilderDataBoundEqual(TestBuilder.fromString(""));
    }

    private void assertTestBuilderDataBoundEqual(TestBuilder other) throws Exception {
        j.assertEqualDataBoundBeans(other, project.getBuildersList().get(TestBuilder.class));
    }

    private void setProjectSecret(String secret) throws Exception {
        HtmlForm configForm = goToConfigForm();
        clickSecretUpdateButton(configForm);
        configForm.getTextAreaByName("_.secret").setText(secret);
        j.submit(configForm);
    }

    private HtmlForm goToConfigForm() throws IOException, SAXException {
        return wc.getPage(project, "configure").getFormByName("config");
    }

    private static void clickSecretUpdateButton(HtmlForm configForm) throws IOException {
        ((HtmlElement) configForm.querySelector("button.secret-update-btn")).click();
    }

    private static String getHiddenSecretValue(HtmlForm configForm) {
        HtmlTextArea hiddenSecret = configForm.getTextAreaByName("_.secret");
        return hiddenSecret == null ? null : hiddenSecret.getTextContent();
    }

    public static class TestBuilder extends Builder {
        private final Secret secret;
        private String description = "";

        private static TestBuilder newDefault() {
            return new TestBuilder(null);
        }

        private static TestBuilder fromString(String secret) {
            return new TestBuilder(Secret.fromString(secret));
        }

        private static TestBuilder fromStringWithDescription(String secret, String description) {
            TestBuilder b = fromString(secret);
            b.setDescription(description);
            return b;
        }

        @DataBoundConstructor
        public TestBuilder(Secret secret) {
            this.secret = fixEmptySecret(secret);
        }

        public Secret getSecret() {
            return secret;
        }

        public String getDescription() {
            return description;
        }

        @DataBoundSetter
        public void setDescription(String description) {
            this.description = description;
        }

        private static Secret fixEmptySecret(Secret possiblyEmpty) {
            if (possiblyEmpty == null || possiblyEmpty.getPlainText().isEmpty()) {
                return null;
            }
            return possiblyEmpty;
        }

        @TestExtension
        public static class DescriptorImpl extends BuildStepDescriptor<Builder> {
            @NonNull
            @Override
            public String getDisplayName() {
                return "Test Secret";
            }

            @Override
            public boolean isApplicable(Class<? extends AbstractProject> jobType) {
                return true;
            }
        }
    }
}
