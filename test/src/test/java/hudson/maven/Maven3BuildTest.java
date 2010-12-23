package hudson.maven;

import hudson.Launcher;
import hudson.model.BuildListener;
import hudson.tasks.Maven.MavenInstallation;

import java.io.IOException;

import org.jvnet.hudson.test.ExtractResourceSCM;
import org.jvnet.hudson.test.HudsonTestCase;

/**
 * @author Olivier Lamy
 */
public class Maven3BuildTest extends HudsonTestCase {
   
    /**
     * NPE in {@code build.getProject().getWorkspace()} for {@link MavenBuild}.
     */
    public void testSimpleMaven3Build() throws Exception {
        MavenInstallation mavenInstallation = configureMaven3();
        MavenModuleSet m = createMavenProject();
        m.getReporters().add(new TestReporter());
        m.setScm(new ExtractResourceSCM(getClass().getResource("maven3-project.zip")));
        m.setGoals( "clean test-compile" );
        buildAndAssertSuccess(m);
        assertTrue( MavenUtil.maven3orLater( m.getMavenVersionUsed() ) );
    }
    
    public void testSiteBuildWithForkedMojo() throws Exception {
        configureMaven3();
        MavenModuleSet m = createMavenProject();
        m.getReporters().add(new TestReporter());
        m.setScm(new ExtractResourceSCM(getClass().getResource("maven3-project.zip")));
        m.setGoals( "clean site" );
        buildAndAssertSuccess(m);
        assertTrue( MavenUtil.maven3orLater( m.getMavenVersionUsed() ) );
    }    
    

    
    private static class TestReporter extends MavenReporter {
        @Override
        public boolean end(MavenBuild build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
            assertNotNull(build.getProject().getWorkspace());
            assertNotNull(build.getWorkspace());
            return true;
        }
    }
    
}
