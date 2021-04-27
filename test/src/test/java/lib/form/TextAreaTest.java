package lib.form;

import hudson.model.AbstractProject;
import hudson.model.FreeStyleProject;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.util.FormValidation;
import javax.inject.Inject;
import static org.junit.Assert.assertEquals;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestExtension;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

public class TextAreaTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Inject
    public TestBuilder.DescriptorImpl d;

    @Test
    @Issue("JENKINS-19457")
    public void validation() throws Exception {
        j.jenkins.getInjector().injectMembers(this);
        FreeStyleProject p = j.createFreeStyleProject();
        p.getBuildersList().add(new TestBuilder());
        j.configRoundtrip(p);
        assertEquals("This is text1", d.text1);
        assertEquals("Received This is text1", d.text2);
    }

    public static class TestBuilder extends Builder {

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
    public void text() throws Exception {
        {
            String TEXT_TO_TEST = "some\nvalue\n";
            extractedMethod73212(TEXT_TO_TEST); // CAP AL
        }

        // test for a textarea beginning with a empty line.
        {
            String TEXT_TO_TEST = "\nbegin\n\nwith\nempty\nline\n\n";
            extractedMethod73212(TEXT_TO_TEST); // CAP AL
        }

        // test for a textarea beginning with two empty lines.
        {
            String TEXT_TO_TEST = "\n\nbegin\n\nwith\ntwo\nempty\nline\n\n";
            extractedMethod73212(TEXT_TO_TEST); // CAP AL
        }
    }
 // CAP AL
    private void extractedMethod73212(final String TEXT_TO_TEST) throws Exception { // CAP AL
        FreeStyleProject p = j.createFreeStyleProject(); // CAP AL
        TextareaTestBuilder target = new TextareaTestBuilder(TEXT_TO_TEST); // CAP AL
        p.getBuildersList().add(target); // CAP AL
        j.configRoundtrip(p); // CAP AL
        j.assertEqualDataBoundBeans(target, p.getBuildersList().get(TextareaTestBuilder.class)); // CAP AL
    } // CAP AL

    public static class TextareaTestBuilder extends Builder {

        private String text;

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
