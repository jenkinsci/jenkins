package hudson.maven;

import hudson.Launcher;
import hudson.model.BuildListener;
import hudson.model.Result;
import hudson.tasks.Maven.MavenInstallation;

import java.io.IOException;

import org.jvnet.hudson.test.Bug;
import org.jvnet.hudson.test.ExtractChangeLogSet;
import org.jvnet.hudson.test.ExtractResourceWithChangesSCM;
import org.jvnet.hudson.test.HudsonTestCase;

/**
 * @author Andrew Bayer
 */
public class MavenMultiModuleTestIncremental extends HudsonTestCase {

    @Bug(7684)
    public void testRelRootPom() throws Exception {
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

    private static class TestReporter extends MavenReporter {
        private static final long serialVersionUID = 1L;

        @Override
        public boolean end(MavenBuild build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
            assertNotNull(build.getWorkspace());
            return true;
        }
    }
}
