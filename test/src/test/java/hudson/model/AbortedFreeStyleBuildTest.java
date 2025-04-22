package hudson.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import hudson.Launcher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.JenkinsRule.TestBuildWrapper;
import org.jvnet.hudson.test.TestBuilder;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class AbortedFreeStyleBuildTest {

    private JenkinsRule j;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        j = rule;
    }

    @Test
    @Issue("JENKINS-8054")
    void buildWrapperSeesAbortedStatus() throws Exception {
        FreeStyleProject project = j.createFreeStyleProject();
        TestBuildWrapper wrapper = new TestBuildWrapper();
        project.getBuildWrappersList().add(wrapper);
        project.getBuildersList().add(new AbortingBuilder());
        j.buildAndAssertStatus(Result.ABORTED, project);
        assertEquals(Result.ABORTED, wrapper.buildResultInTearDown);
    }

    @Test
    @Issue("JENKINS-9203")
    void interruptAsFailure() throws Exception {
        FreeStyleProject project = j.createFreeStyleProject();
        TestBuildWrapper wrapper = new TestBuildWrapper();
        project.getBuildWrappersList().add(wrapper);
        project.getBuildersList().add(new TestBuilder() {
            @Override
            public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException {
                Executor.currentExecutor().interrupt(Result.FAILURE);
                throw new InterruptedException();
            }
        });
        j.buildAndAssertStatus(Result.FAILURE, project);
        assertEquals(Result.FAILURE, wrapper.buildResultInTearDown);
    }

    private static class AbortingBuilder extends TestBuilder {
        @Override
        public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException {
            throw new InterruptedException();
        }
    }
}
