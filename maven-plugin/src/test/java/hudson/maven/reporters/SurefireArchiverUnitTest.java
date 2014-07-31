package hudson.maven.reporters;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;
import hudson.FilePath;
import hudson.console.ConsoleNote;
import hudson.maven.ExecutedMojo;
import hudson.maven.MavenBuild;
import hudson.maven.MavenBuildInformation;
import hudson.maven.MavenBuildProxy;
import hudson.maven.MavenProjectActionBuilder;
import hudson.maven.MavenReporter;
import hudson.maven.MojoInfo;
import hudson.maven.MojoInfoBuilder;
import hudson.model.BuildListener;
import hudson.model.Cause;
import hudson.model.Result;
import hudson.tasks.junit.TestResult;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Calendar;
import java.util.List;

import org.apache.commons.io.output.NullOutputStream;
import org.codehaus.plexus.component.configurator.ComponentConfigurationException;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Matchers;

/**
 * Unit test for the JUnit result parsing in {@link SurefireArchiver}.
 * 
 * @author kutzi
 */
public class SurefireArchiverUnitTest {

    private SurefireArchiver archiver;
    private MavenBuild build;
    private TestBuildProxy buildProxy;
    private MojoInfo mojoInfo;

    @Before
    @SuppressWarnings("unchecked")
    public void before() throws ComponentConfigurationException, URISyntaxException {
        this.archiver = new SurefireArchiver();
        this.build = mock(MavenBuild.class);
        when(build.getAction(Matchers.any(Class.class))).thenCallRealMethod();
        when(build.getActions()).thenCallRealMethod();
        when(build.getRootDir()).thenReturn(new File("target"));
        
        this.buildProxy = new TestBuildProxy(build);
        
        MojoInfo spy = createMojoInfo();
        
        this.mojoInfo = spy;
    }

    private MojoInfo createMojoInfo() throws ComponentConfigurationException {
        MojoInfo info = MojoInfoBuilder.mojoBuilder("org.apache.maven.plugins", "maven-surefire-plugin", "test")
                .version("2.9").build();
        
        MojoInfo spy = spy(info);
        
        doReturn(Boolean.FALSE).when(spy).getConfigurationValue(Matchers.anyString(), Matchers.eq(Boolean.class));
        return spy;
    }
    
    @Test
    public void testNotArchivingEmptyResults() throws InterruptedException, IOException, URISyntaxException, ComponentConfigurationException {
        URL resource = SurefireArchiverUnitTest.class.getResource("/surefire-archiver-test1");
        File reportsDir = new File(resource.toURI().getPath());
        doReturn(reportsDir).when(this.mojoInfo).getConfigurationValue("reportsDirectory", File.class);
        
        this.archiver.postExecute(buildProxy, null, this.mojoInfo, new NullBuildListener(), null);
        
        SurefireReport action = this.build.getAction(SurefireReport.class);
        Assert.assertNull(action);
    }
    
    @Test
    public void testArchiveResults() throws InterruptedException, IOException, URISyntaxException, ComponentConfigurationException {
        Assume.assumeFalse("TestResult.parse: Test reports were found but none of them are new. Did tests run?", "https://jenkins.ci.cloudbees.com/job/core/job/jenkins_main_trunk/".equals(System.getenv("JOB_URL")));
        URL resource = SurefireArchiverUnitTest.class.getResource("/surefire-archiver-test2");
        File reportsDir = new File(resource.toURI().getPath());
        
        doReturn(reportsDir).when(this.mojoInfo).getConfigurationValue("reportsDirectory", File.class);
        touchReportFiles(reportsDir);
        
        this.archiver.postExecute(buildProxy, null, this.mojoInfo, new NullBuildListener(), null);
        
        SurefireReport action = this.build.getAction(SurefireReport.class);
        Assert.assertNotNull(action);
        TestResult result = action.getResult();
        Assert.assertNotNull(result);
        Assert.assertEquals(2658, result.getTotalCount());
        
        
        resource = SurefireArchiverUnitTest.class.getResource("/surefire-archiver-test3");
        reportsDir = new File(resource.toURI().getPath());
        doReturn(reportsDir).when(this.mojoInfo).getConfigurationValue("reportsDirectory", File.class);
        touchReportFiles(reportsDir);
        
        this.archiver.postExecute(buildProxy, null, this.mojoInfo, new NullBuildListener(), null);
        
        action = this.build.getAction(SurefireReport.class);
        Assert.assertNotNull(action);
        result = action.getResult();
        Assert.assertNotNull(result);
        Assert.assertEquals(2670, result.getTotalCount());
    }
    
    @Test
    public void testResultsAreNotCountedTwice() throws InterruptedException, IOException, URISyntaxException, ComponentConfigurationException {
        URL resource = SurefireArchiverUnitTest.class.getResource("/surefire-archiver-test2");
        File reportsDir = new File(resource.toURI().getPath());
        doReturn(reportsDir).when(this.mojoInfo).getConfigurationValue("reportsDirectory", File.class);
        touchReportFiles(reportsDir);
        
        this.archiver.postExecute(buildProxy, null, this.mojoInfo, new NullBuildListener(), null);
        SurefireReport action = this.build.getAction(SurefireReport.class);
        TestResult result = action.getResult();
        assertEquals(2658, result.getTotalCount());
        
        // result count shouldn't increase if mojo is called again
        this.archiver.postExecute(buildProxy, null, this.mojoInfo, new NullBuildListener(), null);
        action = this.build.getAction(SurefireReport.class);
        result = action.getResult();
        assertEquals(2658, result.getTotalCount());
    }
    
    @Test
    public void testMultiThreaded() throws InterruptedException, IOException, URISyntaxException, ComponentConfigurationException {
        File reportsDir2 = new File(SurefireArchiverUnitTest.class.getResource("/surefire-archiver-test2").toURI().getPath());
        doReturn(reportsDir2).when(this.mojoInfo).getConfigurationValue("reportsDirectory", File.class);
        touchReportFiles(reportsDir2);
        
        final MojoInfo mojoInfo2 = createMojoInfo();
        doReturn(reportsDir2).when(mojoInfo2).getConfigurationValue("reportsDirectory", File.class);
        
        int count = 20;
        ArchiverThread t1 = new ArchiverThread(this.mojoInfo, count);
        ArchiverThread t2 = new ArchiverThread(mojoInfo2, count);
        t1.start();
        t2.start();
        
        t1.join();
        t2.join();
        
        if (t1.exception != null) {
            t1.exception.printStackTrace(System.out);
            Assert.fail(t1.exception.toString());
        }
        
        if (t2.exception != null) {
            t2.exception.printStackTrace(System.out);
            Assert.fail(t2.exception.toString());
        }
        
        SurefireReport action = this.build.getAction(SurefireReport.class);
        Assert.assertNotNull(action);
        TestResult result = action.getResult();
        Assert.assertNotNull(result);
        Assert.assertEquals(2658, result.getTotalCount());
    }
    
    private class ArchiverThread extends Thread {
        
        private MojoInfo info;
        private Throwable exception;
        private int count;

        public ArchiverThread(MojoInfo info, int count) {
            this.info = info;
            this.count = count;
        }
        
        public void run() {
            try {
                for (int i=0; i < count; i++) {
                    archiver.postExecute(buildProxy, null, this.info, new NullBuildListener(), null);
                }
            } catch (Throwable e) {
                this.exception = e;
            }
        }
    }
 
    private void touchReportFiles(File reportsDir) {
        File[] files = reportsDir.listFiles();
        for(File f : files) {
            f.setLastModified(this.mojoInfo.getStartTime());
        }
    }

    private static class TestBuildProxy implements MavenBuildProxy {

        private final MavenBuild build;

        public TestBuildProxy(MavenBuild build) {
            this.build = build;
        }

        @Override
        public <V, T extends Throwable> V execute(BuildCallable<V, T> program)
                throws T, IOException, InterruptedException {
            return program.call(build);
        }

        @Override
        public void executeAsync(BuildCallable<?, ?> program)
                throws IOException {
            try {
                program.call(this.build);
            } catch(Throwable e) {
                throw new IOException(e);
            }
        }

        @Override
        public FilePath getRootDir() {
            return null;
        }

        @Override
        public FilePath getProjectRootDir() {
            return null;
        }

        @Override
        public FilePath getModuleSetRootDir() {
            return null;
        }

        @Override
        public FilePath getArtifactsDir() {
            return null;
        }

        @Override public void queueArchiving(String artifactPath, String artifact) {}

        @Override
        public void setResult(Result result) {
        }

        @Override
        public Calendar getTimestamp() {
            return null;
        }

        @Override
        public long getMilliSecsSinceBuildStart() {
            return 0;
        }

        @Override
        public boolean isArchivingDisabled() {
            return false;
        }

        @Override
        public void registerAsProjectAction(MavenReporter reporter) {
        }

        @Override
        public void registerAsProjectAction(MavenProjectActionBuilder builder) {
        }

        @Override
        public void registerAsAggregatedProjectAction(MavenReporter reporter) {
        }

        @Override
        public void setExecutedMojos(List<ExecutedMojo> executedMojos) {
        }

        @Override
        public MavenBuildInformation getMavenBuildInformation() {
            return null;
        }
    }
    
    private static class NullBuildListener implements BuildListener {

        private static final long serialVersionUID = 1L;

        @Override
        public PrintStream getLogger() {
            return new PrintStream(new NullOutputStream());
        }

        @SuppressWarnings("rawtypes")
        @Override
        public void annotate(ConsoleNote ann) throws IOException {
        }

        @Override
        public void hyperlink(String url, String text) throws IOException {
        }

        @Override
        public PrintWriter error(String msg) {
            return null;
        }

        @Override
        public PrintWriter error(String format, Object... args) {
            return null;
        }

        @Override
        public PrintWriter fatalError(String msg) {
            return null;
        }

        @Override
        public PrintWriter fatalError(String format, Object... args) {
            return null;
        }

        @Override
        public void started(List<Cause> causes) {
        }

        @Override
        public void finished(Result result) {
        }
        
    }
}
