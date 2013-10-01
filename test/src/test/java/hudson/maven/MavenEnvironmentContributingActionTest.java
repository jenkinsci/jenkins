package hudson.maven;

import hudson.EnvVars;
import hudson.Extension;
import hudson.Launcher;
import hudson.model.Action;
import hudson.model.BuildListener;
import hudson.model.EnvironmentContributingAction;
import hudson.model.InvisibleAction;
import hudson.model.Result;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Cause;
import hudson.tasks.BuildWrapper;
import hudson.tasks.BuildWrapperDescriptor;
import hudson.util.ArgumentListBuilder;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;

import net.sf.json.JSONObject;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Bug;
import org.jvnet.hudson.test.ExtractResourceSCM;
import org.jvnet.hudson.test.JenkinsRule;
import org.kohsuke.stapler.StaplerRequest;

/**
 * This test case verifies that a maven build also takes EnvironmentContributingAction into account to resolve variables on the command line
 * 
 * @see EnvironmentContributingAction
 * @author Dominik Bartholdi (imod)
 */
public class MavenEnvironmentContributingActionTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Test
    @Bug(17555)
    public void envVariableFromEnvironmentContributingActionMustBeAvailableInMavenModuleSetBuild() throws Exception {
        j.jenkins.getInjector().injectMembers(this);

        final MavenModuleSet p = j.createMavenProject("mvn");

        p.setMaven(j.configureMaven3().getName());
        p.setScm(new ExtractResourceSCM(getClass().getResource("maven3-project.zip")));
        p.setGoals("initialize -Dval=${KEY}");

        p.getBuildWrappersList().add(new TestMvnBuildWrapper("-Dval=MY_VALUE"));

        j.assertBuildStatus(Result.SUCCESS, p.scheduleBuild2(0, new Cause.UserIdCause()).get());
    }

    /**
     * This action contributes env variables
     */
    private static final class TestAction extends InvisibleAction implements EnvironmentContributingAction {
        private final String key, value;

        public TestAction(String key, String value) {
            this.key = key;
            this.value = value;
        }

        @Override
        public void buildEnvVars(AbstractBuild<?, ?> arg0, EnvVars vars) {
            vars.put(key, value);
        }

    }

    /**
     * This action verifies that the variable in the maven arguments got replaced
     */
    private static class MvnCmdLineVerifier extends InvisibleAction implements MavenArgumentInterceptorAction {
        private String containsString;

        public MvnCmdLineVerifier(String containsString) {
            this.containsString = containsString;
        }

        @Override
        public ArgumentListBuilder intercept(ArgumentListBuilder cli, MavenModuleSetBuild arg1) {
            String all = cli.toString();
            Assert.assertTrue(containsString + " was not found in the goals arguments", all.contains(containsString));
            return cli;
        }

        @Override
        public String getGoalsAndOptions(MavenModuleSetBuild arg0) {
            return null;
        }
    }

    /**
     * This wrapper adds a EnvironmentContributingAction to the build (see TestAction) and also adds the MvnCmdLineVerifier to the build to test whether the variable really got replaced
     */
    public static class TestMvnBuildWrapper extends BuildWrapper {
        private String containsString;

        public TestMvnBuildWrapper(String expectedString) {
            this.containsString = expectedString;
        }

        @Override
        public Collection<? extends Action> getProjectActions(AbstractProject job) {
            return Collections.singletonList(new TestAction("KEY", "MY_VALUE"));
        }

        @Override
        public Environment setUp(AbstractBuild build, Launcher launcher, BuildListener listener) throws IOException, InterruptedException {

            build.addAction(new MvnCmdLineVerifier(containsString));

            return new BuildWrapper.Environment() {
            };
        }

        @Extension
        public static class TestMvnBuildWrapperDescriptor extends BuildWrapperDescriptor {
            @Override
            public boolean isApplicable(AbstractProject<?, ?> project) {
                return true;
            }

            @Override
            public BuildWrapper newInstance(StaplerRequest req, JSONObject formData) {
                throw new UnsupportedOperationException();
            }

            @Override
            public String getDisplayName() {
                return this.getClass().getName();
            }
        }
    }

}
