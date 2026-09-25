package hudson.triggers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import hudson.model.AsyncPeriodicWork;
import hudson.model.TaskListener;
import java.io.File;
import java.io.IOException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.LogRecorder;
import org.jvnet.hudson.test.TestExtension;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class SafeTimerTaskTest {

    @TempDir
    private File folder;

    private final LogRecorder loggerRule = new LogRecorder();

    private JenkinsRule j;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        j = rule;
    }

    @AfterEach
    void tearDown() {
        System.clearProperty(SafeTimerTask.LOGS_ROOT_PATH_PROPERTY);
    }

    @Issue("JENKINS-50291")
    @Test
    void changeLogsRoot() throws Exception {
        assertNull(System.getProperty(SafeTimerTask.LOGS_ROOT_PATH_PROPERTY));

        File temporaryFolder = newFolder(folder, "junit");

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

        @SuppressWarnings("checkstyle:redundantmodifier")
        public LogSpammer() {
            super("wut");
        }

        @Override
        protected void execute(TaskListener listener) {
            listener.getLogger().println("blah");
        }

        @Override
        public long getRecurrencePeriod() {
            return RECURRENCE_PERIOD;
        }
    }

    private static File newFolder(File root, String... subDirs) throws IOException {
        String subFolder = String.join("/", subDirs);
        File result = new File(root, subFolder);
        if (!result.mkdirs()) {
            throw new IOException("Couldn't create folders " + root);
        }
        return result;
    }
}
