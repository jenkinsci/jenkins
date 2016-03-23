package jenkins.install;

import hudson.util.VersionNumber;
import org.apache.commons.io.FileUtils;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import javax.inject.Inject;

import java.io.IOException;

import static org.junit.Assert.*;

/**
 * @author Kohsuke Kawaguchi
 */
public class UpgradeWizardTest {
    @Rule
    public final JenkinsRule j = new JenkinsRule();

    @Inject
    UpgradeWizard uw;

    @Before
    public void setup() {
        j.jenkins.getInjector().injectMembers(this);
    }

    @Test
    public void snooze() throws Exception {
        assertTrue(uw.isDue());
        uw.doSnooze();
        assertFalse(uw.isDue());
    }

    /**
     * If not upgraded, the upgrade should cause some side effect.
     */
    @Test
    public void upgrade() throws Exception {
        assertTrue(j.jenkins.getUpdateCenter().getJobs().size() == 0);
        assertTrue(newInstance().isDue());
        assertNotSame(UpgradeWizard.NOOP, uw.doUpgrade());

        // can't really test this because UC metadata is empty
        // assertTrue(j.jenkins.getUpdateCenter().getJobs().size() > 0);
    }

    /**
     * If already upgraded, don't show anything
     */
    @Test
    public void fullyUpgraded() throws Exception {
        FileUtils.writeStringToFile(uw.getStateFile(), "2.0");
        assertFalse(newInstance().isDue());
        assertSame(UpgradeWizard.NOOP, uw.doUpgrade());
    }

    /**
     * If downgraded from future version, don't prompt upgrade wizard.
     */
    @Test
    public void downgradeFromFuture() throws Exception {
        FileUtils.writeStringToFile(uw.getStateFile(), "3.0");
        assertFalse(newInstance().isDue());
        assertSame(UpgradeWizard.NOOP, uw.doUpgrade());
    }

    @Test
    public void freshInstallation() throws Exception {
        assertTrue(uw.isDue());
        uw.setCurrentLevel(new VersionNumber("2.0"));
        assertFalse(uw.isDue());
    }

    /**
     * Fresh instance of {@link UpgradeWizard} to test its behavior.
     */
    private UpgradeWizard newInstance() throws IOException {
        UpgradeWizard uw2 = new UpgradeWizard();
        j.jenkins.getInjector().injectMembers(uw2);
        return uw2;
    }
}
