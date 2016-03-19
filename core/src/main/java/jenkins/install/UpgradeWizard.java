package jenkins.install;

import hudson.Extension;
import hudson.model.PageDecorator;
import hudson.model.UpdateSite.Plugin;
import hudson.util.HttpResponses;
import hudson.util.VersionNumber;
import jenkins.model.Jenkins;
import org.apache.commons.io.FileUtils;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.interceptor.RequirePOST;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import static java.util.logging.Level.*;
import static org.apache.commons.io.FileUtils.*;
import static org.apache.commons.lang.StringUtils.*;

/**
 * This is a stop-gap measure until JENKINS-33663 comes in.
 * This call may go away. Please don't use it from outside.
 *
 * @author Kohsuke Kawaguchi
 */
@Restricted(NoExternalUse.class)
@Extension
public class UpgradeWizard extends PageDecorator {
    @Inject
    private Jenkins jenkins;

    /**
     * Is this instance fully upgraded?
     */
    private volatile boolean upToDate;

    /**
     * File that captures the state of upgrade.
     *
     * This file records the vesrion number that the installation has upgraded to.
     */
    /*package*/ File getStateFile() {
        return new File(Jenkins.getInstance().getRootDir(),"upgraded");
    }

    public UpgradeWizard() throws IOException {
        updateUpToDate();
    }

    private void updateUpToDate() throws IOException {
        upToDate = new VersionNumber("2.0").compareTo(getCurrentLevel()) <= 0;
    }

    /**
     * What is the version the upgrade wizard has run the last time and upgraded to?
     */
    private VersionNumber getCurrentLevel() throws IOException {
        VersionNumber from = new VersionNumber("1.0");
        File state = getStateFile();
        if (state.exists()) {
            from = new VersionNumber(defaultIfBlank(readFileToString(state), "1.0").trim());
        }
        return from;
    }

    /*package*/
    public void setCurrentLevel(VersionNumber v) throws IOException {
        FileUtils.writeStringToFile(getStateFile(), v.toString());
    }

    /**
     * Do we need to show the upgrade wizard prompt?
     */
    public boolean isDue() {
        if (upToDate)
            return false;

        // only admin users should see this
        if (!jenkins.hasPermission(Jenkins.ADMINISTER))
            return false;

        return System.currentTimeMillis() > getStateFile().lastModified();
    }

    /**
     * Snooze the upgrade wizard notice.
     */
    @RequirePOST
    public HttpResponse doSnooze() throws IOException {
        jenkins.checkPermission(Jenkins.ADMINISTER);
        File f = getStateFile();
        FileUtils.touch(f);
        f.setLastModified(System.currentTimeMillis() + TimeUnit.DAYS.toMillis(1));
        LOGGER.log(FINE, "Snoozed the upgrade wizard notice");
        return HttpResponses.redirectToContextRoot();
    }

    /**
     * Performs the upgrade activity.
     */
    @RequirePOST
    public HttpResponse doUpgrade() throws IOException {
        try {
            if (new VersionNumber("2.0").compareTo(getCurrentLevel())>0) {
                // 1.0 -> 2.0 upgrade
                LOGGER.log(WARNING, "Performing 1.0 to 2.0 upgrade");

                for (String shortName : Arrays.asList("workflow-aggregator", "pipeline-stage-view", "github-organization-folder")) {
                    Plugin p = jenkins.getUpdateCenter().getPlugin(shortName);
                    if (p==null) {
                        LOGGER.warning("Plugin not found in the update center: " + shortName);
                    } else {
                        p.deploy(true);
                    }
                }

                // upgrade to 2.0 complete (if plugin installations fail, that's too bad)
                FileUtils.writeStringToFile(getStateFile(),"2.0");

                // send the user to the update center so that people can see the installation & restart if need be.
                return HttpResponses.redirectViaContextPath("updateCenter/");
            }

//      in the future...
//        if (new VersionNumber("3.0").compareTo(getCurrentLevel())>0) {
//
//        }

            return NOOP;
        } finally {
            updateUpToDate();
        }
    }

    /*package*/ static final HttpResponse NOOP = HttpResponses.redirectToContextRoot();


    private static final Logger LOGGER = Logger.getLogger(UpgradeWizard.class.getName());
}
