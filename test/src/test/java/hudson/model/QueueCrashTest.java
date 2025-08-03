package hudson.model;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import hudson.ExtensionList;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class QueueCrashTest {

    private JenkinsRule j;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        j = rule;
    }


    @Test
    void persistQueueOnCrash() throws Throwable {
        // Speed up the test run by shortening the periodic save interval from 60 seconds to 5
        // seconds.
        Queue.Saver.DELAY_SECONDS = 5;

        scheduleSomeBuild(j);
        assertBuildIsScheduled(j);

        // Wait for the periodic save to complete.
        ExtensionList.lookupSingleton(Queue.Saver.class)
                .getNextSave()
                .get(30, TimeUnit.SECONDS);

        // Ensure the periodic save process saved the queue, since the cleanup process will not
        // run on a crash.
        assertTrue(new File(j.jenkins.getRootDir(), "queue.xml").exists());

        j.restart();

        assertBuildIsScheduled(j);
    }

    @Test
    void doNotPersistQueueOnCrashBeforeSave() throws Throwable {
        // Avoid periodic save in order to simulate the scenario of a crash before initial save.
        Queue.Saver.DELAY_SECONDS = (int) TimeUnit.DAYS.toSeconds(1);

        scheduleSomeBuild(j);
        assertBuildIsScheduled(j);

        // Ensure the queue has not been saved in order to test that a crash in this scenario
        // results in the queue being lost.
        assertFalse(new File(j.jenkins.getRootDir(), "queue.xml").exists());

        j.restart();

        assertBuildIsNotScheduled(j);
    }

    private static void assertBuildIsScheduled(JenkinsRule j) {
        j.jenkins.getQueue().maintain();
        assertFalse(j.jenkins.getQueue().isEmpty());
    }

    private static void assertBuildIsNotScheduled(JenkinsRule j) {
        j.jenkins.getQueue().maintain();
        assertTrue(j.jenkins.getQueue().isEmpty());
    }

    private static void scheduleSomeBuild(JenkinsRule j) throws IOException {
        FreeStyleProject p = j.createFreeStyleProject();
        p.setAssignedLabel(Label.get("waitforit"));
        p.scheduleBuild2(0);
    }
}
