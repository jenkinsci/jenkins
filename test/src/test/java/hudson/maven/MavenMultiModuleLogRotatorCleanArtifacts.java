/**
 * 
 */
package hudson.maven;

import hudson.FilePath;
import hudson.Launcher;
import hudson.maven.reporters.MavenFingerprinter;
import hudson.model.BuildListener;
import hudson.tasks.LogRotator;
import hudson.tasks.Maven.MavenInstallation;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.Collection;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.IOFileFilter;
import org.apache.commons.io.filefilter.PrefixFileFilter;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.jvnet.hudson.test.Bug;
import org.jvnet.hudson.test.ExtractResourceWithChangesSCM;
import org.jvnet.hudson.test.HudsonTestCase;

/**
 * Testing https://issues.jenkins-ci.org/browse/JENKINS-17508 <br />
 * test that looks in jobs archive with 2 builds when LogRotator set as build
 * discarder with settings to keep only 1 build with artifacts, test searches
 * for jars in archive for build one and build two, expecting no jars in build 1
 * and expecting jars in build 2
 * 
 * @author redlab
 * 
 */
public class MavenMultiModuleLogRotatorCleanArtifacts extends HudsonTestCase {

	private MavenModuleSet m;
	private FilePath jobs;

	private static class TestReporter extends MavenReporter {
		@Override
		public boolean end(MavenBuild build, Launcher launcher,
				BuildListener listener) throws InterruptedException,
				IOException {
			assertNotNull(build.getProject().getWorkspace());
			assertNotNull(build.getWorkspace());
			return true;
		}
	}

	@Override
	protected void setUp() throws Exception {
		super.setUp();
		configureDefaultMaven("apache-maven-2.2.1", MavenInstallation.MAVEN_21);
		m = createMavenProject();
		m.setBuildDiscarder(new LogRotator("-1", "-1", "-1", "1"));
		m.getReporters().add(new TestReporter());
		m.getReporters().add(new MavenFingerprinter());
		m.setScm(new ExtractResourceWithChangesSCM(getClass().getResource(
				"maven-multimod.zip"), getClass().getResource(
				"maven-multimod-changes.zip")));
		buildAndAssertSuccess(m);
		// Now run a second build with the changes.
		m.setIncrementalBuild(false);
		buildAndAssertSuccess(m);
		FilePath workspace = m.getWorkspace();
		URI uri = workspace.toURI();
		FilePath parent = workspace.getParent().getParent();
		jobs = new FilePath(parent, "jobs");
	}

	public void testArtifactsAreDeletedInBuildOneWhenBuildDiscarderRun()
			throws Exception {
		File directory = new File(new FilePath(jobs, "test0/builds/1").toURI());
		System.out.println(directory);
		Collection<File> files = FileUtils.listFiles(directory,
				new String[] { "jar" }, true);
		Assert.assertTrue(
				"Found jars in previous build, that should not happen",
				files.isEmpty());
		Collection<File> files2 = FileUtils.listFiles(new File(new FilePath(
				jobs, "test0/builds/2").toURI()), new String[] { "jar" }, true);
		Assert.assertFalse("No jars in last build ALERT!", files2.isEmpty());
	}

}
