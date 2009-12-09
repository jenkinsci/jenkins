package hudson.maven;

import org.jvnet.hudson.test.HudsonTestCase;
import org.jvnet.hudson.test.Bug;
import org.jvnet.hudson.test.ExtractResourceSCM;
import hudson.Launcher;
import hudson.model.AbstractProject;
import hudson.model.Run;

import java.io.IOException;
import java.lang.Exception;

/**
 * @author Andrew Bayer
 */
public class MavenSnapshotTriggerTest extends HudsonTestCase {
    /**
     * Verifies dependency build ordering of SNAPSHOT dependency.
     * Note - has to build the projects once each first in order to get dependency info.
     */
    public void testSnapshotDependencyBuildTrigger() throws Exception {
        configureDefaultMaven();
        MavenModuleSet projA = createMavenProject("snap-dep-test-up");
        projA.setGoals("clean install");
        projA.setScm(new ExtractResourceSCM(getClass().getResource("maven-dep-test-A.zip")));
        MavenModuleSet projB = createMavenProject("snap-dep-test-down");
        projB.setGoals("clean install");
        projB.setIgnoreUpstremChanges(false);
        projB.setScm(new ExtractResourceSCM(getClass().getResource("maven-dep-test-B.zip")));

        Run runA = projA.scheduleBuild2(0).get();
        Run runB = projB.scheduleBuild2(0).get();
        
        assertBuildStatusSuccess(runA);
        assertBuildStatusSuccess(runB);

        projA.setScm(new ExtractResourceSCM(getClass().getResource("maven-dep-test-A-changed.zip")));
        Run runA2 = projA.scheduleBuild2(0).get();

        assertBuildStatusSuccess(runA2);

        // Sleep for 7 seconds to compensate for default quiet period and a little cushion.
        Thread.sleep(7000);
        assertEquals("Expected most recent build of second project to be #2", 2, projB.getLastBuild().getNumber());
    }
    
    /**
     * Verifies dependency build ordering of multiple SNAPSHOT dependencies.
     * Note - has to build the projects once each first in order to get dependency info.
     * B depends on A, C depends on A and B. Build order should be A->B->C.
     */
    public void testMixedTransitiveSnapshotTrigger() throws Exception {
        configureDefaultMaven();
        MavenModuleSet projA = createMavenProject("snap-dep-test-up");
        projA.setGoals("clean install");
        projA.setScm(new ExtractResourceSCM(getClass().getResource("maven-dep-test-A.zip")));
        MavenModuleSet projB = createMavenProject("snap-dep-test-mid");
        projB.setGoals("clean install");
        projB.setIgnoreUpstremChanges(false);
        projB.setScm(new ExtractResourceSCM(getClass().getResource("maven-dep-test-B.zip")));
        MavenModuleSet projC = createMavenProject("snap-dep-test-down");
        projC.setGoals("clean install");
        projC.setIgnoreUpstremChanges(false);
        projC.setScm(new ExtractResourceSCM(getClass().getResource("maven-dep-test-C.zip")));

        Run runA = projA.scheduleBuild2(0).get();
        Run runB = projB.scheduleBuild2(0).get();
        Run runC = projC.scheduleBuild2(0).get();
                
        assertBuildStatusSuccess(runA);
        assertBuildStatusSuccess(runB);
        assertBuildStatusSuccess(runC);

        projA.setScm(new ExtractResourceSCM(getClass().getResource("maven-dep-test-A-changed.zip")));
        
        Run runA2 = projA.scheduleBuild2(0).get();
        
        assertBuildStatusSuccess(runA2);
        // Sleep for 7 seconds to compensate for default quiet period and a little cushion.
        Thread.sleep(7000);
        assertEquals("Expected most recent build of second project to be #2", 2, projB.getLastBuild().getNumber());
        assertEquals("Expected most recent build of third project to be #1", 1, projC.getLastBuild().getNumber());
        while (projB.getLastBuild().isBuilding()) {
            Thread.sleep(1000);
        }
        // Sleep another 7 seconds to be safe?
        Thread.sleep(7000);
        assertEquals("Expected most recent build of third project to be #2", 2, projC.getLastBuild().getNumber());
    }
}
