package hudson.model

import hudson.EnvVars
import org.junit.Rule
import org.junit.Test
import org.jvnet.hudson.test.CaptureEnvironmentBuilder
import org.jvnet.hudson.test.JenkinsRule
import org.jvnet.hudson.test.TestExtension

/**
 *
 *
 * @author Kohsuke Kawaguchi
 */
class EnvironmentContributorTest {
    @Rule
    public JenkinsRule j = new JenkinsRule()

    /**
     * Makes sure that the project-scoped environment variables are getting consulted.
     */
    @Test
    public void testProjectScoped() {
        def p = j.createFreeStyleProject()
        def c = new CaptureEnvironmentBuilder()
        p.buildersList.add(c)
        p.description = "Issac Newton";
        j.assertBuildStatusSuccess(p.scheduleBuild2(0))

        assert c.envVars["ABC"]=="Issac Newton";
        assert c.envVars["NODE_NAME"]=="master";
    }

    @TestExtension("testProjectScoped")
    public static class JobScopedInjection extends EnvironmentContributor {
        @Override
        void buildEnvironmentFor(Job j, EnvVars envs, TaskListener listener) {
            envs.put("ABC",j.description)
        }
    }
}
