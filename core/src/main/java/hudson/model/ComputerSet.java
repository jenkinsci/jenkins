package hudson.model;

import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import hudson.node_monitors.NodeMonitor;

import java.util.List;

/**
 * Serves as the top of {@link Computer}s in the URL hierarchy.
 * <p>
 * Getter methods are prefixed with '_' to avoid collision with computer names.
 * 
 * @author Kohsuke Kawaguchi
 */
public final class ComputerSet implements ModelObject {
    public String getDisplayName() {
        return "nodes";
    }

    public List<NodeMonitor> get_monitors() {
        return null;
    }

    public Computer[] get_all() {
        return Hudson.getInstance().getComputers();
    }

    public Computer getDynamic(String token, StaplerRequest req, StaplerResponse rsp) {
        return Hudson.getInstance().getComputer(token);
    }
}
