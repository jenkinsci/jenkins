package jenkins.install;

import jenkins.model.Jenkins;
import net.sf.json.JSONArray;

import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import hudson.Main;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

/**
 * @author Kohsuke Kawaguchi
 */
public class UpgradeWizardTest {
    @Rule
    public final JenkinsRule j = new JenkinsRule();
    
    private JSONArray platformPluginUpdates;
    
    private void rmStateFile() {
        File f = SetupWizard.getUpdateStateFile();
        if(f != null && f.exists()) {
            f.delete();
        }
    }
    
    private void setSetupWizard(SetupWizard wiz) throws Exception {
        Field f = Jenkins.class.getDeclaredField("setupWizard");
        f.setAccessible(true);
        f.set(Jenkins.getInstance(), wiz);
    }
    
    @Before
    public void setSetupWizard() throws Exception {
        rmStateFile();
        platformPluginUpdates = JSONArray.fromObject("[{name:'test-plugin',since:'2.1'}]");
        setSetupWizard(new SetupWizard() {
            @Override
            public JSONArray getPlatformPluginUpdates() {
                return platformPluginUpdates;
            }
        });
        // Disable the unit test flag.
        Main.isUnitTest = false;
    }
    
    @After
    public void restoreSetupWizard() throws Exception {
        rmStateFile();
        setSetupWizard(new SetupWizard());
        // Disable the unit test flag.
        Main.isUnitTest = true;
    }
    
    @Test
    public void snooze() throws Exception {
        j.executeOnServer(new Callable<Void>() {
            @Override
            public Void call() throws Exception {
                writeVersion("1.5");
                UpgradeWizard uw = getInstance();
                assertTrue(uw.isDue());
                uw.doSnooze();
                assertFalse(uw.isDue());

                return null;
            }
        });
    }

    /**
     * If not upgraded, the upgrade should cause some side effect.
     */
    @Test
    public void upgrade() throws Exception {
        j.executeOnServer(new Callable<Void>() {
            @Override
            public Void call() throws Exception {
                assertTrue(j.jenkins.getUpdateCenter().getJobs().size() == 0);
                writeVersion("1.5");
                assertTrue(getInstance().isDue());

                // can't really test this because UC metadata is empty
                // assertTrue(j.jenkins.getUpdateCenter().getJobs().size() > 0);

                return null;
            }
        });
    }

    /**
     * If already upgraded, don't show anything
     */
    @Test
    public void fullyUpgraded() throws Exception {
        j.executeOnServer(new Callable<Void>() {
            @Override
            public Void call() throws Exception {
                platformPluginUpdates = new JSONArray();
                assertFalse(getInstance().isDue());
                return null;
            }
        });
    }

    @Test
    public void freshInstallation() throws Exception {
        j.executeOnServer(new Callable<Void>() {
            @Override
            public Void call() throws Exception {
                InstallState prior = j.jenkins.getInstallState();
                try {
                    UpgradeWizard uw = getInstance();
                    assertTrue(uw.isDue()); // there are platform plugin updates
                    j.jenkins.getSetupWizard().completeSetup();
                    assertFalse(uw.isDue());
                    return null;
                } finally {
                    j.jenkins.setInstallState(prior);
                }
            }
        });
    }

    /**
     * Fresh instance of {@link UpgradeWizard} to test its behavior.
     */
    private UpgradeWizard getInstance() throws Exception {
        try {
            UpgradeWizard uw = UpgradeWizard.get();
            uw.initializeState();
            return uw;
        } catch(Exception e) {
            e.printStackTrace();
            throw e;
        }
    }

    /**
     * Write a version to the update file, update last modified to > 24h
     * @param s
     * @throws IOException
     */
    private static void writeVersion(String s) throws IOException {
        File f = SetupWizard.getUpdateStateFile();
        FileUtils.writeStringToFile(f, s);
        f.setLastModified(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(1));
    }
}
