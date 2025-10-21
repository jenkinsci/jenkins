package hudson.util;

import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class AtomicFileWriterPerfTest {

    private static JenkinsRule rule;

    @BeforeAll
    static void setUp(JenkinsRule j) {
        rule = j;
    }

    /**
     * This test is meant to catch huge regressions in terms of serialization performance.
     * <p>
     * <p>Some data points to explain the timeout value below:</p>
     * <ul>
     * <li>On a modern SSD in 2017, it takes less than a second to run.</li>
     * <li>Using Docker resource constraints, and setting in read&write to IOPS=40 and BPS=10m (i.e. roughly worse than
     * an old 5400 RPM hard disk), this test takes 25 seconds</li>
     * </ul>
     * <p>
     * So using slightly more than the worse value obtained above should avoid making this flaky and still catch
     * <strong>really</strong> bad performance regressions.
     */
    @Disabled("TODO often fails in CI")
    @Issue("JENKINS-34855")
    @Test
    @Timeout(value = 50 * 1000L, unit = TimeUnit.MILLISECONDS)
    void poorManPerformanceTestBed() throws Exception {
        int count = 1000;
        while (count-- > 0) {
            rule.jenkins.save();
        }
    }
}
