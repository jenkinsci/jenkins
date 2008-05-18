package hudson.slaves;

import hudson.model.Descriptor;
import hudson.util.StreamTaskListener;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * {@link ComputerStartMethod} via JNLP.
 *
 * @author Stephen Connolly
 * @author Kohsuke Kawaguchi
*/
public class JNLPStartMethod extends ComputerStartMethod {
    @DataBoundConstructor
    public JNLPStartMethod() {
    }

    @Override
    public boolean isLaunchSupported() {
        return false;
    }

    public void launch(SlaveComputer computer, StreamTaskListener listener) {
        // do nothing as we cannot self start
    }

    public Descriptor<ComputerStartMethod> getDescriptor() {
        return DESCRIPTOR;
    }

    public static final Descriptor<ComputerStartMethod> DESCRIPTOR = new Descriptor<ComputerStartMethod>(JNLPStartMethod.class) {
        public String getDisplayName() {
            return "Launch slave agents via JNLP";
        }
    };
}
