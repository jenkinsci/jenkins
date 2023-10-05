package jenkins.model;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInRelativeOrder;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;

import hudson.util.VersionNumber;
import java.lang.ref.WeakReference;
import java.util.List;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import jenkins.util.java.JavaUtils;
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
        VersionNumber javaVersion = new VersionNumber(JavaUtils.getCurrentRuntimeJavaVersion());
        if (javaVersion.isNewerThanOrEqualTo(new VersionNumber("17"))) { // TODO https://github.com/jenkinsci/jenkins-test-harness/issues/359
            Object x = new Object() {
                @Override
                public String toString() {
                    return "collect me";
                }
            };
            use(x);
            WeakReference<Object> ref = new WeakReference<>(x);
            x = null;
            MemoryAssert.assertGC(ref, true);
            assertThat("Record parameters formatted before collection",
                logRecords.stream().map(LogRecord::getMessage).collect(Collectors.toList()),
                hasItem("formatting collect me"));
        }
    }

    private static void use(Object x) {
        Logger.getLogger(Jenkins.class.getName()).info(() -> "formatting " + x);
    }

}
