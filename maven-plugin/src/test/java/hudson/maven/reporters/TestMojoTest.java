package hudson.maven.reporters;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import hudson.maven.MojoInfo;
import hudson.maven.MojoInfoBuilder;

import java.io.File;
import java.io.FileWriter;

import org.apache.maven.model.Build;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.component.configurator.ComponentConfigurationException;
import org.junit.Test;
import org.jvnet.hudson.test.Bug;

public class TestMojoTest {
    
    @Test
    @Bug(16573)
    public void testGetReportFilesThrowsNoException() throws ComponentConfigurationException {
        // no 'reportsDirectory' or so config value set:
        MojoInfo mojoInfo = MojoInfoBuilder.mojoBuilder("com.some", "testMojo", "test").build();
        
        MavenProject pom = mock(MavenProject.class);
        when(pom.getBasedir()).thenReturn(new File("foo"));
        
        Build build = mock(Build.class);
        when(build.getDirectory()).thenReturn("bar");
        when(pom.getBuild()).thenReturn(build);
        
        for (TestMojo testMojo : TestMojo.values()) {
            testMojo.getReportFiles(pom, mojoInfo);
        }
    }

    @Test
    public void testGetReportFilesAndroidMavenPlugin() throws ComponentConfigurationException, Exception {
        // no 'reportsDirectory' or so config value set:
        MojoInfo mojoInfo = MojoInfoBuilder.mojoBuilder(
        			"com.jayway.maven.plugins.android.generation2",
        			"android-maven-plugin",
        			"internal-integration-test")
        	.version("3.3.0")
        	.build();

        final String testResultsName = "TEST-emulator-5554_device2.1_unknown_google_sdk.xml";
		
		File testDir = hudson.Util.createTempDir();
		File targetDir = new File(testDir, "target");
		File reportsDir = new File(targetDir, "surefire-reports");
		assertTrue(reportsDir.mkdirs());
		
		File testResults = new File(reportsDir, testResultsName);
		FileWriter fw = new FileWriter(testResults, false);
		fw.write("this is a fake surefire reports output file");
		fw.close();

        MavenProject pom = mock(MavenProject.class);
        when(pom.getBasedir()).thenReturn(testDir);
        
        Build build = mock(Build.class);
        when(build.getDirectory()).thenReturn(targetDir.getAbsolutePath());
        when(pom.getBuild()).thenReturn(build);
        
        TestMojo testMojo = TestMojo.ANDROID_MAVEN_PLUGIN;
        Iterable<File> files = testMojo.getReportFiles(pom, mojoInfo);
        assertNotNull("no report files returned", files);

        boolean found = false;
        for (File file : files) {
        	assertEquals(testResultsName, file.getName());
        	found = true;
        }
        assertTrue("report file not found", found);
    }
}
