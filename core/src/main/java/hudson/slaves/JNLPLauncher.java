package hudson.slaves;

import hudson.model.Descriptor;
import hudson.util.StreamTaskListener;
import hudson.Util;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * {@link ComputerLauncher} via JNLP.
 *
 * @author Stephen Connolly
 * @author Kohsuke Kawaguchi
*/
public class JNLPLauncher extends ComputerLauncher {
    /**
     * If the slave needs to tunnel the connection to the master,
     * specify the "host:port" here. This can include the special
     * syntax "host:" and ":port" to indicate the default host/port
     * shall be used.
     *
     * <p>
     * Null if no tunneling is necessary.
     *
     * @since 1.250
     */
    public final String tunnel;

    @DataBoundConstructor
    public JNLPLauncher(String tunnel) {
        this.tunnel = Util.fixEmptyAndTrim(tunnel);
    }

    public JNLPLauncher() {
        this(null);
    }

    @Override
    public boolean isLaunchSupported() {
        return false;
    }

    public void launch(SlaveComputer computer, StreamTaskListener listener) {
        // do nothing as we cannot self start
    }

    public Descriptor<ComputerLauncher> getDescriptor() {
        return DESCRIPTOR;
    }

    public static final Descriptor<ComputerLauncher> DESCRIPTOR = new Descriptor<ComputerLauncher>() {
        public String getDisplayName() {
            return Messages.JNLPLauncher_displayName();
        }
    };

    static {
        LIST.add(DESCRIPTOR);
    }
}
