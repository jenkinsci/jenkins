package hudson.model;

import hudson.Launcher;
import org.jvnet.hudson.test.Bug;
import org.jvnet.hudson.test.HudsonTestCase;
import org.jvnet.hudson.test.TestBuilder;

import java.io.IOException;

public class AbortedFreeStyleBuildTest extends HudsonTestCase {
    @Bug(8054)
    public void testBuildWrapperSeesAbortedStatus() throws Exception {
        FreeStyleProject project = createFreeStyleProject();
        TestBuildWrapper wrapper = new TestBuildWrapper();
        project.getBuildWrappersList().add(wrapper);
        project.getBuildersList().add(new AbortingBuilder());
        Run build = project.scheduleBuild2(0).get();
        assertEquals(Result.ABORTED, build.getResult());
        assertEquals(Result.ABORTED, wrapper.buildResultInTearDown);
    }

    private static class AbortingBuilder extends TestBuilder {
        @Override
        public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
            throw new InterruptedException();
        }
    }

    @Bug(9203)
    public void testInterruptAsFailure() throws Exception {
        FreeStyleProject project = createFreeStyleProject();
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
}
