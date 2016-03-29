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

import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

import hudson.security.HudsonPrivateSecurityRealm;
import jenkins.model.Jenkins;

/**
 * Jenkins install state.
 *
 * @author <a href="mailto:tom.fennelly@gmail.com">tom.fennelly@gmail.com</a>
 */
@Restricted(NoExternalUse.class)
public enum InstallState {
    /**
     * The initial set up has been completed
     */
    INITIAL_SETUP_COMPLETED(true, null),
    /**
     * Creating an admin user for an initial Jenkins install.
     */
    CREATE_ADMIN_USER(false, INITIAL_SETUP_COMPLETED),
    /**
     * Configure security
     */
    CONFIGURE_SECURITY(false, CREATE_ADMIN_USER) {
        @Override
        public InstallState getNextState() {
            if(Jenkins.getInstance().getSecurityRealm() instanceof HudsonPrivateSecurityRealm) {
                return CREATE_ADMIN_USER;
            }
            return INITIAL_SETUP_COMPLETED;
        }
    },
    /**
     * New Jenkins install. The user has kicked off the process of installing an
     * initial set of plugins (via the install wizard).
     */
    INITIAL_PLUGINS_INSTALLING(false, CONFIGURE_SECURITY),
    /**
     * New Jenkins install.
     */
    NEW(false, INITIAL_PLUGINS_INSTALLING),
    /**
     * Restart of an existing Jenkins install.
     */
    RESTART(true, INITIAL_SETUP_COMPLETED),
    /**
     * Upgrade of an existing Jenkins install.
     */
    UPGRADE(true, INITIAL_SETUP_COMPLETED),
    /**
     * Downgrade of an existing Jenkins install.
     */
    DOWNGRADE(true, INITIAL_SETUP_COMPLETED),
    /**
     * Jenkins started in test mode (JenkinsRule).
     */
    TEST(true, INITIAL_SETUP_COMPLETED),
    /**
     * Jenkins started in development mode: Bolean.getBoolean("hudson.Main.development").
     * Can be run normally with the -Djenkins.install.runSetupWizard=true
     */
    DEVELOPMENT(true, INITIAL_SETUP_COMPLETED);

    private final boolean isSetupComplete;
    private final InstallState nextState;

    private InstallState(boolean isSetupComplete, InstallState nextState) {
        this.isSetupComplete = isSetupComplete;
        this.nextState = nextState;
    }

    /**
     * Indicates the initial setup is complete
     */
    public boolean isSetupComplete() {
        return isSetupComplete;
    }
    
    /**
     * Gets the next state
     */
    public InstallState getNextState() {
        return nextState;
    }
}
