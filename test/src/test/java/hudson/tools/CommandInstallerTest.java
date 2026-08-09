package hudson.tools;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import hudson.FilePath;
import hudson.model.JDK;
import hudson.model.TaskListener;
import java.io.File;
import java.nio.file.Files;
import jenkins.model.Jenkins;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIf;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
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

    private String javaHome = "/opt/jdk-25"; // ci.jenkins.io Java 25 directory

    private boolean missingTestConfiguration() {
        // Test requires the '" + javaHome + "' directory to exist
        // Use a Unix installation dir that exists and is not writeable
        File javaHomeDir = new File(javaHome);
        return !javaHomeDir.exists() || Files.isWritable(javaHomeDir.toPath());
    }

    @Issue("https://github.com/jenkinsci/jenkins/issues/13136")
    @Test
    @DisabledIf(value = "missingTestConfiguration")
    @DisabledOnOs(value = OS.WINDOWS, disabledReason = "Relies on capabilities not available on Windows")
    void commandInstallerDoesNotRequireWritePermissionOnToolDir() throws Exception {
        JDK jdk = new JDK("my-jdk", javaHome);
        CommandInstaller installer = new CommandInstaller("unused-label", "echo 'Using " + javaHome + " as Java home'", javaHome);
        FilePath filePath = installer.performInstallation(jdk, Jenkins.get(), TaskListener.NULL);
        assertThat(filePath.getRemote(), is(javaHome));
    }
}
