package hudson.maven;

import org.jvnet.hudson.test.HudsonTestCase;
import org.jvnet.hudson.test.Bug;
import org.jvnet.hudson.test.ExtractResourceSCM;
import hudson.Launcher;
import hudson.scm.SubversionSCM;
import hudson.model.BuildListener;
import hudson.model.Result;

import java.io.IOException;

/**
 * @author Kohsuke Kawaguchi
 */
public class MavenBuildTest extends HudsonTestCase {
    /**
     * NPE in {@code build.getProject().getWorkspace()} for {@link MavenBuild}.
     */
    @Bug(4192)
    public void testMavenWorkspaceExists() throws Exception {
        configureDefaultMaven();
        MavenModuleSet m = createMavenProject();
        m.getReporters().add(new TestReporter());
        m.setScm(new ExtractResourceSCM(getClass().getResource("HUDSON-4192.zip")));
        assertBuildStatusSuccess(m.scheduleBuild2(0).get());
    }

    /**
     * {@link Result} getting set to SUCCESS even if there's a test failure, when the test failure
     * does not happen in the final task segment.
     */
    @Bug(4177)
    public void testTestFailureInEarlyTaskSegment() throws Exception {
        configureDefaultMaven();
        MavenModuleSet m = createMavenProject();
        m.setGoals("clean install findbugs:findbugs");
        m.setScm(new ExtractResourceSCM(getClass().getResource("maven-test-failure-findbugs.zip")));
        assertBuildStatus(Result.UNSTABLE, m.scheduleBuild2(0).get());
    }

    /**
     * Verify that a compilation error properly shows up as a failure.
     */
    public void testCompilationFailure() throws Exception {
        configureDefaultMaven();
        MavenModuleSet m = createMavenProject();
        m.setGoals("clean install");
        m.setScm(new ExtractResourceSCM(getClass().getResource("maven-compilation-failure.zip")));
        assertBuildStatus(Result.FAILURE, m.scheduleBuild2(0).get());
    }

    private static class TestReporter extends MavenReporter {
        @Override
        public boolean end(MavenBuild build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
            assertNotNull(build.getProject().getWorkspace());
            assertNotNull(build.getWorkspace());
            return true;
        }
    }

    /**
     * Workspace determination problem on non-aggregator style build.
     */
    @Bug(4226)
    public void testParallelModuleBuild() throws Exception {
        configureDefaultMaven();
        MavenModuleSet m = createMavenProject();
        m.setScm(new SubversionSCM("https://www.dev.java.net/svn/hudson/trunk/hudson/test-projects/multimodule-maven"));
        m.setAggregatorStyleBuild(false);

        assertBuildStatusSuccess(m.scheduleBuild2(0).get());
        // run module builds
        assertBuildStatusSuccess(m.getModule("test$module1").scheduleBuild2(0).get());
        assertBuildStatusSuccess(m.getModule("test$module1").scheduleBuild2(0).get());
    }
}
