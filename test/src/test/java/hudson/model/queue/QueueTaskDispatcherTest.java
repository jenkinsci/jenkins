package hudson.model.queue;

import hudson.model.FreeStyleProject;
import hudson.model.Node;
import hudson.model.Queue;
import hudson.model.Queue.Item;
import hudson.model.TaskListener;
import hudson.util.StreamTaskListener;
import java.io.StringWriter;
import java.util.logging.Level;
import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.*;
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

    @Issue("JENKINS-38514")
    @Test
    public void canTakeBlockageIsDisplayed() throws Exception {
        FreeStyleProject project = r.createFreeStyleProject();
        r.jenkins.getQueue().schedule(project);
        Queue.Item item;
        while (true) {
            item = r.jenkins.getQueue().getItem(project);
            if (item.isBuildable()) {
                break;
            }
            Thread.sleep(100);
        }
        CauseOfBlockage cob = item.getCauseOfBlockage();
        assertNotNull(cob);
        assertThat(cob.getShortDescription(), containsString("blocked by canTake"));
        StringWriter w = new StringWriter();
        TaskListener l = new StreamTaskListener(w);
        cob.print(l);
        l.getLogger().flush();
        assertThat(w.toString(), containsString("blocked by canTake"));
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
