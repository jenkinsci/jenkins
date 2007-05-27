package hudson.tasks;

import hudson.model.Label;
import hudson.model.Node;
import hudson.model.Descriptor;
import hudson.remoting.VirtualChannel;
import hudson.tasks.labelers.OSLabeler;

import java.util.Set;
import java.util.List;
import java.util.ArrayList;

/**
 * Support for autoconfiguration of nodes.
 *
 * @author Stephen Connolly
 */
public interface LabelFinder {

    public static final List<DynamicLabeler> LABELERS = new ArrayList<DynamicLabeler>()/*{
        // Taking adding default DynamicLabelers out of main trunk
        {
            add(OSLabeler.INSTANCE);
        }
    }*/;

    /**
     * Find the labels that the node supports.
     * @param node The Node
     * @return a set of labels.
     */
    Set<String> findLabels(VirtualChannel channel);
}
