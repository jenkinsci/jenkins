/*
 * The MIT License
 *
 * Copyright (c) 2015, CloudBees, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package jenkins.install;

import static java.util.logging.Level.SEVERE;
import static java.util.logging.Level.WARNING;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;

import org.apache.commons.io.FileUtils;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

import com.thoughtworks.xstream.XStream;

import hudson.Functions;
import hudson.model.UpdateCenter.DownloadJob.InstallationStatus;
import hudson.model.UpdateCenter.DownloadJob.Installing;
import hudson.security.AuthorizationStrategy;
import hudson.security.FullControlOnceLoggedInAuthorizationStrategy;
import hudson.security.HudsonPrivateSecurityRealm;
import hudson.security.SecurityRealm;
import hudson.model.UpdateCenter.InstallationJob;
import hudson.model.UpdateCenter.UpdateCenterJob;
import hudson.util.VersionNumber;
import jenkins.model.Jenkins;
import jenkins.util.SystemProperties;
import jenkins.util.xml.XMLUtils;

/**
 * Jenkins install utilities.
 *
 * @author <a href="mailto:tom.fennelly@gmail.com">tom.fennelly@gmail.com</a>
 */
@Restricted(NoExternalUse.class)
public class InstallUtil {

    private static final Logger LOGGER = Logger.getLogger(InstallUtil.class.getName());

    // tests need this to be 1.0
    private static final VersionNumber NEW_INSTALL_VERSION = new VersionNumber("1.0");

    /**
     * Get the current installation state.
     * @return The type of "startup" currently under way in Jenkins.
     */
    public static InstallState getInstallState() {
        // Support a simple state override. Useful for testing.
        String stateOverride = System.getenv("jenkins.install.state");
        if (stateOverride != null) {
            try {
                return InstallState.valueOf(stateOverride.toUpperCase());
            } catch (RuntimeException e) {
                throw new IllegalStateException("Unknown install state override specified on the commandline: '" + stateOverride + "'.");
            }
        }
        
        // Support a 3-state flag for running or disabling the setup wizard
        String shouldRunFlag = SystemProperties.getString("jenkins.install.runSetupWizard");
        boolean shouldRun = "true".equalsIgnoreCase(shouldRunFlag);
        boolean shouldNotRun = "false".equalsIgnoreCase(shouldRunFlag);
        
        // install wizard will always run if environment specified
        if (!shouldRun) {
            if (Functions.getIsUnitTest()) {
                return InstallState.TEST;
            }
            
            if (Boolean.getBoolean("hudson.Main.development")) {
                return InstallState.DEVELOPMENT;
            }
        }

        VersionNumber lastRunVersion = new VersionNumber(getLastExecVersion());

        // Neither the top level config or the lastExecVersionFile have a version
        // stored in them, which means it's a new install.
        if (lastRunVersion.compareTo(NEW_INSTALL_VERSION) == 0) {
            Jenkins j = Jenkins.getInstance();
            
            // Allow for skipping
            if(shouldNotRun) {
                try {
                    SetupWizard.completeSetup(j);
                    UpgradeWizard.completeUpgrade(j);
                    return j.getInstallState();
                } catch (RuntimeException e) {
                    throw e;
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }

            // Edge case: used Jenkins 1 but did not save the system config page,
            // the version is not persisted and returns 1.0, so try to check if
            // they actually did anything
            if (!j.getItemMap().isEmpty() || !mayBeJenkins2SecurityDefaults(j)) {
                return InstallState.UPGRADE;
            }
            return InstallState.NEW;
        }

        // We have a last version.

        VersionNumber currentRunVersion = new VersionNumber(getCurrentExecVersion());
        if (lastRunVersion.isOlderThan(currentRunVersion)) {
            return InstallState.UPGRADE;
        } else if (lastRunVersion.isNewerThan(currentRunVersion)) {
            return InstallState.DOWNGRADE;
        } else {
            // Last running version was the same as "this" running version.
            return InstallState.RESTART;
        }
    }

    /**
     * This could be an upgrade, detect a non-default security realm for the stupid case
     * where someone installed 1.x and did not save global config or create any items...
     */
    private static boolean mayBeJenkins2SecurityDefaults(Jenkins j) {
        if(j.getSecurityRealm() == SecurityRealm.NO_AUTHENTICATION) { // called before security set up first
            return true;
        }
        if(j.getSecurityRealm() instanceof HudsonPrivateSecurityRealm) { // might be called after a restart, setup isn't complete
            HudsonPrivateSecurityRealm securityRealm = (HudsonPrivateSecurityRealm)j.getSecurityRealm();
            if(securityRealm.getAllUsers().size() == 1 && securityRealm.getUser(SetupWizard.initialSetupAdminUserName) != null) {
                AuthorizationStrategy authStrategy = j.getAuthorizationStrategy();
                if(authStrategy instanceof FullControlOnceLoggedInAuthorizationStrategy) {
                    // must have been using 2.0+ to set this, as it wasn't present in 1.x and the default is true, to _allow_ anon read
                    if(!((FullControlOnceLoggedInAuthorizationStrategy)authStrategy).isAllowAnonymousRead()) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Save the current Jenkins instance version as the last executed version.
     * <p>
     * This state information is required in order to determine whether or not the Jenkins instance
     * is just restarting, or is being upgraded from an earlier version.
     */
    public static void saveLastExecVersion() {
        if (Jenkins.VERSION.equals(Jenkins.UNCOMPUTED_VERSION)) {
            // This should never happen!! Only adding this check in case someone moves the call to this method to the wrong place.
            throw new IllegalStateException("Unexpected call to InstallUtil.saveLastExecVersion(). Jenkins.VERSION has not been initialized. Call computeVersion() first.");
        }
        saveLastExecVersion(Jenkins.VERSION);
    }

    /**
     * Get the last saved Jenkins instance version.
     * @return The last saved Jenkins instance version.
     * @see #saveLastExecVersion()
     */
    public static @Nonnull String getLastExecVersion() {
        File lastExecVersionFile = getLastExecVersionFile();
        if (lastExecVersionFile.exists()) {
            try {
                return FileUtils.readFileToString(lastExecVersionFile);
            } catch (IOException e) {
                LOGGER.log(SEVERE, "Unexpected Error. Unable to read " + lastExecVersionFile.getAbsolutePath(), e);
                LOGGER.log(WARNING, "Unable to determine the last running version (see error above). Treating this as a restart. No plugins will be updated.");
                return getCurrentExecVersion();
            }
        } else {
            // Backward compatibility. Use the last version stored in the top level config.xml.
            // Going to read the value directly from the config.xml file Vs hoping that the
            // Jenkins startup sequence has moved far enough along that it has loaded the
            // global config. It can't load the global config until well into the startup
            // sequence because the unmarshal requires numerous objects to be created e.g.
            // it requires the Plugin Manager. It happens too late and it's too risky to
            // change how it currently works.
            File configFile = getConfigFile();
            if (configFile.exists()) {
                try {
                    String lastVersion = XMLUtils.getValue("/hudson/version", configFile);
                    if (lastVersion.length() > 0) {
                        return lastVersion;
                    }
                } catch (Exception e) {
                    LOGGER.log(SEVERE, "Unexpected error reading global config.xml", e);
                }
            }
            return NEW_INSTALL_VERSION.toString();
        }
    }

    /**
     * Save a specific version as the last execute version.
     * @param version The version to save.
     */
    static void saveLastExecVersion(@Nonnull String version) {
        File lastExecVersionFile = getLastExecVersionFile();
        try {
            FileUtils.write(lastExecVersionFile, version);
        } catch (IOException e) {
            LOGGER.log(SEVERE, "Failed to save " + lastExecVersionFile.getAbsolutePath(), e);
        }
    }

    static File getConfigFile() {
        return new File(Jenkins.getInstance().getRootDir(), "config.xml");
    }

    static File getLastExecVersionFile() {
        return new File(Jenkins.getInstance().getRootDir(), "jenkins.install.InstallUtil.lastExecVersion");
    }

    static File getInstallingPluginsFile() {
        return new File(Jenkins.getInstance().getRootDir(), "jenkins.install.InstallUtil.installingPlugins");
    }

    private static String getCurrentExecVersion() {
        if (Jenkins.VERSION.equals(Jenkins.UNCOMPUTED_VERSION)) {
            // This should never happen!! Only adding this check in case someone moves the call to this method to the wrong place.
            throw new IllegalStateException("Unexpected call to InstallUtil.getCurrentExecVersion(). Jenkins.VERSION has not been initialized. Call computeVersion() first.");
        }
        return Jenkins.VERSION;
    }

    /**
     * Returns a list of any plugins that are persisted in the installing list
     */
    @SuppressWarnings("unchecked")
	public static synchronized @CheckForNull Map<String,String> getPersistedInstallStatus() {
        File installingPluginsFile = getInstallingPluginsFile();
        if(installingPluginsFile == null || !installingPluginsFile.exists()) {
		return null;
        }
        return (Map<String,String>)new XStream().fromXML(installingPluginsFile);
    }

    /**
     * Persists a list of installing plugins; this is used in the case Jenkins fails mid-installation and needs to be restarted
     * @param installingPlugins
     */
    public static synchronized void persistInstallStatus(List<UpdateCenterJob> installingPlugins) {
        File installingPluginsFile = getInstallingPluginsFile();
	if(installingPlugins == null || installingPlugins.isEmpty()) {
		installingPluginsFile.delete();
		return;
	}
	LOGGER.fine("Writing install state to: " + installingPluginsFile.getAbsolutePath());
	Map<String,String> statuses = new HashMap<String,String>();
	for(UpdateCenterJob j : installingPlugins) {
		if(j instanceof InstallationJob && j.getCorrelationId() != null) { // only include install jobs with a correlation id (directly selected)
			InstallationJob ij = (InstallationJob)j;
			InstallationStatus status = ij.status;
			String statusText = status.getType();
			if(status instanceof Installing) { // flag currently installing plugins as pending
				statusText = "Pending";
			}
			statuses.put(ij.plugin.name, statusText);
		}
	}
        try {
		String installingPluginXml = new XStream().toXML(statuses);
            FileUtils.write(installingPluginsFile, installingPluginXml);
        } catch (IOException e) {
            LOGGER.log(SEVERE, "Failed to save " + installingPluginsFile.getAbsolutePath(), e);
        }
    }

    /**
     * Call to remove any active install status
     */
	public static void clearInstallStatus() {
		persistInstallStatus(null);
	}
}
