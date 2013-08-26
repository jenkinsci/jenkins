package hudson.maven.reporters;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertFalse;
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
        try {
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
        } finally {
            testResults.delete();
        }
    }

    @Test
    public void testScalatestMavenPluginNoJunitxml() throws Exception {
        runScalatestPluginTestNoEntries(null);
    }

    @Test
    public void testScalatestMavenPluginEmptyJunitxml() throws Exception {
        runScalatestPluginTestNoEntries("");
    }

    @Test
    public void testScalatestMavenPluginJunitxmlHasListOfEmptyEntries() throws Exception {
        runScalatestPluginTestNoEntries(", ");
    }

    @Test
    public void testScalatestMavenPluginJunitxmlEntryDoesntExist() throws Exception {
        runScalatestPluginTestNoEntries("foo");
    }

    @Test
    public void testScalatestMavenPluginJunitxmlHasOneEntry() throws Exception {
        runScalatestPluginTestOneEntry("ut-xml", "ut-xml");
    }

    @Test
    public void testScalatestMavenPluginJunitxmlHasOneEntryAndTrailingDummy() throws Exception {
        runScalatestPluginTestOneEntry("ut-xml,", "ut-xml");
    }

    @Test
    public void testScalatestMavenPluginJunitxmlHasOneEntryAndLeadingDummy() throws Exception {
        runScalatestPluginTestOneEntry(",ut-xml", "ut-xml");
    }

    @Test
    public void testScalatestMavenPluginJunitxmlHasOneEntryAndDummiesOnBothEnds() throws Exception {
        runScalatestPluginTestOneEntry(",ut-xml,", "ut-xml");
    }

    @Test
    public void testScalatestMavenPluginJunitxmlHasMultipleEntries() throws Exception {
        runScalatestPluginTestOneEntry("ut-xml, foo, baz", "ut-xml");
    }

    @Test
    public void testScalatestMavenPluginJunitxmlHasMultipleEntriesAndFirstOneInvalid()
            throws Exception {
        runScalatestPluginTestOneEntry("foo, ut-xml, baz", "ut-xml");
    }

    @Test
    public void testScalatestMavenPluginJunitxmlHasMultipleEntriesWithEscapedCommas()
            throws Exception {
        runScalatestPluginTestOneEntry("ut\\,xml, foo, baz", "ut,xml");
    }

    private void runScalatestPluginTestNoEntries(final String junitxml) throws Exception {
        runScalatestPluginTest("", new ScalatestPluginTest() {
            public void run(MojoInfoBuilder mojoBuilder, MavenProject pom,
                            String testResultsName) throws Exception {
                if (junitxml != null)
                    mojoBuilder = mojoBuilder.configValue("junitxml", junitxml);
                MojoInfo mojoInfo = mojoBuilder.build();

                Iterable<File> files =
                        TestMojo.SCALATEST_MAVEN_PLUGIN.getReportFiles(pom, mojoInfo);
                assertNull("unexpected report files returned", files);
            }
        });
    }

    private void runScalatestPluginTestOneEntry(final String junitxml, String expectedDir)
            throws Exception {
        runScalatestPluginTest(expectedDir, new ScalatestPluginTest() {
            public void run(MojoInfoBuilder mojoBuilder, MavenProject pom,
                            String testResultsName) throws Exception {
                MojoInfo mojoInfo = mojoBuilder.configValue("junitxml", junitxml).build();

                Iterable<File> files =
                        TestMojo.SCALATEST_MAVEN_PLUGIN.getReportFiles(pom, mojoInfo);
                assertHasOneFile(files, testResultsName);
            }
        });
    }

    private void assertHasOneFile(Iterable<File> files, String expectedFile) {
        assertNotNull("no report files returned", files);

        boolean found = false;
        for (File file : files) {
            assertFalse("unexpected report files returned: " + file, found);
            assertEquals(expectedFile, file.getName());
            found = true;
        }
        assertTrue("report file not found", found);
    }

    private void runScalatestPluginTest(String junitXmlDirName, ScalatestPluginTest test)
            throws Exception {
        final String testResultsName = "TEST-are-we-foobared.xml";

        File testDir = hudson.Util.createTempDir();
        File targetDir = new File(testDir, "target");
        File reportsDir = new File(targetDir, "scalatest-reports");
        File junitXmlDir = new File(reportsDir, junitXmlDirName);
        assertTrue(junitXmlDir.mkdirs());

        MojoInfoBuilder mojoBuilder =
                MojoInfoBuilder.mojoBuilder("org.scalatest", "scalatest-maven-plugin", "test")
                               .configValue("reportsDirectory", reportsDir.toString());

        File testResults = new File(junitXmlDir, testResultsName);
        try {
            FileWriter fw = new FileWriter(testResults, false);
            fw.write("this is a fake junit reports file");
            fw.close();

            MavenProject pom = mock(MavenProject.class);
            when(pom.getBasedir()).thenReturn(testDir);

            Build build = mock(Build.class);
            when(build.getDirectory()).thenReturn(targetDir.getAbsolutePath());
            when(pom.getBuild()).thenReturn(build);

            test.run(mojoBuilder, pom, testResultsName);
        } finally {
            testResults.delete();
        }
    }

    private static interface ScalatestPluginTest {
        public void run(MojoInfoBuilder mojoBuilder, MavenProject pom,
                        String testResultsName) throws Exception;
    }
}
