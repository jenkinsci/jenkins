package hudson.slaves;

import hudson.model.Slave;
import hudson.model.Descriptor;
import hudson.util.StreamTaskListener;
import org.kohsuke.stapler.StaplerRequest;
import net.sf.json.JSONObject;

/**
 * {@link SlaveStartMethod} via JNLP.
 *
 * @author Stephen Connolly
 * @author Kohsuke Kawaguchi
*/
public class JNLPStartMethod extends SlaveStartMethod {

    @Override
    public boolean isLaunchSupported() {
        return false;
    }

    public void launch(Slave.ComputerImpl computer, StreamTaskListener listener) {
        // do nothing as we cannot self start
    }

    //@DataBoundConstructor
    public JNLPStartMethod() {
    }

    public Descriptor<SlaveStartMethod> getDescriptor() {
        return DESCRIPTOR;
    }

    public static final Descriptor<SlaveStartMethod> DESCRIPTOR = new Descriptor<SlaveStartMethod>(JNLPStartMethod.class) {
        public String getDisplayName() {
            return "Launch slave agents via JNLP";
        }

        public SlaveStartMethod newInstance(StaplerRequest req, JSONObject formData) throws FormException {
            return new JNLPStartMethod();
        }
    };
}
