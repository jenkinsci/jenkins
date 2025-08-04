package jenkins.install;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import hudson.Main;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.junit.jupiter.JenkinsSessionExtension;

@Tag("SmokeTest")
class SetupWizardRestartTest {

    @RegisterExtension
    private final JenkinsSessionExtension sessions = new JenkinsSessionExtension();

    @Issue("JENKINS-47439")
    @Test
    void restartKeepsSetupWizardState() throws Throwable {
        sessions.then(j -> {
                // Modify state so that we get into the same conditions as a real start
                Main.isUnitTest = false;
                Files.writeString(InstallUtil.getLastExecVersionFile().toPath(), "", StandardCharsets.US_ASCII);
                // Re-evaluate current state based on the new context
                InstallUtil.proceedToNextStateFrom(InstallState.UNKNOWN);
                assertEquals(InstallState.NEW, j.jenkins.getInstallState(), "Unexpected install state");
                assertTrue(j.jenkins.getSetupWizard().hasSetupWizardFilter(), "Expecting setup wizard filter to be up");
                InstallUtil.saveLastExecVersion();
        });
        // Check that the state is retained after a restart
        sessions.then(j -> {
                assertEquals(InstallState.NEW, j.jenkins.getInstallState(), "Unexpected install state");
                assertTrue(j.jenkins.getSetupWizard().hasSetupWizardFilter(),  "Expecting setup wizard filter to be up after restart");
        });
    }

}
