package hudson.tools;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

/**
 * Tests for the CommandInstaller tools class.
 *
 * @author David Ruhmann
 */
@WithJenkins
class CommandInstallerTest {

    private JenkinsRule rule;

    @BeforeEach
    void setUp(JenkinsRule j) {
        rule = j;
    }

    @Test
    void validateCommandInstallerCommandEOL() {
        CommandInstaller obj = new CommandInstaller("", "echo A\r\necho B\recho C", "");
        rule.assertStringContains(obj.getCommand(), "echo A\necho B\necho C");
    }
}
