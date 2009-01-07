package hudson.slaves;

import hudson.model.Descriptor;
import hudson.model.Slave;
import hudson.model.Node;
import hudson.util.DescriptorList;

/**
 * {@link Descriptor} for {@link Slave}.
 *
 * <h2>Views</h2>
 * <p>
 * This object needs to have <tt>newInstanceDetail.jelly</tt> view, which shows up in
 * <tt>http://server/hudson/computers/new</tt> page as an explanation of this job type.
 *
 * <h2>Other Implementation Notes</h2>
 *
 * @author Kohsuke Kawaguchi
 */
public abstract class NodeDescriptor extends Descriptor<Node> {
    protected NodeDescriptor(Class<? extends Node> clazz) {
        super(clazz);
    }

    public final String newInstanceDetailPage() {
        return '/'+clazz.getName().replace('.','/').replace('$','/')+"/newInstanceDetail.jelly";
    }

    @Override
    public String getConfigPage() {
        return getViewPage(clazz, "configure-entries.jelly");
    }

    /**
     * All the registered instances.
     */
    public static final DescriptorList<Node> ALL = new DescriptorList<Node>();

    static {
        ALL.load(DumbSlave.class);
    }
}
