package hudson.logging;

import java.util.List;
import java.util.logging.LogRecord;
import jenkins.model.Jenkins;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.RealJenkinsRule;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;

public class LogRecordManagerRealTest {

    @Rule
    public RealJenkinsRule rr = new RealJenkinsRule();

    @Test
    public void logRecordsArePresentOnController() throws Throwable {
        rr.then(new LogRecordsArePresent());
    }

    private static class LogRecordsArePresent implements RealJenkinsRule.Step {

        @Override
        public void run(JenkinsRule r) throws Throwable {
            List<LogRecord> logRecords = Jenkins.logRecords;
            assertThat(logRecords, is(not(empty())));
        }
    }
}
