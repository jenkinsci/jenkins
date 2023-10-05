package jenkins.install;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import hudson.Main;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsSessionRule;
import org.jvnet.hudson.test.SmokeTest;

@Category(SmokeTest.class)
public class SetupWizardRestartTest {
    @Rule
    public JenkinsSessionRule sessions = new JenkinsSessionRule();

    @Issue("JENKINS-47439")
    @Test
    public void restartKeepsSetupWizardState() throws Throwable {
        sessions.then(j -> {
                // Modify state so that we get into the same conditions as a real start
                Main.isUnitTest = false;
                Files.writeString(InstallUtil.getLastExecVersionFile().toPath(), "", StandardCharsets.US_ASCII);
                // Re-evaluate current state based on the new context
                InstallUtil.proceedToNextStateFrom(InstallState.UNKNOWN);
                assertEquals("Unexpected install state", InstallState.NEW, j.jenkins.getInstallState());
                assertTrue("Expecting setup wizard filter to be up", j.jenkins.getSetupWizard().hasSetupWizardFilter());
                InstallUtil.saveLastExecVersion();
        });
        // Check that the state is retained after a restart
        sessions.then(j -> {
                assertEquals("Unexpected install state", InstallState.NEW, j.jenkins.getInstallState());
                assertTrue("Expecting setup wizard filter to be up after restart",  j.jenkins.getSetupWizard().hasSetupWizardFilter());
        });
    }

}
