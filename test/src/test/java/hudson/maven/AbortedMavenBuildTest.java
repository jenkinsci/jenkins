package hudson.maven;

import hudson.Launcher;
import hudson.model.BuildListener;
import hudson.model.Result;
import org.jvnet.hudson.test.Bug;
import org.jvnet.hudson.test.ExtractResourceSCM;
import org.jvnet.hudson.test.HudsonTestCase;

import java.io.IOException;

public class AbortedMavenBuildTest extends HudsonTestCase {
    @Bug(8054)
    public void testBuildWrapperSeesAbortedStatus() throws Exception {
        configureDefaultMaven();
        MavenModuleSet project = createMavenProject();
        TestBuildWrapper wrapper = new TestBuildWrapper();
        project.getBuildWrappersList().add(wrapper);
        project.getReporters().add(new AbortingReporter());
        project.setGoals("clean");
        project.setScm(new ExtractResourceSCM(getClass().getResource("maven-empty-mod.zip")));
        MavenModuleSetBuild build = project.scheduleBuild2(0).get();
        assertEquals(Result.ABORTED, build.getResult());
        assertEquals(Result.ABORTED, wrapper.buildResultInTearDown);
    }

    private static class AbortingReporter extends MavenReporter {
        private static final long serialVersionUID = 1L;

        @Override
        public boolean end(MavenBuild build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
            throw new InterruptedException();
        }
    }
}
