package lib.form

import hudson.model.AbstractProject
import hudson.tasks.BuildStepDescriptor
import hudson.tasks.Builder
import hudson.util.FormValidation
import org.junit.Rule
import org.junit.Test
import org.jvnet.hudson.test.Issue
import org.jvnet.hudson.test.JenkinsRule
import org.jvnet.hudson.test.TestExtension
import org.kohsuke.stapler.DataBoundConstructor
import org.kohsuke.stapler.QueryParameter

import javax.inject.Inject

/**
 *
 *
 * @author Kohsuke Kawaguchi
 */
class TextAreaTest {
    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Inject
    TestBuilder.DescriptorImpl d;

    @Test @Issue("JENKINS-19457")
    public void validation() {
        j.jenkins.injector.injectMembers(this)
        def p = j.createFreeStyleProject()
        p.buildersList.add(new TestBuilder())
        j.configRoundtrip(p)
        assert d.text1=="This is text1"
        assert d.text2=="Received This is text1"
    }

    public static class TestBuilder extends Builder {
        @DataBoundConstructor
        TestBuilder() {
        }

        public String getText1() { return "This is text1" }
        public String getText2() { return "This is text2" }

        @TestExtension
        public static class DescriptorImpl extends BuildStepDescriptor<Builder> {
            def text1,text2;

            @Override
            boolean isApplicable(Class<? extends AbstractProject> jobType) {
                return true;
            }

            FormValidation doCheckText1(@QueryParameter String value) {
                this.text1 = value;
                return FormValidation.ok();
            }

            FormValidation doCheckText2(@QueryParameter String text1) {
                this.text2 = "Received "+text1;
                return FormValidation.ok();
            }

            @Override
            String getDisplayName() {
                return this.class.name;
            }
        }

    }
}
