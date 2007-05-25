package hudson.tasks;

import hudson.model.Describable;
import hudson.model.Node;
import hudson.model.Descriptor;
import hudson.ExtensionPoint;
import hudson.remoting.VirtualChannel;

import java.util.*;

/**
 * Created by IntelliJ IDEA.
 *
 * @author connollys
 * @since 25-May-2007 14:30:15
 */
public abstract class DynamicLabeler implements LabelFinder, ExtensionPoint {

    /**
     * Find the labels that the node supports.
     *
     * @param node The Node
     * @return a set of labels.
     */
    public Set<String> findLabels(VirtualChannel channel) {
        return new HashSet<String>();
    }
}
