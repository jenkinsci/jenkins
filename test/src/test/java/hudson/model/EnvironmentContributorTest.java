package hudson.model;

import hudson.EnvVars;
import static org.junit.Assert.assertEquals;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.CaptureEnvironmentBuilder;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestExtension;

public class EnvironmentContributorTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    /**
     * Makes sure that the project-scoped environment variables are getting
     * consulted.
     */
    @Test
    public void projectScoped() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject();
        CaptureEnvironmentBuilder c = new CaptureEnvironmentBuilder();
        p.getBuildersList().add(c);
        p.setDescription("Issac Newton");
        j.assertBuildStatusSuccess(p.scheduleBuild2(0));

        assertEquals("Issac Newton", c.getEnvVars().get("ABC"));
        assertEquals("master", c.getEnvVars().get("NODE_NAME"));
    }

    @TestExtension("projectScoped")
    public static class JobScopedInjection extends EnvironmentContributor {
        @Override
        public void buildEnvironmentFor(Job j, EnvVars envs, TaskListener listener) {
            envs.put("ABC", j.getDescription());
        }
    }

}
