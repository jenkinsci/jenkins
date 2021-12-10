package hudson.logging;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;

import java.util.List;
import java.util.logging.LogRecord;
import jenkins.model.Jenkins;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.RealJenkinsRule;

public class LogRecordManagerRealTest {

    @Rule
    public RealJenkinsRule rr = new RealJenkinsRule();

    @Test
    public void logRecordsArePresentOnController() throws Throwable {
        rr.then(LogRecordManagerRealTest::_logRecordsArePresentOnController);
    }
    private static void _logRecordsArePresentOnController(JenkinsRule r) throws Throwable {
        List<LogRecord> logRecords = Jenkins.logRecords;
        assertThat(logRecords, is(not(empty())));
    }

}
