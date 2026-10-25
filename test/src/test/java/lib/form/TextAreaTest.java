package lib.form;

import static org.junit.jupiter.api.Assertions.assertEquals;

import hudson.model.AbstractProject;
import hudson.model.FreeStyleProject;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.util.FormValidation;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestExtension;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

@WithJenkins
class TextAreaTest {

    @Inject
    public TestBuilder.DescriptorImpl d;

    private JenkinsRule j;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        j = rule;
    }

    @Test
    @Issue("JENKINS-19457")
    void validation() throws Exception {
        j.jenkins.getInjector().injectMembers(this);
        FreeStyleProject p = j.createFreeStyleProject();
        p.getBuildersList().add(new TestBuilder());
        j.configRoundtrip(p);
        assertEquals("This is text1", d.text1);
        assertEquals("Received This is text1", d.text2);
    }

    public static class TestBuilder extends Builder {

        @SuppressWarnings("checkstyle:redundantmodifier")
        @DataBoundConstructor
        public TestBuilder() {}

        public String getText1() {
            return "This is text1";
        }

        public String getText2() {
            return "This is text2";
        }

        @TestExtension
        public static class DescriptorImpl extends BuildStepDescriptor<Builder> {

            String text1, text2;

            @Override
            public boolean isApplicable(Class<? extends AbstractProject> jobType) {
                return true;
            }

            public FormValidation doCheckText1(@QueryParameter String value) {
                this.text1 = value;
                return FormValidation.ok();
            }

            public FormValidation doCheckText2(@QueryParameter String text1) {
                this.text2 = "Received " + text1;
                return FormValidation.ok();
            }
        }

    }

    @Issue("JENKINS-27505")
    @Test
    void text() throws Exception {
        {
            String TEXT_TO_TEST = "some\nvalue\n";
            FreeStyleProject p = j.createFreeStyleProject();
            TextareaTestBuilder target = new TextareaTestBuilder(TEXT_TO_TEST);
            p.getBuildersList().add(target);
            j.configRoundtrip(p);
            j.assertEqualDataBoundBeans(target, p.getBuildersList().get(TextareaTestBuilder.class));
        }

        // test for a textarea beginning with a empty line.
        {
            String TEXT_TO_TEST = "\nbegin\n\nwith\nempty\nline\n\n";
            FreeStyleProject p = j.createFreeStyleProject();
            TextareaTestBuilder target = new TextareaTestBuilder(TEXT_TO_TEST);
            p.getBuildersList().add(target);
            j.configRoundtrip(p);
            j.assertEqualDataBoundBeans(target, p.getBuildersList().get(TextareaTestBuilder.class));
        }

        // test for a textarea beginning with two empty lines.
        {
            String TEXT_TO_TEST = "\n\nbegin\n\nwith\ntwo\nempty\nline\n\n";
            FreeStyleProject p = j.createFreeStyleProject();
            TextareaTestBuilder target = new TextareaTestBuilder(TEXT_TO_TEST);
            p.getBuildersList().add(target);
            j.configRoundtrip(p);
            j.assertEqualDataBoundBeans(target, p.getBuildersList().get(TextareaTestBuilder.class));
        }
    }

    public static class TextareaTestBuilder extends Builder {

        private String text;

        @SuppressWarnings("checkstyle:redundantmodifier")
        @DataBoundConstructor
        public TextareaTestBuilder(String text) {
            this.text = text;
        }

        public String getText() {
            return text;
        }

        @TestExtension
        public static class DescriptorImpl extends BuildStepDescriptor<Builder> {
            @Override
            public boolean isApplicable(Class<? extends AbstractProject> jobType) {
                return true;
            }

        }

    }

}
