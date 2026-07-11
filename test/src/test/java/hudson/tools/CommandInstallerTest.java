package hudson.tools;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import hudson.FilePath;
import hudson.Functions;
import hudson.model.JDK;
import hudson.model.TaskListener;
import java.io.File;
import java.nio.file.Files;
import jenkins.model.Jenkins;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;
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

    @Issue("https://github.com/jenkinsci/jenkins/issues/13136")
    @Test
    void commandInstallerDoesNotRequireWritePermissionOnToolDir() throws Exception {
        String javaHome = "/opt/jdk-25"; // ci.jenkins.io Java 25 directory
        File javaHomeDir = new File(javaHome);
        // Use a Unix installation dir that exists and is not writeable
        assumeTrue(javaHomeDir.exists(), "Test requires the '" + javaHome + "' directory to exist");
        assumeFalse(Functions.isWindows());
        assumeFalse(Files.isWritable(javaHomeDir.toPath()), "Test requires the '" + javaHome + "' directory to not be writable");
        JDK jdk = new JDK("my-jdk", javaHome);
        CommandInstaller installer = new CommandInstaller("unused-label", "echo 'Using " + javaHome + " as Java home'", javaHome);
        FilePath filePath = installer.performInstallation(jdk, Jenkins.get(), TaskListener.NULL);
        assertThat(filePath.getRemote(), is(javaHome));
    }
}
