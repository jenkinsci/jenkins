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

import hudson.Functions;
import hudson.util.VersionNumber;
import jenkins.model.Jenkins;
import jenkins.util.xml.XMLUtils;
import org.apache.commons.io.FileUtils;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

import javax.annotation.Nonnull;
import java.io.File;
import java.io.IOException;
import java.util.logging.Logger;

import static java.util.logging.Level.SEVERE;

/**
 * Jenkins startup utilities.
 *
 * @author <a href="mailto:tom.fennelly@gmail.com">tom.fennelly@gmail.com</a>
 */
@Restricted(NoExternalUse.class)
public class StartupUtil {

    private static final Logger LOGGER = Logger.getLogger(StartupUtil.class.getName());

    private static final VersionNumber NEW_INSTALL_VERSION = new VersionNumber("1.0");
    
    /**
     * Get the type of "startup" currently under way in Jenkins.
     * @return The type of "startup" currently under way in Jenkins.
     */
    public static StartupType getStartupType() {
        if (Functions.getIsUnitTest()) {
            return StartupType.TEST;
        }
        
        VersionNumber lastRunVersion = new VersionNumber(getLastExecVersion());

        // Neither the top level config or the lastExecVersionFile have a version
        // stored in them, which means it's a new install.
        if (lastRunVersion.compareTo(NEW_INSTALL_VERSION) == 0) {
            return StartupType.NEW;
        }

        // We have a last version.

        VersionNumber currentRunVersion = new VersionNumber(getCurrentExecVersion());
        if (lastRunVersion.isOlderThan(currentRunVersion)) {
            return StartupType.UPGRADE;
        } else if (lastRunVersion.isNewerThan(currentRunVersion)) {
            return StartupType.DOWNGRADE;
        } else {
            // Last running version was the same as "this" running version.
            return StartupType.RESTART;
        }
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
            throw new IllegalStateException("Unexpected call to StartupUtil.saveLastExecVersion(). Jenkins.VERSION has not been initialized. Call computeVersion() first.");
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
                throw new IllegalStateException("Unexpected Error. Unable to read " + lastExecVersionFile.getAbsolutePath(), e);
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
        return new File(Jenkins.getActiveInstance().getRootDir(), "config.xml");
    }

    static File getLastExecVersionFile() {
        return new File(Jenkins.getActiveInstance().getRootDir(), ".last_exec_version");
    }

    private static String getCurrentExecVersion() {
        if (Jenkins.VERSION.equals(Jenkins.UNCOMPUTED_VERSION)) {
            // This should never happen!! Only adding this check in case someone moves the call to this method to the wrong place.
            throw new IllegalStateException("Unexpected call to StartupUtil.getCurrentExecVersion(). Jenkins.VERSION has not been initialized. Call computeVersion() first.");
        }
        return Jenkins.VERSION;
    }
}
