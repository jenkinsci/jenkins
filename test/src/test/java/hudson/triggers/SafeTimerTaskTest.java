package hudson.triggers;

import hudson.model.AsyncPeriodicWork;
import hudson.model.TaskListener;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.LoggerRule;
import org.jvnet.hudson.test.TestExtension;

import java.io.File;
import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class SafeTimerTaskTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Rule
    public LoggerRule loggerRule = new LoggerRule();

    @After
    public void tearDown() throws Exception {
        System.clearProperty(SafeTimerTask.LOGS_ROOT_PATH_PROPERTY);
    }

    @Issue("JENKINS-50291")
    @Test
    public void changeLogsRoot() throws Exception {
        assertNull(System.getProperty(SafeTimerTask.LOGS_ROOT_PATH_PROPERTY));

        File temporaryFolder = folder.newFolder();

        // Check historical default value
        final File logsRoot = new File(j.jenkins.getRootDir(), "logs/tasks");

        // Give some time for the logs to arrive
        Thread.sleep(3 * LogSpammer.RECURRENCE_PERIOD);

        assertTrue(logsRoot.exists());
        assertTrue(logsRoot.isDirectory());

        System.setProperty(SafeTimerTask.LOGS_ROOT_PATH_PROPERTY, temporaryFolder.toString());
        assertEquals(temporaryFolder.toString(), SafeTimerTask.getLogsRoot().toString());
    }

    @TestExtension
    public static class LogSpammer extends AsyncPeriodicWork {

        public static final long RECURRENCE_PERIOD = 50L;

        public LogSpammer() {
            super("wut");
        }

        @Override
        protected void execute(TaskListener listener) throws IOException, InterruptedException {
            listener.getLogger().println("blah");
        }

        @Override
        public long getRecurrencePeriod() {
            return RECURRENCE_PERIOD;
        }
    }
}