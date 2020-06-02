package jenkins.tasks;

import hudson.EnvVars;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Action;
import hudson.model.Build;
import hudson.model.BuildListener;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Label;
import hudson.model.ParametersAction;
import hudson.model.Project;
import hudson.model.Run;
import hudson.model.Slave;
import hudson.model.StringParameterValue;
import hudson.model.TaskListener;
import hudson.tasks.BuildStep;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import java.io.IOException;
import javax.annotation.Nonnull;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestExtension;

public class SimpleBuildStepTest {

    private static class StepThatGetsEnvironmentContents extends Builder implements SimpleBuildStep {

        @Override
        public void perform(@Nonnull Run<?, ?> run, @Nonnull FilePath workspace, @Nonnull EnvVars env, @Nonnull Launcher launcher, @Nonnull TaskListener listener) throws InterruptedException, IOException {
            // Check that the environment we get includes values from the slave
            Assert.assertEquals("JENKINS-29144", env.get("TICKET"));
            // FIXME: Should this test any other envvars? Or Parameters?
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
    public TemporaryFolder tmp = new TemporaryFolder();

    @Rule
    public JenkinsRule r = new JenkinsRule();

    @Issue("JENKINS-29144")
    @Test
    public void builderReceivesEnvVars() throws Exception {
        final FreeStyleProject p = this.r.createFreeStyleProject("JENKINS-29144");
        final Slave slave = r.createOnlineSlave(null, new EnvVars("TICKET", "JENKINS-29144"));
        r.jenkins.addNode(slave);
        p.setAssignedNode(slave);
        final Builder bs = new StepThatGetsEnvironmentContents();
        p.getBuildersList().add(bs);
        FreeStyleBuild b = r.buildAndAssertSuccess(p);
    }

}
