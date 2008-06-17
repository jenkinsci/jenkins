package hudson.slaves;

import hudson.model.Descriptor;
import hudson.util.StreamTaskListener;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * {@link ComputerLauncher} via JNLP.
 *
 * @author Stephen Connolly
 * @author Kohsuke Kawaguchi
*/
public class JNLPLauncher extends ComputerLauncher {
    @DataBoundConstructor
    public JNLPLauncher() {
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

    public static final Descriptor<ComputerLauncher> DESCRIPTOR = new Descriptor<ComputerLauncher>(JNLPLauncher.class) {
        public String getDisplayName() {
            return Messages.JNLPLauncher.displayName();
        }
    };

    static {
        LIST.add(DESCRIPTOR);
    }
}
