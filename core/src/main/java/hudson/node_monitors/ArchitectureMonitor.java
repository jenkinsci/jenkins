package hudson.node_monitors;

import hudson.model.Computer;
import hudson.remoting.Callable;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.StaplerRequest;

import java.io.IOException;

/**
 * Discovers the architecture of the system to display in the slave list page.
 *
 * @author Kohsuke Kawaguchi
 */
public class ArchitectureMonitor extends NodeMonitor {
    public AbstractNodeMonitorDescriptor getDescriptor() {
        return DESCRIPTOR;
    }

    public static final AbstractNodeMonitorDescriptor<String> DESCRIPTOR = new AbstractNodeMonitorDescriptor<String>() {
        protected String monitor(Computer c) throws IOException, InterruptedException {
            return c.getChannel().call(new GetArchTask());
        }

        public String getDisplayName() {
            return Messages.ArchitectureMonitor_displayName();
        }

        public NodeMonitor newInstance(StaplerRequest req, JSONObject formData) throws FormException {
            return new ArchitectureMonitor();
        }
    };

    /**
     * Obtains the string that represents the architecture.
     */
    private static class GetArchTask implements Callable<String,RuntimeException> {
        public String call() {
            String os = System.getProperty("os.name");
            String arch = System.getProperty("os.arch");
            return os+" ("+arch+')';
        }

        private static final long serialVersionUID = 1L;
    }

    static {
        LIST.add(DESCRIPTOR);
    }
}
