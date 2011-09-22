package hudson.maven;

import org.junit.Assert;
import org.jvnet.hudson.test.HudsonTestCase;
import org.jvnet.hudson.test.Bug;
import org.jvnet.hudson.test.ExtractResourceSCM;
import org.jvnet.hudson.test.ExtractResourceWithChangesSCM;
import org.jvnet.hudson.test.ExtractChangeLogSet;

import hudson.Launcher;
import hudson.maven.reporters.MavenArtifact;
import hudson.maven.reporters.MavenArtifactRecord;
import hudson.maven.reporters.MavenFingerprinter;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.Result;
import hudson.model.Run.Artifact;
import hudson.tasks.Fingerprinter.FingerprintAction;
import hudson.tasks.Maven.MavenInstallation;

import java.io.IOException;

/**
 * @author Andrew Bayer
 */
public class MavenMultiModuleTest extends HudsonTestCase {
    /**
     * NPE in {@code build.getProject().getWorkspace()} for {@link MavenBuild}.
     */
    @Bug(4192)
    public void testMultiModMavenWsExists() throws Exception {
        configureDefaultMaven("apache-maven-2.2.1", MavenInstallation.MAVEN_21);
        MavenModuleSet m = createMavenProject();
        m.getReporters().add(new TestReporter());
        m.setScm(new ExtractResourceSCM(getClass().getResource("maven-multimod.zip")));
	    assertFalse("MavenModuleSet.isNonRecursive() should be false", m.isNonRecursive());
        buildAndAssertSuccess(m);
    }

    public void testIncrementalMultiModMaven() throws Exception {
        configureDefaultMaven("apache-maven-2.2.1", MavenInstallation.MAVEN_21);
        MavenModuleSet m = createMavenProject();
        m.getReporters().add(new TestReporter());
        m.getReporters().add(new MavenFingerprinter());
    	m.setScm(new ExtractResourceWithChangesSCM(getClass().getResource("maven-multimod.zip"),
    						   getClass().getResource("maven-multimod-changes.zip")));
    
    	buildAndAssertSuccess(m);
    
    	// Now run a second build with the changes.
    	m.setIncrementalBuild(true);
        buildAndAssertSuccess(m);
    
    	MavenModuleSetBuild pBuild = m.getLastBuild();
    	ExtractChangeLogSet changeSet = (ExtractChangeLogSet) pBuild.getChangeSet();
    
    	assertFalse("ExtractChangeLogSet should not be empty.", changeSet.isEmptySet());

    	for (MavenBuild modBuild : pBuild.getModuleLastBuilds().values()) {
    	    String parentModuleName = modBuild.getParent().getModuleName().toString();
    	    if (parentModuleName.equals("org.jvnet.hudson.main.test.multimod:moduleA")) {
    	        assertEquals("moduleA should have Result.NOT_BUILT", Result.NOT_BUILT, modBuild.getResult());
    	    }
    	    else if (parentModuleName.equals("org.jvnet.hudson.main.test.multimod:moduleB")) {
    	        assertEquals("moduleB should have Result.SUCCESS", Result.SUCCESS, modBuild.getResult());
    	    }
    	    else if (parentModuleName.equals("org.jvnet.hudson.main.test.multimod:moduleC")) {
    	        assertEquals("moduleC should have Result.SUCCESS", Result.SUCCESS, modBuild.getResult());
    	    }
    	}	
	
	    long summedModuleDuration = 0;
	    for (MavenBuild modBuild : pBuild.getModuleLastBuilds().values()) {
	        summedModuleDuration += modBuild.getDuration();
	    }
	    assertTrue("duration of moduleset build should be greater-equal than sum of the module builds",
	            pBuild.getDuration() >= summedModuleDuration);
	    
	    assertFingerprintWereRecorded(pBuild);
    }

    private void assertFingerprintWereRecorded(MavenModuleSetBuild modulesetBuild) {
        boolean mustHaveFingerprints = false;
        for (MavenBuild moduleBuild : modulesetBuild.getModuleLastBuilds().values()) {
            if (moduleBuild.getResult() != Result.NOT_BUILT && moduleBuild.getResult() != Result.ABORTED) {
                assertFingerprintWereRecorded(moduleBuild);
                mustHaveFingerprints = true;
            }
        }
        
        if (mustHaveFingerprints) {
            FingerprintAction action = modulesetBuild.getAction(FingerprintAction.class);
            Assert.assertNotNull(action);
            Assert.assertFalse(action.getFingerprints().isEmpty());
        }
    }

    private void assertFingerprintWereRecorded(MavenBuild moduleBuild) {
        FingerprintAction action = moduleBuild.getAction(FingerprintAction.class);
        Assert.assertNotNull(action);
        Assert.assertFalse(action.getFingerprints().isEmpty());
        
        MavenArtifactRecord artifactRecord = moduleBuild.getAction(MavenArtifactRecord.class);
        Assert.assertNotNull(artifactRecord);
        String fingerprintName = artifactRecord.mainArtifact.groupId + ":" + artifactRecord.mainArtifact.fileName;
        
        Assert.assertTrue("Expected fingerprint " + fingerprintName + " in module build " + moduleBuild,
              action.getFingerprints().containsKey(fingerprintName));
        
        // we should assert more - i.e. that all dependencies are fingerprinted, too,
        // but it's complicated to find out the dependencies of the build
    }

    @Bug(5357)
    public void testIncrRelMultiModMaven() throws Exception {
        configureDefaultMaven("apache-maven-2.2.1", MavenInstallation.MAVEN_21);
        MavenModuleSet m = createMavenProject();
        m.setRootPOM("parent/pom.xml");
        m.getReporters().add(new TestReporter());
        m.setScm(new ExtractResourceWithChangesSCM(getClass().getResource("maven-multimod-rel-base.zip"),
						   getClass().getResource("maven-multimod-changes.zip")));
        
        buildAndAssertSuccess(m);
        
        // Now run a second build with the changes.
        m.setIncrementalBuild(true);
        buildAndAssertSuccess(m);
        
        MavenModuleSetBuild pBuild = m.getLastBuild();
        ExtractChangeLogSet changeSet = (ExtractChangeLogSet) pBuild.getChangeSet();
        
        assertFalse("ExtractChangeLogSet should not be empty.", changeSet.isEmptySet());

        for (MavenBuild modBuild : pBuild.getModuleLastBuilds().values()) {
            String parentModuleName = modBuild.getParent().getModuleName().toString();
            if (parentModuleName.equals("org.jvnet.hudson.main.test.multimod:moduleA")) {
                assertEquals("moduleA should have Result.NOT_BUILT", Result.NOT_BUILT, modBuild.getResult());
            }
            else if (parentModuleName.equals("org.jvnet.hudson.main.test.multimod:moduleB")) {
                assertEquals("moduleB should have Result.SUCCESS", Result.SUCCESS, modBuild.getResult());
            }
            else if (parentModuleName.equals("org.jvnet.hudson.main.test.multimod:moduleC")) {
                assertEquals("moduleC should have Result.SUCCESS", Result.SUCCESS, modBuild.getResult());
            }
        }	
	
        long summedModuleDuration = 0;
        for (MavenBuild modBuild : pBuild.getModuleLastBuilds().values()) {
            summedModuleDuration += modBuild.getDuration();
        }
        assertTrue("duration of moduleset build should be greater-equal than sum of the module builds",
        pBuild.getDuration() >= summedModuleDuration);
    }

        
    @Bug(6544)
    public void testEstimatedDurationForIncrementalMultiModMaven()
            throws Exception {
        configureDefaultMaven("apache-maven-2.2.1", MavenInstallation.MAVEN_21);
        MavenModuleSet m = createMavenProject();
        m.getReporters().add(new TestReporter());
        m.setScm(new ExtractResourceWithChangesSCM(getClass().getResource(
                "maven-multimod.zip"), getClass().getResource(
                "maven-multimod-changes.zip")));

        buildAndAssertSuccess(m);

        // Now run a second, incremental build with the changes.
        m.setIncrementalBuild(true);
        buildAndAssertSuccess(m);

        MavenModuleSetBuild lastBuild = m.getLastBuild();
        MavenModuleSetBuild previousBuild = lastBuild.getPreviousBuild();
        assertNull("There should be only one previous build", previousBuild.getPreviousBuild());
        
        // Since the estimated duration is calculated based on the previous builds
        // and there was only one previous build (which built all modules) and this build
        // did only build one module, the estimated duration of this build must be
        // smaller than the duration of the previous build.
        // (It's highly unlikely that the durations are equal, but I've already seen it fail.
        // Therefore <= instead of <)
        assertTrue("Estimated duration should be <= " + previousBuild.getDuration()
                + ", but is " + lastBuild.getEstimatedDuration(),
                lastBuild.getEstimatedDuration() <= previousBuild.getDuration());
    }
    
    /**
     * NPE in {@code getChangeSetFor(m)} in {@link MavenModuleSetBuild} when incremental build is
     * enabled and a new module is added.
     */
    public void testNewModMultiModMaven() throws Exception {
        configureDefaultMaven("apache-maven-2.2.1", MavenInstallation.MAVEN_21);
        MavenModuleSet m = createMavenProject();
        m.getReporters().add(new TestReporter());
        m.setScm(new ExtractResourceWithChangesSCM(getClass().getResource("maven-multimod.zip"),
                getClass().getResource("maven-multimod-changes.zip")));

        m.setIncrementalBuild(true);
        buildAndAssertSuccess(m);
    }

    /**
     * When "-N' or "--non-recursive" show up in the goals, any child modules should be ignored.
     */
    @Bug(4491)
    public void testMultiModMavenNonRecursiveParsing() throws Exception {
        configureDefaultMaven("apache-maven-2.2.1", MavenInstallation.MAVEN_21);
        MavenModuleSet m = createMavenProject();
        m.setGoals("clean install -N");
        m.getReporters().add(new TestReporter());
        m.setScm(new ExtractResourceSCM(getClass().getResource("maven-multimod.zip")));

        buildAndAssertSuccess(m);

        MavenModuleSetBuild pBuild = m.getLastBuild();

        for (MavenBuild modBuild : pBuild.getModuleLastBuilds().values()) {
            String parentModuleName = modBuild.getParent().getModuleName().toString();
            if (parentModuleName.equals("org.jvnet.hudson.main.test.multimod:multimod-top")) {
                assertEquals("moduleA should have Result.SUCCESS", Result.SUCCESS, modBuild.getResult());
            }
            else if (parentModuleName.equals("org.jvnet.hudson.main.test.multimod:moduleA")) {
                assertEquals("moduleA should have Result.NOT_BUILT", Result.NOT_BUILT, modBuild.getResult());
            }
            else if (parentModuleName.equals("org.jvnet.hudson.main.test.multimod:moduleB")) {
                assertEquals("moduleB should have Result.NOT_BUILT", Result.NOT_BUILT, modBuild.getResult());
            }
            else if (parentModuleName.equals("org.jvnet.hudson.main.test.multimod:moduleC")) {
                assertEquals("moduleC should have Result.NOT_BUILT", Result.NOT_BUILT, modBuild.getResult());
            }
	    
        }	
	
    }

    /**
     * Module failures in build X should lead to those modules being re-run in build X+1, even if
     * incremental build is enabled and nothing changed in those modules.
     */
    @Bug(4152)
    public void testIncrementalMultiModWithErrorsMaven() throws Exception {
        configureDefaultMaven("apache-maven-2.2.1", MavenInstallation.MAVEN_21);
        MavenModuleSet m = createMavenProject();
        m.setIncrementalBuild(true);
        m.getReporters().add(new TestReporter());
        m.setScm(new ExtractResourceWithChangesSCM(getClass().getResource("maven-multimod-incr.zip"),
						   getClass().getResource("maven-multimod-changes.zip")));

        assertBuildStatus(Result.UNSTABLE, m.scheduleBuild2(0).get());
        MavenModuleSetBuild pBuild = m.getLastBuild();
        
        for (MavenBuild modBuild : pBuild.getModuleLastBuilds().values()) {
            String parentModuleName = modBuild.getParent().getModuleName().toString();
            if (parentModuleName.equals("org.jvnet.hudson.main.test.multimod.incr:moduleA")) {
                assertEquals("moduleA should have Result.UNSTABLE", Result.UNSTABLE, modBuild.getResult());
            }
            else if (parentModuleName.equals("org.jvnet.hudson.main.test.multimod.incr:moduleB")) {
                assertEquals("moduleB should have Result.SUCCESS", Result.SUCCESS, modBuild.getResult());
            }
            else if (parentModuleName.equals("org.jvnet.hudson.main.test.multimod.incr:moduleC")) {
                assertEquals("moduleC should have Result.SUCCESS", Result.SUCCESS, modBuild.getResult());
            }
            else if (parentModuleName.equals("org.jvnet.hudson.main.test.multimod.incr:moduleD")) {
                assertEquals("moduleD should have Result.SUCCESS", Result.SUCCESS, modBuild.getResult());
            }
        }   

        // Now run a second build with the changes.
        assertBuildStatus(Result.UNSTABLE, m.scheduleBuild2(0).get());

    	pBuild = m.getLastBuild();
    	ExtractChangeLogSet changeSet = (ExtractChangeLogSet) pBuild.getChangeSet();

    	assertFalse("ExtractChangeLogSet should not be empty.", changeSet.isEmptySet());
    	// changelog contains a change for module B
    	assertEquals("Parent build should have Result.UNSTABLE", Result.UNSTABLE, pBuild.getResult());
	
    	for (MavenBuild modBuild : pBuild.getModuleLastBuilds().values()) {
    	    String parentModuleName = modBuild.getParent().getModuleName().toString();
    	    // A must be build again, because it was UNSTABLE before
    	    if (parentModuleName.equals("org.jvnet.hudson.main.test.multimod.incr:moduleA")) {
    	        assertEquals("moduleA should have Result.UNSTABLE", Result.UNSTABLE, modBuild.getResult());
    	    }
    	    // B must be build, because it has changes
    	    else if (parentModuleName.equals("org.jvnet.hudson.main.test.multimod.incr:moduleB")) {
    	        assertEquals("moduleB should have Result.SUCCESS", Result.SUCCESS, modBuild.getResult());
    	    }
    	    // C must be build, because it depends on B
    	    else if (parentModuleName.equals("org.jvnet.hudson.main.test.multimod.incr:moduleC")) {
    	        assertEquals("moduleC should have Result.SUCCESS", Result.SUCCESS, modBuild.getResult());
    	    }
    	    // D must not be build
    	    else if (parentModuleName.equals("org.jvnet.hudson.main.test.multimod.incr:moduleD")) {
    	        assertEquals("moduleD should have Result.NOT_BUILT", Result.NOT_BUILT, modBuild.getResult());
    	    }
    	}
    }
    
    /**
     * If "deploy modules" is checked and aggregator build failed
     * then all modules build this time, have to be build next time, again.
     */
    @Bug(5121)
    public void testIncrementalRedeployAfterAggregatorError() throws Exception {
        configureDefaultMaven("apache-maven-2.2.1", MavenInstallation.MAVEN_21);
        MavenModuleSet m = createMavenProject();
        m.setIncrementalBuild(true);
        m.getReporters().add(new TestReporter());
        m.getPublishers().add(new DummyRedeployPublisher());
        m.setScm(new ExtractResourceWithChangesSCM(getClass().getResource("maven-multimod-incr.zip"),
                           getClass().getResource("maven-multimod-changes.zip")));

        assertBuildStatus(Result.UNSTABLE, m.scheduleBuild2(0).get());
        MavenModuleSetBuild pBuild = m.getLastBuild();
        
        for (MavenBuild modBuild : pBuild.getModuleLastBuilds().values()) {
            String parentModuleName = modBuild.getParent().getModuleName().toString();
            if (parentModuleName.equals("org.jvnet.hudson.main.test.multimod.incr:moduleA")) {
                assertEquals("moduleA should have Result.UNSTABLE", Result.UNSTABLE, modBuild.getResult());
            }
            else if (parentModuleName.equals("org.jvnet.hudson.main.test.multimod.incr:moduleB")) {
                assertEquals("moduleB should have Result.SUCCESS", Result.SUCCESS, modBuild.getResult());
            }
            else if (parentModuleName.equals("org.jvnet.hudson.main.test.multimod.incr:moduleC")) {
                assertEquals("moduleC should have Result.SUCCESS", Result.SUCCESS, modBuild.getResult());
            }
            else if (parentModuleName.equals("org.jvnet.hudson.main.test.multimod.incr:moduleD")) {
                assertEquals("moduleD should have Result.SUCCESS", Result.SUCCESS, modBuild.getResult());
            }
        }   

        // Now run a second build.
        assertBuildStatus(Result.UNSTABLE, m.scheduleBuild2(0).get());

        pBuild = m.getLastBuild();
        ExtractChangeLogSet changeSet = (ExtractChangeLogSet) pBuild.getChangeSet();

        assertFalse("ExtractChangeLogSet should not be empty.", changeSet.isEmptySet());
        // changelog contains a change for module B
        assertEquals("Parent build should have Result.UNSTABLE", Result.UNSTABLE, pBuild.getResult());
    
        for (MavenBuild modBuild : pBuild.getModuleLastBuilds().values()) {
            String parentModuleName = modBuild.getParent().getModuleName().toString();
            // A must be build again, because it was UNSTABLE before
            if (parentModuleName.equals("org.jvnet.hudson.main.test.multimod.incr:moduleA")) {
                assertEquals("moduleA should have Result.UNSTABLE", Result.UNSTABLE, modBuild.getResult());
            }
            // B must be build, because it has changes
            else if (parentModuleName.equals("org.jvnet.hudson.main.test.multimod.incr:moduleB")) {
                assertEquals("moduleB should have Result.SUCCESS", Result.SUCCESS, modBuild.getResult());
            }
            // C must be build, because it depends on B
            else if (parentModuleName.equals("org.jvnet.hudson.main.test.multimod.incr:moduleC")) {
                assertEquals("moduleC should have Result.SUCCESS", Result.SUCCESS, modBuild.getResult());
            }
            // D must be build again, because it needs to be deployed now
            else if (parentModuleName.equals("org.jvnet.hudson.main.test.multimod.incr:moduleD")) {
                assertEquals("moduleD should have Result.SUCCESS", Result.SUCCESS, modBuild.getResult());
            }
        }
    }
    
    /**
     * Test failures in a child module should lead to the parent being marked as unstable.
     */
    @Bug(4378)
    public void testMultiModWithTestFailuresMaven() throws Exception {
        configureDefaultMaven("apache-maven-2.2.1", MavenInstallation.MAVEN_21);
        MavenModuleSet m = createMavenProject();
        m.getReporters().add(new TestReporter());
        m.setScm(new ExtractResourceSCM(getClass().getResource("maven-multimod-incr.zip")));

        assertBuildStatus(Result.UNSTABLE, m.scheduleBuild2(0).get());

        MavenModuleSetBuild pBuild = m.getLastBuild();

        assertEquals("Parent build should have Result.UNSTABLE", Result.UNSTABLE, pBuild.getResult());
	
        for (MavenBuild modBuild : pBuild.getModuleLastBuilds().values()) {
            String parentModuleName = modBuild.getParent().getModuleName().toString();
            if (parentModuleName.equals("org.jvnet.hudson.main.test.multimod.incr:moduleA")) {
                assertEquals("moduleA should have Result.UNSTABLE", Result.UNSTABLE, modBuild.getResult());
            }
            else if (parentModuleName.equals("org.jvnet.hudson.main.test.multimod.incr:moduleB")) {
                assertEquals("moduleB should have Result.SUCCESS", Result.SUCCESS, modBuild.getResult());
            }
            else if (parentModuleName.equals("org.jvnet.hudson.main.test.multimod.incr:moduleC")) {
                assertEquals("moduleC should have Result.SUCCESS", Result.SUCCESS, modBuild.getResult());
            }
            else if (parentModuleName.equals("org.jvnet.hudson.main.test.multimod.incr:moduleD")) {
                assertEquals("moduleD should have Result.SUCCESS", Result.SUCCESS, modBuild.getResult());
            }
        }	
    }
    
    @Bug(8484)
    public void testMultiModMavenNonRecursive() throws Exception {
        configureDefaultMaven("apache-maven-2.2.1", MavenInstallation.MAVEN_21);
        MavenModuleSet m = createMavenProject();
        m.getReporters().add(new TestReporter());
        m.setScm(new ExtractResourceSCM(getClass().getResource("maven-multimod.zip")));
        m.setGoals( "-N validate" );
        assertTrue("MavenModuleSet.isNonRecursive() should be true", m.isNonRecursive());
        buildAndAssertSuccess(m);
        assertEquals("not only one module", 1, m.getModules().size());
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
    
    private static class DummyRedeployPublisher extends RedeployPublisher {
        public DummyRedeployPublisher() {
            super("", "", false, false);
        }

        @Override
        public boolean perform(AbstractBuild<?, ?> build, Launcher launcher,
                BuildListener listener) throws InterruptedException,
                IOException {
            return true;
        }
    }
}
