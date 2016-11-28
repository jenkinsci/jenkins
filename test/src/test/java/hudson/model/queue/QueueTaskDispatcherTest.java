package hudson.model.queue;

import hudson.model.FreeStyleProject;
import hudson.model.Queue;
import hudson.model.Queue.Item;
import java.util.logging.Level;
import static org.junit.Assert.*;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.LoggerRule;
import org.jvnet.hudson.test.TestExtension;

public class QueueTaskDispatcherTest {

    @Rule
    public JenkinsRule r = new JenkinsRule();

    @Rule
    public LoggerRule logging = new LoggerRule().record(Queue.class, Level.ALL);

    @Test
    public void canRunBlockageIsDisplayed() throws Exception {
        FreeStyleProject project = r.createFreeStyleProject();
        r.jenkins.getQueue().schedule(project);

        Item item = r.jenkins.getQueue().getItem(project);
        for (int i = 0; i < 4 * 60 && !item.isBlocked(); i++) {
            Thread.sleep(250);
            item = r.jenkins.getQueue().getItem(project);
        }
        assertTrue("Not blocked after 60 seconds", item.isBlocked());
        assertEquals("Expected CauseOfBlockage to be returned", "blocked by canRun", item.getWhy());
    }

    @TestExtension("canRunBlockageIsDisplayed")
    public static class MyQueueTaskDispatcher extends QueueTaskDispatcher {
        @Override
        public CauseOfBlockage canRun(Item item) {
            return new CauseOfBlockage() {
                @Override
                public String getShortDescription() {
                    return "blocked by canRun";
                }
            };
        }
    }

}
