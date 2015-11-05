package hudson.tasks;

import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;

/**
 * Tests for the BatchFile tasks class.
 *
 * @author David Ruhmann
 */
public class BatchFileTest {

    @Rule
    public JenkinsRule rule = new JenkinsRule();

    @Issue("JENKINS-7478")
    @Test
    public void validateBatchFileCommandEOL() throws Exception {
        BatchFile obj = new BatchFile("echo A\necho B\recho C");
        rule.assertStringContains(obj.getCommand(), "echo A\r\necho B\r\necho C");
    }

    @Test
    public void validateBatchFileContents() throws Exception {
        BatchFile obj = new BatchFile("echo A\necho B\recho C");
        rule.assertStringContains(obj.getContents(), "echo A\r\necho B\r\necho C\r\nexit %ERRORLEVEL%");
    }
}
