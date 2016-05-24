package jenkins.install;

import static java.util.logging.Level.FINE;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.inject.Provider;
import javax.servlet.http.HttpSession;

import org.apache.commons.io.FileUtils;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.WebApp;
import org.kohsuke.stapler.interceptor.RequirePOST;

import hudson.Extension;
import hudson.util.HttpResponses;
import jenkins.model.Jenkins;
import net.sf.json.JSONArray;

/**
 * This class is responsible for specific upgrade behaviors in the Jenkins UI.
 * 
 * Note an instance of this class is already an @Extension in {@link InstallState#UPGRADE}
 *
 * @author Kohsuke Kawaguchi, Keith Zantow
 */
@Restricted(NoExternalUse.class)
public class UpgradeWizard extends InstallState {
    /**
     * Is this instance fully upgraded?
     */
    private volatile boolean isUpToDate = true;
    
    /**
     * Whether to show the upgrade wizard
     */
    private static final String SHOW_UPGRADE_WIZARD_FLAG = UpgradeWizard.class.getName() + ".show";

    /*package*/ UpgradeWizard() {
        super("UPGRADE", false);
    }
    
    /**
     * Get the upgrade wizard instance
     */
    public static UpgradeWizard get() {
        return (UpgradeWizard)InstallState.UPGRADE;
    }
    
    @Override
    public void initializeState() {
        // Initializing this state is directly related to 
        // running the detached plugin checks, these should be consolidated somehow
        updateUpToDate();
        
        // If there are no platform updates, proceed to running
        if (isUpToDate) {
            if (Jenkins.getInstance().getSetupWizard().getPlatformPluginUpdates().isEmpty()) {
                Jenkins.getInstance().setInstallState(InstallState.RUNNING);
            }
        }
    }
    
    @Override
    public boolean isSetupComplete() {
        return !isDue();
    }
    
    private void updateUpToDate() {
        // If we don't have any platform plugins, it's considered 'up to date' in terms
        // of the updater
        try {
            JSONArray platformPlugins = Jenkins.getInstance().getSetupWizard().getPlatformPluginUpdates();
            isUpToDate = platformPlugins.isEmpty();
        } catch(Exception e) {
            LOGGER.log(Level.WARNING, "Unable to get the platform plugin update list.", e);
        }
    }

    /**
     * Do we need to show the upgrade wizard prompt?
     */
    public boolean isDue() {
        if (isUpToDate)
            return false;

        // only admin users should see this
        if (!Jenkins.getInstance().hasPermission(Jenkins.ADMINISTER))
            return false;

        // only show when Jenkins is fully up & running
        WebApp wa = WebApp.getCurrent();
        if (wa==null || !(wa.getApp() instanceof Jenkins))
            return false;

        return System.currentTimeMillis() > SetupWizard.getUpdateStateFile().lastModified();
    }
    
    /**
     * Whether to show the upgrade wizard
     */
    public boolean isShowUpgradeWizard() {
        HttpSession session = Stapler.getCurrentRequest().getSession(false);
        if(session != null) {
            return Boolean.TRUE.equals(session.getAttribute(SHOW_UPGRADE_WIZARD_FLAG));
        }
        return false;
    }
    /**
     * Call this to show the upgrade wizard
     */
    public HttpResponse doShowUpgradeWizard() throws Exception {
        Jenkins.getInstance().checkPermission(Jenkins.ADMINISTER);
        HttpSession session = Stapler.getCurrentRequest().getSession(true);
        session.setAttribute(SHOW_UPGRADE_WIZARD_FLAG, true);
        return HttpResponses.redirectToContextRoot();
    }
    
    /**
     * Call this to hide the upgrade wizard
     */
    public HttpResponse doHideUpgradeWizard() {
        Jenkins.getInstance().checkPermission(Jenkins.ADMINISTER);
        HttpSession session = Stapler.getCurrentRequest().getSession(false);
        if(session != null) {
            session.removeAttribute(SHOW_UPGRADE_WIZARD_FLAG);
        }
        return HttpResponses.redirectToContextRoot();
    }

    /**
     * Snooze the upgrade wizard notice.
     */
    @RequirePOST
    public HttpResponse doSnooze() throws IOException {
        Jenkins.getInstance().checkPermission(Jenkins.ADMINISTER);
        File f = SetupWizard.getUpdateStateFile();
        FileUtils.touch(f);
        f.setLastModified(System.currentTimeMillis() + TimeUnit.DAYS.toMillis(1));
        LOGGER.log(FINE, "Snoozed the upgrade wizard notice");
        return HttpResponses.redirectToContextRoot();
    }
    
    @Extension
    public static class ListenForInstallComplete extends InstallStateFilter {
        @Override
        public InstallState getNextInstallState(InstallState current, Provider<InstallState> proceed) {
            InstallState next = proceed.get();
            if (InstallState.INITIAL_SETUP_COMPLETED.equals(current)) {
                UpgradeWizard.get().isUpToDate = true;
            }
            return next;
        }
    }

    private static final Logger LOGGER = Logger.getLogger(UpgradeWizard.class.getName());
}
