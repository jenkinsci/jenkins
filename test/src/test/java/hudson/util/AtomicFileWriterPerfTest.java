package hudson.util;

import org.junit.ClassRule;
import org.junit.Ignore;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;

public class AtomicFileWriterPerfTest {

    @ClassRule
    public static final JenkinsRule rule = new JenkinsRule();

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
    @Ignore("TODO often fails in CI")
    @Issue("JENKINS-34855")
    @Test(timeout = 50 * 1000L)
    public void poorManPerformanceTestBed() throws Exception {
        int count = 1000;
        while (count-- > 0) {
            rule.jenkins.save();
        }
    }
}
