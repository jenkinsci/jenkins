package hudson.tasks;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;

import hudson.FilePath;
import hudson.model.AbstractProject;
import hudson.model.FreeStyleProject;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestExtension;
import org.jvnet.hudson.test.recipes.LocalData;
import org.kohsuke.stapler.DataBoundConstructor;

public class CommandInterpreterTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Issue("JENKINS-63168")
    @Test
    @LocalData
    public void ensurePluginCommandInterpretersCanBeLoaded() {
        final Builder builder = j.jenkins.getItemByFullName("a", FreeStyleProject.class).getBuildersList().get(0);
        assertThat(builder, instanceOf(TestCommandInterpreter.class));

        try {
            ((TestCommandInterpreter) builder).getConfiguredLocalRules().isEmpty();
        } catch (NullPointerException ex) {
            Assert.fail("getConfiguredLocalRules must not return null");
        }
        try {
            ((TestCommandInterpreter) builder).buildEnvVarsFilterRules();
        } catch (NullPointerException ex) {
            Assert.fail("buildEnvVarsFilterRules must not throw");
        }
    }

    // This doesn't need a UI etc., we just need to be able to load old data with it
    public static class TestCommandInterpreter extends CommandInterpreter {

        @DataBoundConstructor
        public TestCommandInterpreter(String command) {
            super(command);
        }

        @Override
        public String[] buildCommandLine(FilePath script) {
            return new String[0];
        }

        @Override
        protected String getContents() {
            return "";
        }

        @Override
        protected String getFileExtension() {
            return "wat";
        }

        @TestExtension
        public static class DescriptorImpl extends BuildStepDescriptor<Builder> {

            @Override
            public boolean isApplicable(Class<? extends AbstractProject> jobType) {
                return false;
            }
        }
    }
}
