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

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;

import hudson.Extension;
import hudson.ExtensionList;
import hudson.ExtensionPoint;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.model.Jenkins;
import org.apache.commons.lang.StringUtils;
/**
 * Jenkins install state.
 *
 * In order to hook into the setup wizard lifecycle, you should
 * include something in a script that call
 * to `onSetupWizardInitialized` with a callback, for example:
 * 
 * See <em><code>upgradeWizard.js</code></em> for an example
 * 
 * @author <a href="mailto:tom.fennelly@gmail.com">tom.fennelly@gmail.com</a>
 */
public class InstallState implements ExtensionPoint {
    /**
     * Need InstallState != NEW for tests by default
     */
    @Extension
    public static final InstallState UNKNOWN = new InstallState("UNKNOWN", true);
    
    /**
     * After any setup / restart / etc. hooks are done, states hould be running
     */
    @Extension
    public static final InstallState RUNNING = new InstallState("RUNNING", true);
    
    /**
     * The initial set up has been completed
     */
    @Extension
    public static final InstallState INITIAL_SETUP_COMPLETED = new InstallState("INITIAL_SETUP_COMPLETED", true) {
        public void initializeState() {
            Jenkins j = Jenkins.getInstance();
            try {
                j.getSetupWizard().completeSetup();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            j.setInstallState(RUNNING);
        }
    };
    
    /**
     * Creating an admin user for an initial Jenkins install.
     */
    @Extension
    public static final InstallState CREATE_ADMIN_USER = new InstallState("CREATE_ADMIN_USER", false) {
        public void initializeState() {
            Jenkins j = Jenkins.getInstance();
            // Skip this state if not using the security defaults
            // e.g. in an init script set up security already
            if (!j.getSetupWizard().isUsingSecurityDefaults()) {
                InstallUtil.proceedToNextStateFrom(this);
            }
        }
    };
    
    /**
     * New Jenkins install. The user has kicked off the process of installing an
     * initial set of plugins (via the install wizard).
     */
    @Extension
    public static final InstallState INITIAL_PLUGINS_INSTALLING = new InstallState("INITIAL_PLUGINS_INSTALLING", false);
    
    /**
     * Security setup for a new Jenkins install.
     */
    @Extension
    public static final InstallState INITIAL_SECURITY_SETUP = new InstallState("INITIAL_SECURITY_SETUP", false) {
        public void initializeState() {
            try {
                Jenkins.getInstance().getSetupWizard().init(true);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            
            InstallUtil.proceedToNextStateFrom(INITIAL_SECURITY_SETUP);
        }
    };
    
    /**
     * New Jenkins install.
     */
    @Extension
    public static final InstallState NEW = new InstallState("NEW", false);
    
    /**
     * Restart of an existing Jenkins install.
     */
    @Extension
    public static final InstallState RESTART = new InstallState("RESTART", true) {
        public void initializeState() {
            InstallUtil.saveLastExecVersion();
        }
    };
    
    /**
     * Upgrade of an existing Jenkins install.
     */
    @Extension
    public static final InstallState UPGRADE = new UpgradeWizard();
    
    /**
     * Downgrade of an existing Jenkins install.
     */
    @Extension
    public static final InstallState DOWNGRADE = new InstallState("DOWNGRADE", true) {
        public void initializeState() {
            InstallUtil.saveLastExecVersion();
        }
    };
    
    private static final Logger LOGGER = Logger.getLogger(InstallState.class.getName());
    
    /**
     * Jenkins started in test mode (JenkinsRule).
     */
    public static final InstallState TEST = new InstallState("TEST", true);
    
    /**
     * Jenkins started in development mode: Bolean.getBoolean("hudson.Main.development").
     * Can be run normally with the -Djenkins.install.runSetupWizard=true
     */
    public static final InstallState DEVELOPMENT = new InstallState("DEVELOPMENT", true);

    private final boolean isSetupComplete;
    private final String name;

    public InstallState(@Nonnull String name, boolean isSetupComplete) {
        this.name = name;
        this.isSetupComplete = isSetupComplete;
    }
    
    /**
     * Process any initialization this install state requires
     */
    public void initializeState() {
    }
    
    public Object readResolve() {
        // If we get invalid state from the configuration, fallback to unknown
        if (StringUtils.isBlank(name)) {
            LOGGER.log(Level.WARNING, "Read install state with blank name: '{0}'. It will be ignored", name);
            return UNKNOWN;
        }
        
        InstallState state = InstallState.valueOf(name);
        if (state == null) {
            LOGGER.log(Level.WARNING, "Cannot locate an extension point for the state '{0}'. It will be ignored", name);
            return UNKNOWN;
        }
        
        // Otherwise we return the actual state
        return state;
    }

    /**
     * Indicates the initial setup is complete
     */
    public boolean isSetupComplete() {
        return isSetupComplete;
    }
    
    public String name() {
        return name;
    }
    
    @Override
    public int hashCode() {
        return name.hashCode();
    }
    
    @Override
    public boolean equals(Object obj) {
        if(obj instanceof InstallState) {
            return name.equals(((InstallState)obj).name());
        }
        return false;
    }
    
    @Override
    public String toString() {
        return "InstallState (" + name + ")";
    }

    /**
     * Find an install state by name
     * @param name
     * @return
     */
    @CheckForNull
    public static InstallState valueOf(@Nonnull String name) {
        for (InstallState state : all()) {
            if (name.equals(state.name)) {
                return state;
            }
        }
        return null;
    }

    /**
     * Returns all install states in the system
     */
    static ExtensionList<InstallState> all() {
        return ExtensionList.lookup(InstallState.class);
    }
}
