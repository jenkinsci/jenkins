package hudson.node_monitors;

import hudson.model.Computer;
import hudson.model.Node;
import hudson.model.Descriptor;
import hudson.util.ClockDifference;
import org.kohsuke.stapler.StaplerRequest;

import java.io.IOException;

import net.sf.json.JSONObject;

/**
 * {@link NodeMonitor} that checks clock of {@link Node} to
 * detect out of sync clocks.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.123
 */
public class ClockMonitor extends NodeMonitor {
    public AbstractNodeMonitorDescriptor getDescriptor() {
        return DESCRIPTOR;
    }

    public ClockDifference getDifferenceFor(Computer c) {
        return DESCRIPTOR.get(c);
    }

    public static final AbstractNodeMonitorDescriptor<ClockDifference> DESCRIPTOR = new AbstractNodeMonitorDescriptor<ClockDifference>() {
        protected ClockDifference monitor(Computer c) throws IOException, InterruptedException {
            Node n = c.getNode();
            if(n==null) return null;
            return n.getClockDifference();
        }

        public String getDisplayName() {
            return Messages.ClockMonitor_displayName();
        }

        @Override
        public NodeMonitor newInstance(StaplerRequest req, JSONObject formData) throws FormException {
            return new ClockMonitor();
        }
    };

    static {
        LIST.add(DESCRIPTOR);
    }
}
