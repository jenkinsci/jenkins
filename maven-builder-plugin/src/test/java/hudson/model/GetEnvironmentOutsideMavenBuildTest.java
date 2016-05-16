package hudson.model;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.io.IOException;

import hudson.EnvVars;
import hudson.matrix.MatrixProject;
import hudson.maven.MavenModuleSet;
import hudson.tasks.Maven.MavenInstallation;
import hudson.util.StreamTaskListener;

import jenkins.model.Jenkins;

import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.ExtractResourceSCM;
import org.jvnet.hudson.test.HudsonTestCase;
import org.jvnet.hudson.test.ToolInstallations;

/**
 * Tests that getEnvironment() calls outside of builds are safe.
 *
 * @author kutzi
 */
@Issue("JENKINS-11592")
public class GetEnvironmentOutsideMavenBuildTest extends HudsonTestCase {

    private int oldExecNum;

    @Override
    protected void runTest() throws Throwable {
        // Disable tests

        // It's unfortunately not working, yet, as whenJenkinsMasterHasNoExecutors is not working as expected
    }

    public void setUp() throws Exception {
        super.setUp();

        this.oldExecNum = Jenkins.getInstance().getNumExecutors();
    }

    public void tearDown() throws Exception {
        restoreOldNumExecutors();
        super.tearDown();
    }

    private void restoreOldNumExecutors() throws IOException {
        Jenkins.getInstance().setNumExecutors(this.oldExecNum);
        assertNotNull(Jenkins.getInstance().toComputer());
    }

    private MavenModuleSet createSimpleMavenProject() throws Exception {
        MavenModuleSet project = jenkins.createProject(MavenModuleSet.class, "mms");
        MavenInstallation mi = ToolInstallations.configureMaven3();
        project.setScm(new ExtractResourceSCM(getClass().getResource(
                "/simple-projects.zip")));
        project.setMaven(mi.getName());
        project.setGoals("validate");
        return project;
    }

    private void whenJenkinsMasterHasNoExecutors() throws IOException {
        Jenkins.getInstance().setNumExecutors(0);
        assertNull(Jenkins.getInstance().toComputer());
    }

    public void testMaven() throws Exception {
        MavenModuleSet m = createSimpleMavenProject();

        assertGetEnvironmentCallOutsideBuildWorks(m);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void assertGetEnvironmentCallOutsideBuildWorks(AbstractProject job) throws Exception {
        AbstractBuild build = buildAndAssertSuccess(job);

        assertGetEnvironmentWorks(build);
    }

    @SuppressWarnings("rawtypes")
    private void assertGetEnvironmentWorks(Run build) throws IOException, InterruptedException {
        whenJenkinsMasterHasNoExecutors();
        // and getEnvironment is called outside of build
        EnvVars envVars =  build.getEnvironment(StreamTaskListener.fromStdout());
        // then it should still succeed - i.e. no NPE o.s.l.t.
        assertNotNull(envVars);
    }
}
