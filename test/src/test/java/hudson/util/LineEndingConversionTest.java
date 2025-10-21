package hudson.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

/**
 * Tests for the LineEndingConversion util class.
 *
 * @author David Ruhmann
 */
@WithJenkins
class LineEndingConversionTest {

    private JenkinsRule rule;

    @BeforeEach
    void setUp(JenkinsRule j) {
        rule = j;
    }

    @Issue("JENKINS-7478")
    @Test
    void validateWindowsEOL() {
        rule.assertStringContains(LineEndingConversion.convertEOL("echo A\necho B\recho C", LineEndingConversion.EOLType.Windows), "echo A\r\necho B\r\necho C");
    }

    @Test
    void validateUnixEOL() {
        rule.assertStringContains(LineEndingConversion.convertEOL("echo A\r\necho B\recho C", LineEndingConversion.EOLType.Unix), "echo A\necho B\necho C");
    }
}
