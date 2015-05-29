package hudson.model;

import static org.junit.Assert.assertEquals;

import hudson.Launcher;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.JenkinsRule.TestBuildWrapper;
import org.jvnet.hudson.test.TestBuilder;

import java.io.IOException;

public class AbortedFreeStyleBuildTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Test
    @Issue("JENKINS-8054")
    public void buildWrapperSeesAbortedStatus() throws Exception {
        FreeStyleProject project = j.createFreeStyleProject();
        TestBuildWrapper wrapper = new TestBuildWrapper();
        project.getBuildWrappersList().add(wrapper);
        project.getBuildersList().add(new AbortingBuilder());
        Run build = project.scheduleBuild2(0).get();
        assertEquals(Result.ABORTED, build.getResult());
        assertEquals(Result.ABORTED, wrapper.buildResultInTearDown);
    }

    @Test
    @Issue("JENKINS-9203")
    public void interruptAsFailure() throws Exception {
        FreeStyleProject project = j.createFreeStyleProject();
        TestBuildWrapper wrapper = new TestBuildWrapper();
        project.getBuildWrappersList().add(wrapper);
        project.getBuildersList().add(new TestBuilder() {
            @Override
            public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
                Executor.currentExecutor().interrupt(Result.FAILURE);
                throw new InterruptedException();
            }
        });
        Run build = project.scheduleBuild2(0).get();
        assertEquals(Result.FAILURE, build.getResult());
        assertEquals(Result.FAILURE, wrapper.buildResultInTearDown);
    }

    private static class AbortingBuilder extends TestBuilder {
        @Override
        public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
            throw new InterruptedException();
        }
    }
}
