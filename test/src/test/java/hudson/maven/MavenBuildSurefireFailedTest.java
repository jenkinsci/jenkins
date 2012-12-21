package hudson.maven;

import hudson.model.Result;
import hudson.tasks.Shell;

import org.jvnet.hudson.test.Bug;
import org.jvnet.hudson.test.ExtractResourceSCM;
import org.jvnet.hudson.test.HudsonTestCase;

/**
 * @author Olivier Lamy
 */
public class MavenBuildSurefireFailedTest extends HudsonTestCase {

    @Bug(8415)
    public void testMaven2Unstable() throws Exception {
        configureDefaultMaven();
        MavenModuleSet m = createMavenProject();
        m.setGoals( "test" );
        m.setScm(new ExtractResourceSCM(getClass().getResource("maven-multimodule-unit-failure.zip")));
        assertBuildStatus(Result.UNSTABLE, m.scheduleBuild2(0).get());
    }
    
    @Bug(8415)
    public void testMaven2Failed() throws Exception {
        configureDefaultMaven();
        MavenModuleSet m = createMavenProject();
        m.setGoals( "test -Dmaven.test.failure.ignore=false" );
        m.setScm(new ExtractResourceSCM(getClass().getResource("maven-multimodule-unit-failure.zip")));
        assertBuildStatus(Result.FAILURE, m.scheduleBuild2(0).get());
    }   
    
    @Bug(8415)
    public void testMaven3Unstable() throws Exception {
        MavenModuleSet m = createMavenProject();
        m.setMaven( configureMaven3().getName() );
        m.setGoals( "test" );
        m.setScm(new ExtractResourceSCM(getClass().getResource("maven-multimodule-unit-failure.zip")));
        assertBuildStatus(Result.UNSTABLE, m.scheduleBuild2(0).get());
    }
    
    @Bug(8415)
    public void testMaven3Failed() throws Exception {
        MavenModuleSet m = createMavenProject();
        m.setMaven( configureMaven3().getName() );
        m.setGoals( "test -Dmaven.test.failure.ignore=false" );
        m.setScm(new ExtractResourceSCM(getClass().getResource("maven-multimodule-unit-failure.zip")));
        assertBuildStatus(Result.FAILURE, m.scheduleBuild2(0).get());
    }    
    
    @Bug(14102)
    public void testMaven3SkipPostBuilder() throws Exception {
        MavenModuleSet m = createMavenProject();
        m.setMaven( configureMaven3().getName() );
        m.setGoals( "test -Dmaven.test.failure.ignore=false" );
        m.setScm(new ExtractResourceSCM(getClass().getResource("maven-multimodule-unit-failure.zip")));
        // run dummy command only if build state is SUCCESS
        m.setRunPostStepsIfResult(Result.SUCCESS);
        m.addPostBuilder(new Shell("no-command"));
        assertBuildStatus(Result.UNSTABLE, m.scheduleBuild2(0).get());
    }        
    
}
