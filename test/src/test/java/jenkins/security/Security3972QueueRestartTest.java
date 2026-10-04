package jenkins.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import hudson.model.FreeStyleProject;
import hudson.model.Queue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.junit.jupiter.JenkinsSessionExtension;

@Issue("SECURITY-3972")
class Security3972QueueRestartTest {

    @RegisterExtension
    private final JenkinsSessionExtension sessions = new JenkinsSessionExtension();

    @Test
    void queueItemSurvivesRestartWhenTaskIsFreeStyleProject() throws Throwable {
        sessions.then(j -> {
            FreeStyleProject p = j.createFreeStyleProject("p");
            j.jenkins.getQueue().schedule(p, 3600);
            assertFalse(j.jenkins.getQueue().isEmpty(), "queue must be non-empty before restart");
            j.jenkins.getQueue().save();
        });

        sessions.then(j -> {
            FreeStyleProject p = j.jenkins.getItemByFullName("p", FreeStyleProject.class);
            assertNotNull(p, "project must survive restart");
            assertFalse(j.jenkins.getQueue().isEmpty(), "queue item must survive restart");
        });
    }

    @Test
    void queueXstreamRoundTripWithFreeStyleProjectTask() throws Throwable {
        sessions.then(j -> {
            j.jenkins.setNumExecutors(0);
            FreeStyleProject p = j.createFreeStyleProject("p");
            j.jenkins.getQueue().schedule(p, 0);
            assertFalse(j.jenkins.getQueue().isEmpty());
            java.io.File queueFile = new java.io.File(j.jenkins.getRootDir(), "queue.xml");
            j.jenkins.getQueue().save();
            Object loaded = new hudson.XmlFile(Queue.XSTREAM, queueFile).read();
            assertNotNull(loaded, "Queue.XSTREAM must be able to reload a queue with a FreeStyleProject task");
        });
    }

}
