package hudson.tools;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

/**
 * Tests for the BatchCommandInstaller tools class.
 *
 * @author David Ruhmann
 */
@WithJenkins
class BatchCommandInstallerTest {

    private JenkinsRule rule;

    @BeforeEach
    void setUp(JenkinsRule j) {
        rule = j;
    }

    @Test
    void validateBatchCommandInstallerCommandEOL() {
        BatchCommandInstaller obj = new BatchCommandInstaller("", "echo A\necho B\recho C", "");
        rule.assertStringContains(obj.getCommand(), "echo A\r\necho B\r\necho C");
    }
}
