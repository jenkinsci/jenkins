package hudson.model;

import hudson.model.Descriptor.FormException;
import hudson.node_monitors.NodeMonitor;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Serves as the top of {@link Computer}s in the URL hierarchy.
 * <p>
 * Getter methods are prefixed with '_' to avoid collision with computer names.
 * 
 * @author Kohsuke Kawaguchi
 */
public final class ComputerSet implements ModelObject {
    private static volatile List<NodeMonitor> monitors = Collections.emptyList();

    public ComputerSet() {
        if(monitors.isEmpty()) {
            // create all instances when requested for the first time.
            ArrayList<NodeMonitor> r = new ArrayList<NodeMonitor>();
            for (Descriptor<NodeMonitor> d : NodeMonitor.LIST)
                try {
                    r.add(d.newInstance(null));
                } catch (FormException e) {
                    // so far impossible. TODO: report
                }
            monitors = r;
        }
    }

    public String getDisplayName() {
        return "nodes";
    }

    public List<NodeMonitor> get_monitors() {
        return monitors;
    }

    public Computer[] get_all() {
        return Hudson.getInstance().getComputers();
    }

    public Computer getDynamic(String token, StaplerRequest req, StaplerResponse rsp) {
        return Hudson.getInstance().getComputer(token);
    }
}
