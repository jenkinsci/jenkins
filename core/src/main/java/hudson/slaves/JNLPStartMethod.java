package hudson.slaves;

import hudson.model.Descriptor;
import hudson.model.Slave;
import hudson.util.StreamTaskListener;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * {@link SlaveStartMethod} via JNLP.
 *
 * @author Stephen Connolly
 * @author Kohsuke Kawaguchi
*/
public class JNLPStartMethod extends SlaveStartMethod {
    @DataBoundConstructor
    public JNLPStartMethod() {
    }

    @Override
    public boolean isLaunchSupported() {
        return false;
    }

    public void launch(Slave.ComputerImpl computer, StreamTaskListener listener) {
        // do nothing as we cannot self start
    }

    public Descriptor<SlaveStartMethod> getDescriptor() {
        return DESCRIPTOR;
    }

    public static final Descriptor<SlaveStartMethod> DESCRIPTOR = new Descriptor<SlaveStartMethod>(JNLPStartMethod.class) {
        public String getDisplayName() {
            return "Launch slave agents via JNLP";
        }
    };
}
