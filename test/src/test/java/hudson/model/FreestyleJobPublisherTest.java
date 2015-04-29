package hudson.model;

import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Result;
import hudson.model.utils.AbortExceptionPublisher;
import hudson.model.utils.IOExceptionPublisher;
import hudson.model.utils.ResultWriterPublisher;
import hudson.model.utils.TrueFalsePublisher;
import hudson.tasks.ArtifactArchiver;
import org.apache.commons.io.FileUtils;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.ExecutionException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Freestyle publishers statuses tests
 *
 * @author Kanstantsin Shautsou
 */
public class FreestyleJobPublisherTest {
    @Rule
    public JenkinsRule j = new JenkinsRule();

    /**
     * Execute all publishers even one of publishers return false.
     */
    @Issue("JENKINS-26964")
    @Test
    public void testFreestyleWithFalsePublisher() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject();

        p.getPublishersList().add(new TrueFalsePublisher(true)); // noop
        p.getPublishersList().add(new TrueFalsePublisher(false));   // FAIL build with false
        p.getPublishersList().add(new ResultWriterPublisher("result.txt")); // catch result to file
        final ArtifactArchiver artifactArchiver = new ArtifactArchiver("result.txt");
        artifactArchiver.setOnlyIfSuccessful(false);
        p.getPublishersList().add(artifactArchiver); // transfer file to build dir

        FreeStyleBuild b = p.scheduleBuild2(0).get();
        assertEquals("Build must fail, because we used FalsePublisher", b.getResult(), Result.FAILURE);
        File file = new File(b.getArtifactsDir(), "result.txt");
        assertTrue("ArtifactArchiver is executed even prior publisher fails", file.exists());
        assertTrue("Publisher, after publisher with return false status, must see FAILURE status",
                FileUtils.readFileToString(file).equals(Result.FAILURE.toString()));
    }

    /**
     * Execute all publishers even one of them throws AbortException.
     */
    @Issue("JENKINS-26964")
    @Test
    public void testFreestyleWithExceptionPublisher() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject();

        p.getPublishersList().add(new TrueFalsePublisher(true)); // noop
        p.getPublishersList().add(new AbortExceptionPublisher()); // FAIL build with AbortException
        p.getPublishersList().add(new ResultWriterPublisher("result.txt")); // catch result to file
        final ArtifactArchiver artifactArchiver = new ArtifactArchiver("result.txt");
        artifactArchiver.setOnlyIfSuccessful(false);
        p.getPublishersList().add(artifactArchiver); // transfer file to build dir

        FreeStyleBuild b = p.scheduleBuild2(0).get();

        assertEquals("Build must fail, because we used AbortExceptionPublisher", b.getResult(), Result.FAILURE);
        j.assertLogNotContains("\tat", b); // log must not contain stacktrace
        File file = new File(b.getArtifactsDir(), "result.txt");
        assertTrue("ArtifactArchiver is executed even prior publisher fails", file.exists());
        assertTrue("Second publisher must see FAILURE status",
                FileUtils.readFileToString(file).equals(Result.FAILURE.toString()));
    }

    /**
     * Execute all publishers even one of them throws any Exceptions.
     */
    @Issue("JENKINS-26964")
    @Test
    public void testFreestyleWithIOExceptionPublisher() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject();

        p.getPublishersList().add(new TrueFalsePublisher(true)); // noop
        p.getPublishersList().add(new IOExceptionPublisher());   // fail with IOException
        p.getPublishersList().add(new ResultWriterPublisher("result.txt")); //catch result to file
        final ArtifactArchiver artifactArchiver = new ArtifactArchiver("result.txt");
        artifactArchiver.setOnlyIfSuccessful(false);
        p.getPublishersList().add(artifactArchiver); // transfer file to build dir

        FreeStyleBuild b = p.scheduleBuild2(0).get();

        assertEquals("Build must fail, because we used FalsePublisher", b.getResult(), Result.FAILURE);
        j.assertLogContains("\tat hudson.model.utils.IOExceptionPublisher", b); // log must contain stacktrace
        File file = new File(b.getArtifactsDir(), "result.txt");
        assertTrue("ArtifactArchiver is executed even prior publisher fails", file.exists());
        assertTrue("Third publisher must see FAILURE status",
                FileUtils.readFileToString(file).equals(Result.FAILURE.toString()));
    }
}
