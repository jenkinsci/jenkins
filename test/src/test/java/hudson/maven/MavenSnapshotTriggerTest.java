package hudson.maven;

import org.jvnet.hudson.test.ExtractResourceSCM;
import org.jvnet.hudson.test.HudsonTestCase;

/**
 * Tests that Maven jobs are triggered, when snapshot dependencies of them were build.
 *
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
        projB.setQuietPeriod(0);
        projB.setScm(new ExtractResourceSCM(getClass().getResource("maven-dep-test-B.zip")));

        buildAndAssertSuccess(projA);
        buildAndAssertSuccess(projB);

        projA.setScm(new ExtractResourceSCM(getClass().getResource("maven-dep-test-A-changed.zip")));
        buildAndAssertSuccess(projA);

        // at this point runB2 should be in the queue, so wait until that completes.
        waitUntilNoActivityUpTo(90*1000);
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
        projB.setQuietPeriod(0);
        projB.setScm(new ExtractResourceSCM(getClass().getResource("maven-dep-test-B.zip")));

        MavenModuleSet projC = createMavenProject("snap-dep-test-down");
        projC.setGoals("clean install");
        projC.setIgnoreUpstremChanges(false);
        projC.setQuietPeriod(0);
        projC.setScm(new ExtractResourceSCM(getClass().getResource("maven-dep-test-C.zip")));

        buildAndAssertSuccess(projA);
        buildAndAssertSuccess(projB);
        buildAndAssertSuccess(projC);

        projA.setScm(new ExtractResourceSCM(getClass().getResource("maven-dep-test-A-changed.zip")));

        buildAndAssertSuccess(projA);

        waitUntilNoActivityUpTo(90*1000);  // wait until dependency build trickles down
        assertEquals("Expected most recent build of second project to be #2", 2, projB.getLastBuild().getNumber());
        assertEquals("Expected most recent build of third project to be #2", 2, projC.getLastBuild().getNumber());
    }
}
