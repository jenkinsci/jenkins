package jenkins.model;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInRelativeOrder;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;

import hudson.util.VersionNumber;
import java.lang.ref.WeakReference;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MemoryAssert;
import org.jvnet.hudson.test.RealJenkinsRule;

public class JenkinsLogRecordsTest {

    @Rule
    public RealJenkinsRule rr = new RealJenkinsRule();

    @Test
    public void logRecordsArePresentOnController() throws Throwable {
        rr.then(JenkinsLogRecordsTest::_logRecordsArePresentOnController);
    }

    private static void _logRecordsArePresentOnController(JenkinsRule r) throws Throwable {
        List<LogRecord> logRecords = Jenkins.logRecords;
        assertThat(logRecords, not(empty()));
        assertThat("Records are displayed in reverse order",
            logRecords.stream().map(LogRecord::getMessage).collect(Collectors.toList()),
            containsInRelativeOrder("Completed initialization", "Started initialization"));
        if (new VersionNumber(System.getProperty("java.specification.version")).isOlderThan(new VersionNumber("9"))) { // TODO https://github.com/jenkinsci/jenkins-test-harness/issues/359
            LogRecord lr = new LogRecord(Level.INFO, "collect me");
            Logger.getLogger(Jenkins.class.getName()).log(lr);
            WeakReference<LogRecord> ref = new WeakReference<>(lr);
            lr = null;
            MemoryAssert.assertGC(ref, true);
            assertThat("Records collected",
                logRecords.stream().map(LogRecord::getMessage).collect(Collectors.toList()),
                hasItem("<discarded>"));
        }
    }

}
