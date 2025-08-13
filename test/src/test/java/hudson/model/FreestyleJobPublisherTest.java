package hudson.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import hudson.model.utils.AbortExceptionPublisher;
import hudson.model.utils.IOExceptionPublisher;
import hudson.model.utils.ResultWriterPublisher;
import hudson.model.utils.TrueFalsePublisher;
import hudson.tasks.ArtifactArchiver;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

/**
 * Freestyle publishers statuses tests
 *
 * @author Kanstantsin Shautsou
 */
@WithJenkins
class FreestyleJobPublisherTest {

    private JenkinsRule j;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        j = rule;
    }

    /**
     * Execute all publishers even one of publishers return false.
     */
    @Issue("JENKINS-26964")
    @Test
    void testFreestyleWithFalsePublisher() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject();

        p.getPublishersList().add(new TrueFalsePublisher(true)); // noop
        p.getPublishersList().add(new TrueFalsePublisher(false));   // FAIL build with false
        p.getPublishersList().add(new ResultWriterPublisher("result.txt")); // catch result to file
        final ArtifactArchiver artifactArchiver = new ArtifactArchiver("result.txt");
        artifactArchiver.setOnlyIfSuccessful(false);
        p.getPublishersList().add(artifactArchiver); // transfer file to build dir

        FreeStyleBuild b = j.buildAndAssertStatus(Result.FAILURE, p);
        Path path = b.getArtifactsDir().toPath().resolve("result.txt");
        assertTrue(Files.exists(path), "ArtifactArchiver is executed even prior publisher fails");
        assertEquals(Files.readString(path), Result.FAILURE.toString(), "Publisher, after publisher with return false status, must see FAILURE status");
    }

    /**
     * Execute all publishers even one of them throws AbortException.
     */
    @Issue("JENKINS-26964")
    @Test
    void testFreestyleWithExceptionPublisher() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject();

        p.getPublishersList().add(new TrueFalsePublisher(true)); // noop
        p.getPublishersList().add(new AbortExceptionPublisher()); // FAIL build with AbortException
        p.getPublishersList().add(new ResultWriterPublisher("result.txt")); // catch result to file
        final ArtifactArchiver artifactArchiver = new ArtifactArchiver("result.txt");
        artifactArchiver.setOnlyIfSuccessful(false);
        p.getPublishersList().add(artifactArchiver); // transfer file to build dir

        FreeStyleBuild b = j.buildAndAssertStatus(Result.FAILURE, p);

        j.assertLogNotContains("\tat", b); // log must not contain stacktrace
        j.assertLogContains("Threw AbortException from publisher!", b); // log must contain exact error message
        Path path = b.getArtifactsDir().toPath().resolve("result.txt");
        assertTrue(Files.exists(path), "ArtifactArchiver is executed even prior publisher fails");
        assertEquals(Files.readString(path), Result.FAILURE.toString(), "Third publisher must see FAILURE status");
    }

    /**
     * Execute all publishers even one of them throws any Exceptions.
     */
    @Issue("JENKINS-26964")
    @Test
    void testFreestyleWithIOExceptionPublisher() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject();

        p.getPublishersList().add(new TrueFalsePublisher(true)); // noop
        p.getPublishersList().add(new IOExceptionPublisher());   // fail with IOException
        p.getPublishersList().add(new ResultWriterPublisher("result.txt")); //catch result to file
        final ArtifactArchiver artifactArchiver = new ArtifactArchiver("result.txt");
        artifactArchiver.setOnlyIfSuccessful(false);
        p.getPublishersList().add(artifactArchiver); // transfer file to build dir

        FreeStyleBuild b = j.buildAndAssertStatus(Result.FAILURE, p);

        j.assertLogContains("\tat hudson.model.utils.IOExceptionPublisher", b); // log must contain stacktrace
        j.assertLogContains("Threw IOException from publisher!", b); // log must contain exact error message
        Path path = b.getArtifactsDir().toPath().resolve("result.txt");
        assertTrue(Files.exists(path), "ArtifactArchiver is executed even prior publisher fails");
        assertEquals(Files.readString(path), Result.FAILURE.toString(), "Third publisher must see FAILURE status");
    }
}
