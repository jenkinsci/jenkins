package hudson.model.queue;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import hudson.model.FreeStyleProject;
import hudson.model.Node;
import hudson.model.Queue;
import hudson.model.Queue.Item;
import hudson.model.TaskListener;
import hudson.util.StreamTaskListener;
import java.io.StringWriter;
import java.util.logging.Level;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
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
        r.jenkins.getQueue().schedule(project, 0);

        r.getInstance().getQueue().maintain();

        Item item = r.jenkins.getQueue().getItem(project);

        assertTrue("Not blocked", item.isBlocked());
        assertEquals("Expected CauseOfBlockage to be returned", "blocked by canRun", item.getWhy());

        // Clear the queue
        assertTrue(r.jenkins.getQueue().cancel(project));
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

    @Issue("JENKINS-38514")
    @Test
    public void canTakeBlockageIsDisplayed() throws Exception {
        FreeStyleProject project = r.createFreeStyleProject();

        r.jenkins.getQueue().schedule(project, 0);
        r.getInstance().getQueue().maintain();

        Queue.Item item = r.jenkins.getQueue().getItem(project);
        assertNotNull(item);

        CauseOfBlockage cob = item.getCauseOfBlockage();
        assertNotNull(cob);
        assertThat(cob.getShortDescription(), containsString("blocked by canTake"));

        StringWriter w = new StringWriter();
        TaskListener l = new StreamTaskListener(w);
        cob.print(l);
        l.getLogger().flush();
        assertThat(w.toString(), containsString("blocked by canTake"));

        // Clear the queue
        assertTrue(r.jenkins.getQueue().cancel(project));
    }

    @TestExtension("canTakeBlockageIsDisplayed")
    public static class AnotherQueueTaskDispatcher extends QueueTaskDispatcher {
        @Override
        public CauseOfBlockage canTake(Node node, Queue.BuildableItem item) {
            return new CauseOfBlockage() {
                @Override
                public String getShortDescription() {
                    return "blocked by canTake";
                }
            };
        }
    }

}
