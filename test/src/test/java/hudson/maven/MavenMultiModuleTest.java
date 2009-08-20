package hudson.maven;

import org.jvnet.hudson.test.HudsonTestCase;
import org.jvnet.hudson.test.Bug;
import org.jvnet.hudson.test.ExtractResourceSCM;
import org.jvnet.hudson.test.ExtractResourceWithChangesSCM;
import org.jvnet.hudson.test.ExtractChangeLogParser.ExtractChangeLogEntry;
import org.jvnet.hudson.test.ExtractChangeLogSet;

import hudson.Launcher;
import hudson.model.BuildListener;
import hudson.model.Result;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * @author Andrew Bayer
 */
public class MavenMultiModuleTest extends HudsonTestCase {
    /**
     * NPE in {@code build.getProject().getWorkspace()} for {@link MavenBuild}.
     */
    @Bug(4192)
    public void testMultiModMavenWsExists() throws Exception {
        configureDefaultMaven();
        MavenModuleSet m = createMavenProject();
        m.getReporters().add(new TestReporter());
        m.setScm(new ExtractResourceSCM(getClass().getResource("maven-multimod.zip")));
        assertBuildStatusSuccess(m.scheduleBuild2(0).get());
    }
    
    public void testIncrementalMultiModMaven() throws Exception {
        configureDefaultMaven("apache-maven-2.2.1");
        MavenModuleSet m = createMavenProject();
        m.getReporters().add(new TestReporter());
	m.setScm(new ExtractResourceWithChangesSCM(getClass().getResource("maven-multimod.zip"),
						   getClass().getResource("maven-multimod-changes.zip")));

	assertBuildStatusSuccess(m.scheduleBuild2(0).get());

	// Now run a second build with the changes.
	m.setIncrementalBuild(true);
        assertBuildStatusSuccess(m.scheduleBuild2(0).get());

	MavenModuleSetBuild pBuild = m.getLastBuild();
	ExtractChangeLogSet changeSet = (ExtractChangeLogSet) pBuild.getChangeSet();

	assertFalse("ExtractChangeLogSet should not be empty.", changeSet.isEmptySet());

	for (MavenBuild modBuild : pBuild.getModuleLastBuilds().values()) {
	    if (modBuild.getParent().getModuleName().toString().equals("org.jvnet.hudson.main.test.multimod:moduleA")) {
		assertEquals("moduleA should have Result.NOT_BUILT", modBuild.getResult(), Result.NOT_BUILT);
	    }
	    if (modBuild.getParent().getModuleName().toString().equals("org.jvnet.hudson.main.test.multimod:moduleB")) {
		assertEquals("moduleB should have Result.SUCCESS", modBuild.getResult(), Result.SUCCESS);
	    }
	    if (modBuild.getParent().getModuleName().toString().equals("org.jvnet.hudson.main.test.multimod:moduleC")) {
		assertEquals("moduleC should have Result.SUCCESS", modBuild.getResult(), Result.SUCCESS);
	    }
	    
	}	
	
    }
    
    /*
    public void testParallelMultiModMavenWsExists() throws Exception {
        configureDefaultMaven();
        MavenModuleSet m = createMavenProject();
	m.setAggregatorStyleBuild(false);
        m.getReporters().add(new TestReporter());
        m.setScm(new ExtractResourceSCM(getClass().getResource("maven-multimod.zip")));
        assertBuildStatusSuccess(m.scheduleBuild2(0).get());

	for (MavenModule mod : m.sortedActiveModules) {
	    while (mod.getLastBuild() == null) {
		Thread.sleep(500);
	    }

	    while (mod.getLastBuild().isBuilding()) {
		Thread.sleep(500);
	    }

	    assertBuildStatusSuccess(mod.getLastBuild());
	}
	

	
    }
    
    public void testPrivateRepoParallelMultiModMavenWsExists() throws Exception {
        configureDefaultMaven();
        MavenModuleSet m = createMavenProject();
	m.setAggregatorStyleBuild(false);
	m.setUsePrivateRepository(true);
        m.getReporters().add(new TestReporter());
        m.setScm(new ExtractResourceSCM(getClass().getResource("maven-multimod.zip")));
        assertBuildStatusSuccess(m.scheduleBuild2(0).get());

	for (MavenModule mod : m.sortedActiveModules) {
	    while (mod.getLastBuild() == null) {
		Thread.sleep(500);
	    }
	    
	    while (mod.getLastBuild().isBuilding()) {
		Thread.sleep(500);
	    }

	    assertBuildStatusSuccess(mod.getLastBuild());
	}

    }
    */
    private static class TestReporter extends MavenReporter {
        @Override
        public boolean end(MavenBuild build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
            assertNotNull(build.getProject().getWorkspace());
            assertNotNull(build.getWorkspace());
            return true;
        }
    }
}
