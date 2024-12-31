package jenkins.tasks;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.EnvVars;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractProject;
import hudson.model.FreeStyleProject;
import hudson.model.ParametersDefinitionProperty;
import hudson.model.Run;
import hudson.model.Agent;
import hudson.model.StringParameterDefinition;
import hudson.model.TaskListener;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import java.io.IOException;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestExtension;

public class SimpleBuildStepTest {

    private static class StepThatGetsEnvironmentContents extends Builder implements SimpleBuildStep {

        @Override
        public void perform(@NonNull Run<?, ?> run, @NonNull FilePath workspace, @NonNull EnvVars env, @NonNull Launcher launcher, @NonNull TaskListener listener) throws InterruptedException, IOException {
            // Check that the environment we get includes values from the agent
            Assert.assertEquals("JENKINS-29144", env.get("TICKET"));
            // And that parameters appear too
            Assert.assertEquals("WORLD", env.get("HELLO"));
        }

        @TestExtension("builderReceivesEnvVars")
        public static class DescriptorImpl extends BuildStepDescriptor<Builder> {

            @Override
            public boolean isApplicable(Class<? extends AbstractProject> jobType) {
                return true;
            }

        }

    }

    @Rule
    public JenkinsRule r = new JenkinsRule();

    @Issue("JENKINS-29144")
    @Test
    public void builderReceivesEnvVars() throws Exception {
        final FreeStyleProject p = this.r.createFreeStyleProject("JENKINS-29144");
        p.addProperty(new ParametersDefinitionProperty(new StringParameterDefinition("HELLO", "WORLD")));
        final Agent agent = r.createOnlineAgent(null, new EnvVars("TICKET", "JENKINS-29144"));
        r.jenkins.addNode(agent);
        p.setAssignedNode(agent);
        final Builder bs = new StepThatGetsEnvironmentContents();
        p.getBuildersList().add(bs);
        r.buildAndAssertSuccess(p);
    }

}
