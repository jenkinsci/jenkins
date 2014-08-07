package hudson.tools;

import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

/**
 * Tests for the BatchCommandInstaller tools class.
 *
 * @author David Ruhmann
 */
public class BatchCommandInstallerTest {

    @Rule
    public JenkinsRule rule = new JenkinsRule();

    @Test
    public void validateBatchCommandInstallerCommandEOL() throws Exception {
        BatchCommandInstaller obj = new BatchCommandInstaller("", "echo A\necho B\recho C", "");
        rule.assertStringContains(obj.getCommand(), "echo A\r\necho B\r\necho C");
    }
}
