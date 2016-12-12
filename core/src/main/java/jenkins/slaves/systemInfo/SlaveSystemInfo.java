package jenkins.slaves.systemInfo;

import hudson.ExtensionList;
import hudson.ExtensionPoint;
import hudson.model.Computer;

/**
 * Extension point that contributes to the system information page of {@link Computer}.
 *
 * <h2>Views</h2>
 * Subtypes must have <tt>systemInfo.groovy/.jelly</tt> view.
 * This view will have the "it" variable that refers to {@link Computer} object, and "instance" variable
 * that refers to {@link SlaveSystemInfo} object.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.559
 */
public abstract class SlaveSystemInfo implements ExtensionPoint {
    /**
     * Human readable name of this statistics.
     */
    public abstract String getDisplayName();

    public static ExtensionList<SlaveSystemInfo> all() {
        return ExtensionList.lookup(SlaveSystemInfo.class);
    }
}
