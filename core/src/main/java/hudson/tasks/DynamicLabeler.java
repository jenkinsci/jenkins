package hudson.tasks;

import hudson.ExtensionPoint;
import hudson.remoting.VirtualChannel;

import java.util.Collections;
import java.util.Set;

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
     * @param channel
     *      Connection that represents the node.
     * @return a set of labels.
     */
    public Set<String> findLabels(VirtualChannel channel) {
        return Collections.emptySet();
    }
}
