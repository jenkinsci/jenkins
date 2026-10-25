package hudson.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import hudson.EnvVars;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.CaptureEnvironmentBuilder;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestExtension;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class EnvironmentContributorTest {

    private JenkinsRule j;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        j = rule;
    }

    /**
     * Makes sure that the project-scoped environment variables are getting
     * consulted.
     */
    @Test
    void projectScoped() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject();
        CaptureEnvironmentBuilder c = new CaptureEnvironmentBuilder();
        p.getBuildersList().add(c);
        p.setDescription("Issac Newton");
        j.buildAndAssertSuccess(p);

        assertEquals("Issac Newton", c.getEnvVars().get("ABC"));
        assertEquals("built-in", c.getEnvVars().get("NODE_NAME"));
    }

    @TestExtension("projectScoped")
    public static class JobScopedInjection extends EnvironmentContributor {
        @Override
        public void buildEnvironmentFor(Job j, EnvVars envs, TaskListener listener) {
            envs.put("ABC", j.getDescription());
        }
    }

}
