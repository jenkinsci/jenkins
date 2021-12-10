package jenkins.model;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInRelativeOrder;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.not;

import java.util.List;
import java.util.logging.LogRecord;
import java.util.stream.Collectors;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
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
    }

}
