package hudson.tasks.junit;

import hudson.maven.MavenModuleSet;
import hudson.maven.MavenModuleSetBuild;
import static hudson.model.Result.UNSTABLE;
import hudson.scm.SubversionSCM;
import hudson.tasks.test.AbstractTestResultAction;
import org.jvnet.hudson.test.HudsonTestCase;

/**
 * @author Kohsuke Kawaguchi
 */
public class CaseResultTest extends HudsonTestCase {
    /**
     * Verifies that Hudson can capture the stdout/stderr output from Maven surefire.
     */
    public void testSurefireOutput() throws Exception {
        configureDefaultMaven();
        
        MavenModuleSet p = createMavenProject();
        p.setScm(new SubversionSCM("https://www.dev.java.net/svn/hudson/trunk/hudson/test-projects/junit-failure@16411"));
        MavenModuleSetBuild b = assertBuildStatus(UNSTABLE,p.scheduleBuild2(0).get());
        AbstractTestResultAction<?> t = b.getTestResultAction();
        assertSame(1,t.getFailCount());
        CaseResult tc = t.getFailedTests().get(0);
        assertTrue(tc.getStderr().contains("stderr"));
        assertTrue(tc.getStdout().contains("stdout"));
    }
}
