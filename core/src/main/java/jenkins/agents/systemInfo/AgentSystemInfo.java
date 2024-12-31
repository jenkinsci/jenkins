package jenkins.agents.systemInfo;

import hudson.ExtensionList;
import hudson.ExtensionPoint;
import hudson.model.Computer;
import hudson.security.Permission;

/**
 * Extension point that contributes to the system information page of {@link Computer}.
 *
 * <h2>Views</h2>
 * Subtypes must have {@code systemInfo.groovy/.jelly} view.
 * This view will have the "it" variable that refers to {@link Computer} object, and "instance" variable
 * that refers to {@link AgentSystemInfo} object.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.559
 */
public abstract class AgentSystemInfo implements ExtensionPoint {
    /**
     * Human readable name of this statistics.
     */
    public abstract String getDisplayName();

    public static ExtensionList<AgentSystemInfo> all() {
        return ExtensionList.lookup(AgentSystemInfo.class);
    }

    /**
     * Returns the permission required for user to see this system info extension on the "System Information" page for the Agent
     *
     * By default {@link Computer#CONNECT}, but {@link Computer#EXTENDED_READ} is also supported.
     *
     * @return the permission required for the extension to be shown on "System Information".
     */
    public Permission getRequiredPermission() {
        return Computer.CONNECT;
    }
}
