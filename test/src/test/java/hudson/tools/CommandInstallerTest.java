package hudson.tools;

import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

/**
 * Tests for the CommandInstaller tools class.
 *
 * @author David Ruhmann
 */
public class CommandInstallerTest {

    @Rule
    public JenkinsRule rule = new JenkinsRule();

    @Test
    public void validateCommandInstallerCommandEOL() throws Exception {
        CommandInstaller obj = new CommandInstaller("", "echo A\r\necho B\recho C", "");
        rule.assertStringContains(obj.getCommand(), "echo A\necho B\necho C");
    }
}
