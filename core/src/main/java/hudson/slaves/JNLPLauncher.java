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

import com.google.common.escape.Escaper;
import com.google.common.escape.Escapers;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.Extension;
import hudson.Util;
import hudson.model.Computer;
import hudson.model.Descriptor;
import hudson.model.TaskListener;
import jenkins.model.Jenkins;
import jenkins.model.identity.InstanceIdentityProvider;
import jenkins.slaves.RemotingWorkDirSettings;
import jenkins.util.SystemProperties;
import jenkins.websocket.WebSockets;
import org.jenkinsci.Symbol;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.DoNotUse;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

/**
 * {@link ComputerLauncher} via inbound connections.
 *
 * @author Stephen Connolly
 * @author Kohsuke Kawaguchi
*/
@SuppressWarnings("deprecation") // see comments about CasC
public class JNLPLauncher extends ComputerLauncher {
    /**
     * Deprecated (only used with deprecated {@code -jnlpUrl} mode), but cannot mark it as such without breaking CasC.
     */
    @CheckForNull
    @SuppressFBWarnings(value = "PA_PUBLIC_PRIMITIVE_ATTRIBUTE", justification = "Preserve API compatibility")
    public String tunnel;

    /**
     * @deprecated No longer used.
     */
    @Deprecated
    public final transient String vmargs = null;

    @NonNull
    private RemotingWorkDirSettings workDirSettings = RemotingWorkDirSettings.getEnabledDefaults();

    private boolean webSocket;

    /**
     * @see #getInboundAgentUrl()
     */
    @NonNull
    @Restricted(NoExternalUse.class)
    public static final String CUSTOM_INBOUND_URL_PROPERTY = "jenkins.agent.inboundUrl";

    /**
     * @deprecated no useful properties, use {@link #JNLPLauncher()}
     */
    @Deprecated
    public JNLPLauncher(@CheckForNull String tunnel, @CheckForNull String vmargs, @CheckForNull RemotingWorkDirSettings workDirSettings) {
        this(tunnel, vmargs);
        if (workDirSettings != null) {
            setWorkDirSettings(workDirSettings);
        }
    }

    /**
     * @deprecated no useful properties, use {@link #JNLPLauncher()}
     */
    @Deprecated
    public JNLPLauncher(@CheckForNull String tunnel) {
        this.tunnel = Util.fixEmptyAndTrim(tunnel);
    }

    /**
     * @deprecated no useful properties, use {@link #JNLPLauncher()}
     */
    @Deprecated
    public JNLPLauncher(@CheckForNull String tunnel, @CheckForNull String vmargs) {
        this.tunnel = Util.fixEmptyAndTrim(tunnel);
    }

    @DataBoundConstructor
    public JNLPLauncher() {
    }

    /**
     * @deprecated no useful properties, use {@link #JNLPLauncher()}
     */
    @Deprecated
    public JNLPLauncher(boolean enableWorkDir) {
        this(null, null, enableWorkDir
                ? RemotingWorkDirSettings.getEnabledDefaults()
                : RemotingWorkDirSettings.getDisabledDefaults());
    }

    @SuppressFBWarnings(value = "RCN_REDUNDANT_NULLCHECK_OF_NONNULL_VALUE", justification = "workDirSettings in readResolve is needed for data migration.")
    protected Object readResolve() {
        if (workDirSettings == null) {
            // For the migrated code agents are always disabled
            workDirSettings = RemotingWorkDirSettings.getDisabledDefaults();
        }
        return this;
    }

    /**
     * Deprecated (only used with deprecated {@code -jnlpUrl} mode), but cannot mark it as such without breaking CasC.
     */
    public RemotingWorkDirSettings getWorkDirSettings() {
        return workDirSettings;
    }

    /**
     * Deprecated (only used with deprecated {@code -jnlpUrl} mode), but cannot mark it as such without breaking CasC.
     */
    @DataBoundSetter
    public final void setWorkDirSettings(@NonNull RemotingWorkDirSettings workDirSettings) {
        this.workDirSettings = workDirSettings;
    }

    @Override
    public boolean isLaunchSupported() {
        return false;
    }

    /**
     * Deprecated (only used with deprecated {@code -jnlpUrl} mode), but cannot mark it as such without breaking CasC.
     */
    public boolean isWebSocket() {
        return webSocket;
    }

    /**
     * Deprecated (only used with deprecated {@code -jnlpUrl} mode), but cannot mark it as such without breaking CasC.
     */
    @DataBoundSetter
    public void setWebSocket(boolean webSocket) {
        this.webSocket = webSocket;
    }

    /**
     * Deprecated (only used with deprecated {@code -jnlpUrl} mode), but cannot mark it as such without breaking CasC.
     */
    public String getTunnel() {
        return tunnel;
    }

    /**
     * Deprecated (only used with deprecated {@code -jnlpUrl} mode), but cannot mark it as such without breaking CasC.
     */
    @DataBoundSetter
    public void setTunnel(String tunnel) {
        this.tunnel = Util.fixEmptyAndTrim(tunnel);
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
    @Restricted(NoExternalUse.class)
    public static /*almost final*/ Descriptor<ComputerLauncher> DESCRIPTOR;

    @NonNull
    @Restricted(NoExternalUse.class)
    public String getRemotingOptionsUnix(@NonNull Computer computer) {
        return getRemotingOptions(escapeUnix(computer.getName()));
    }

    @NonNull
    @Restricted(NoExternalUse.class)
    public String getRemotingOptionsWindows(@NonNull Computer computer) {
        return getRemotingOptions(escapeWindows(computer.getName()));
    }

    @Restricted(DoNotUse.class)
    public boolean isConfigured() {
        return webSocket || tunnel != null || workDirSettings.isConfigured();
    }

    private String getRemotingOptions(String computerName) {
        StringBuilder sb = new StringBuilder();
        sb.append("-name ");
        sb.append(computerName);
        sb.append(' ');
        sb.append("-webSocket ");
        if (tunnel != null) {
            sb.append(" -tunnel ");
            sb.append(tunnel);
            sb.append(' ');
        }
        return sb.toString();
    }

    /**
     * {@link Jenkins#checkGoodName(String)} saves us from most troublesome characters, but we still have to deal with
     * spaces and therefore with double quotes and backticks.
     */
    private static String escapeUnix(@NonNull String input) {
        if (!input.isEmpty() && input.chars().allMatch(Character::isLetterOrDigit)) {
            return input;
        }
        Escaper escaper =
                Escapers.builder().addEscape('"', "\\\"").addEscape('`', "\\`").build();
        return "\"" + escaper.escape(input) + "\"";
    }

    /**
     * {@link Jenkins#checkGoodName(String)} saves us from most troublesome characters, but we still have to deal with
     * spaces and therefore with double quotes.
     */
    private static String escapeWindows(@NonNull String input) {
        if (!input.isEmpty() && input.chars().allMatch(Character::isLetterOrDigit)) {
            return input;
        }
        Escaper escaper = Escapers.builder().addEscape('"', "\\\"").build();
        return "\"" + escaper.escape(input) + "\"";
    }

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
        if (!(computer instanceof SlaveComputer)) {
            return "";
        }
        return workDirSettings.toCommandLineString((SlaveComputer) computer);
    }

    @Extension @Symbol({"inbound", "jnlp"})
    public static class DescriptorImpl extends Descriptor<ComputerLauncher> {
        @SuppressFBWarnings(value = "ST_WRITE_TO_STATIC_FROM_INSTANCE_METHOD", justification = "for backward compatibility")
        public DescriptorImpl() {
            DESCRIPTOR = this;
        }

        @NonNull
        @Override
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

        @Restricted(DoNotUse.class)
        public boolean isTcpSupported() {
            return Jenkins.get().getTcpSlaveAgentListener() != null;
        }

        @Restricted(DoNotUse.class)
        public boolean isInstanceIdentityInstalled() {
            return InstanceIdentityProvider.RSA.getCertificate() != null && InstanceIdentityProvider.RSA.getPrivateKey() != null;
        }

        @Restricted(DoNotUse.class)
        public boolean isWebSocketSupported() {
            return WebSockets.isSupported();
        }

    }

    /**
     * Overrides the url that inbound TCP agents should connect to
     * as advertised in the agent.jnlp file. If not set, the default
     * behavior is unchanged and returns the root URL.
     *
     * This enables using a private address for inbound tcp agents,
     * separate from Jenkins root URL.
     *
     * @see <a href="https://issues.jenkins.io/browse/JENKINS-63222">JENKINS-63222</a>
     */
    @Restricted(NoExternalUse.class)
    public static String getInboundAgentUrl() {
        String url = SystemProperties.getString(CUSTOM_INBOUND_URL_PROPERTY);
        if (url == null || url.isEmpty()) {
            return Jenkins.get().getRootUrl();
        }
        return url;
    }
}
