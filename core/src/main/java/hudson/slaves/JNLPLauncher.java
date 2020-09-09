/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi, Stephen Connolly
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
package hudson.slaves;

import hudson.Extension;
import hudson.Util;
import hudson.model.Computer;
import hudson.model.Descriptor;
import hudson.model.TaskListener;
import hudson.util.FormValidation;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;

import jenkins.model.Jenkins;
import jenkins.slaves.RemotingWorkDirSettings;
import jenkins.util.java.JavaUtils;
import jenkins.websocket.WebSockets;
import org.jenkinsci.Symbol;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;

/**
 * {@link ComputerLauncher} via inbound connections.
 *
 * @author Stephen Connolly
 * @author Kohsuke Kawaguchi
*/
public class JNLPLauncher extends ComputerLauncher {
    /**
     * If the agent needs to tunnel the connection to the master,
     * specify the "host:port" here. This can include the special
     * syntax "host:" and ":port" to indicate the default host/port
     * shall be used.
     *
     * <p>
     * Null if no tunneling is necessary.
     *
     * @since 1.250
     */
    @CheckForNull
    public final String tunnel;

    /**
     * Additional JVM arguments. Can be null.
     * @since 1.297
     */
    @CheckForNull
    public final String vmargs;

    @NonNull
    private RemotingWorkDirSettings workDirSettings = RemotingWorkDirSettings.getEnabledDefaults();

    private boolean webSocket;

    /**
     * Constructor.
     * @param tunnel Tunnel settings
     * @param vmargs JVM arguments
     * @param workDirSettings Settings for Work Directory management in Remoting.
     *                        If {@code null}, {@link RemotingWorkDirSettings#getEnabledDefaults()}
     *                        will be used to enable work directories by default in new agents.
     * @since 2.68
     */
    @Deprecated
    public JNLPLauncher(@CheckForNull String tunnel, @CheckForNull String vmargs, @CheckForNull RemotingWorkDirSettings workDirSettings) {
        this(tunnel, vmargs);
        if (workDirSettings != null) {
            setWorkDirSettings(workDirSettings);
        }
    }
    
    @DataBoundConstructor
    public JNLPLauncher(@CheckForNull String tunnel, @CheckForNull String vmargs) {
        this.tunnel = Util.fixEmptyAndTrim(tunnel);
        this.vmargs = Util.fixEmptyAndTrim(vmargs);
    }

    /**
     * @deprecated This Launcher does not enable the work directory.
     *             It is recommended to use {@link #JNLPLauncher(boolean)}
     */
    @Deprecated
    public JNLPLauncher() {
        this(false);
    }
    
    /**
     * Constructor with default options.
     * 
     * @param enableWorkDir If {@code true}, the work directory will be enabled with default settings.
     */
    public JNLPLauncher(boolean enableWorkDir) {
        this(null, null, enableWorkDir 
                ? RemotingWorkDirSettings.getEnabledDefaults() 
                : RemotingWorkDirSettings.getDisabledDefaults());
    }
    
    protected Object readResolve() {
        if (workDirSettings == null) {
            // For the migrated code agents are always disabled
            workDirSettings = RemotingWorkDirSettings.getDisabledDefaults();
        }
        return this;
    }

    /**
     * Returns work directory settings.
     * 
     * @since 2.72
     */
    @NonNull
    public RemotingWorkDirSettings getWorkDirSettings() {
        return workDirSettings;
    }

    @DataBoundSetter
    public final void setWorkDirSettings(@NonNull RemotingWorkDirSettings workDirSettings) {
        this.workDirSettings = workDirSettings;
    }
    
    @Override
    public boolean isLaunchSupported() {
        return false;
    }

    /**
     * @since 2.216
     */
    public boolean isWebSocket() {
        return webSocket;
    }

    /**
     * @since 2.216
     */
    @DataBoundSetter
    public void setWebSocket(boolean webSocket) {
        this.webSocket = webSocket;
    }

    @Override
    public void launch(SlaveComputer computer, TaskListener listener) {
        // do nothing as we cannot self start
    }

    /**
     * @deprecated as of 1.XXX
     *      Use {@link Jenkins#getDescriptor(Class)}
     */
    @Deprecated
    public static /*almost final*/ Descriptor<ComputerLauncher> DESCRIPTOR;

    /**
     * Gets work directory options as a String.
     * 
     * In public API {@code getWorkDirSettings().toCommandLineArgs(computer)} should be used instead
     * @param computer Computer
     * @return Command line options for launching with the WorkDir
     */
    @NonNull
    @Restricted(NoExternalUse.class)
    public String getWorkDirOptions(@NonNull Computer computer) {
        if(!(computer instanceof SlaveComputer)) {
            return "";
        }
        return workDirSettings.toCommandLineString((SlaveComputer)computer);
    }
    
    @Extension @Symbol("jnlp")
    public static class DescriptorImpl extends Descriptor<ComputerLauncher> {
        public DescriptorImpl() {
            DESCRIPTOR = this;
        }

        public String getDisplayName() {
            return Messages.JNLPLauncher_displayName();
        }
        
        /**
         * Checks if Work Dir settings should be displayed.
         * 
         * This flag is checked in {@code config.jelly} before displaying the 
         * {@link JNLPLauncher#workDirSettings} property.
         * By default the configuration is displayed only for {@link JNLPLauncher},
         * but the implementation can be overridden.
         * @return {@code true} if work directories are supported by the launcher type.
         * @since 2.73
         */
        public boolean isWorkDirSupported() {
            // This property is included only for JNLPLauncher by default. 
            // Causes JENKINS-45895 in the case of includes otherwise
            return DescriptorImpl.class.equals(getClass());
        }

        public FormValidation doCheckWebSocket(@QueryParameter boolean webSocket, @QueryParameter String tunnel) {
            if (webSocket) {
                if (!WebSockets.isSupported()) {
                    return FormValidation.error("WebSocket support is not enabled in this Jenkins installation");
                }
                if (Util.fixEmptyAndTrim(tunnel) != null) {
                    return FormValidation.error("Tunneling is not supported in WebSocket mode");
                }
            } else {
                if (Jenkins.get().getTcpSlaveAgentListener() == null) {
                    return FormValidation.error("Either WebSocket mode is selected, or the TCP port for inbound agents must be enabled");
                }
            }
            return FormValidation.ok();
        }

    }

    /**
     * Returns true if Java Web Start button should be displayed.
     * Java Web Start is only supported when the Jenkins server is
     * running with Java 8.  Earlier Java versions are not supported by Jenkins.
     * Later Java versions do not support Java Web Start.
     *
     * This flag is checked in {@code config.jelly} before displaying the
     * Java Web Start button.
     * @return {@code true} if Java Web Start button should be displayed.
     * @since 2.153
     */
    @Restricted(NoExternalUse.class) // Jelly use
    public boolean isJavaWebStartSupported() {
        return JavaUtils.isRunningWithJava8OrBelow();
    }
}
