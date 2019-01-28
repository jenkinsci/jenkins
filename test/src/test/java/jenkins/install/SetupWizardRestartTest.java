package jenkins.install;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.IOException;

import org.apache.commons.io.FileUtils;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runners.model.Statement;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.RestartableJenkinsRule;

import hudson.Main;
import jenkins.model.Jenkins;
import org.jvnet.hudson.test.SmokeTest;

@Category(SmokeTest.class)
public class SetupWizardRestartTest {
    @Rule
    public RestartableJenkinsRule rr = new RestartableJenkinsRule();

    @Issue("JENKINS-47439")
    @Test
    public void restartKeepsSetupWizardState() {
        rr.addStep(new Statement() {
            @Override
            public void evaluate() throws IOException {
                // Modify state so that we get into the same conditions as a real start
                Main.isUnitTest = false;
                FileUtils.write(InstallUtil.getLastExecVersionFile(), "");
                Jenkins j = rr.j.getInstance();
                // Re-evaluate current state based on the new context
                InstallUtil.proceedToNextStateFrom(InstallState.UNKNOWN);
                assertEquals("Unexpected install state", InstallState.NEW, j.getInstallState());
                assertTrue("Expecting setup wizard filter to be up", j.getSetupWizard().hasSetupWizardFilter());
                InstallUtil.saveLastExecVersion();
            }
        });
        // Check that the state is retained after a restart
        rr.addStep(new Statement() {
            @Override
            public void evaluate() {
                Jenkins j = rr.j.getInstance();
                assertEquals("Unexpected install state", InstallState.NEW, j.getInstallState());
                assertTrue("Expecting setup wizard filter to be up after restart",  j.getSetupWizard().hasSetupWizardFilter());
            }
        });
    }

}
