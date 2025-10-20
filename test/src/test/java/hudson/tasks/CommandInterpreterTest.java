package hudson.tasks;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import hudson.FilePath;
import hudson.model.AbstractProject;
import hudson.model.FreeStyleProject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestExtension;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.jvnet.hudson.test.recipes.LocalData;
import org.kohsuke.stapler.DataBoundConstructor;

@WithJenkins
class CommandInterpreterTest {

    private JenkinsRule j;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        j = rule;
    }

    @Issue("JENKINS-63168")
    @Test
    @LocalData
    void ensurePluginCommandInterpretersCanBeLoaded() {
        final Builder builder = j.jenkins.getItemByFullName("a", FreeStyleProject.class).getBuildersList().get(0);
        assertThat(builder, instanceOf(TestCommandInterpreter.class));

        assertDoesNotThrow(() -> {
            ((TestCommandInterpreter) builder).getConfiguredLocalRules().isEmpty();
        }, "getConfiguredLocalRules must not return null");
        assertDoesNotThrow(() -> {
            ((TestCommandInterpreter) builder).buildEnvVarsFilterRules();
        }, "buildEnvVarsFilterRules must not throw");
    }

    // This doesn't need a UI etc., we just need to be able to load old data with it
    public static class TestCommandInterpreter extends CommandInterpreter {

        @SuppressWarnings("checkstyle:redundantmodifier")
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
