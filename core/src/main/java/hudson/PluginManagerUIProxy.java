package hudson;

import jenkins.model.Jenkins;

/**
 * Extension point for selectively overriding parts of the {@link PluginManager} UI/methods
 * Anything registered with an @extension can override Jelly and define custom views.
 * It is also possible to add/modify API calls coming via Stapler, but this requires caution.
 *
 * @author Sam Van Oort
 * @since 1.625
 */
public abstract class PluginManagerUIProxy implements ExtensionPoint {

    public PluginManager getManager() {
        Jenkins jenkins = Jenkins.getInstance();
        return (jenkins != null) ? jenkins.getPluginManager() : null;
    }

    public static ExtensionList<PluginManagerUIProxy> all() {
        Jenkins jenkins = Jenkins.getInstance();
        if (jenkins != null) {
            return jenkins.getExtensionList(PluginManagerUIProxy.class);
        } else { // Null-safe
            return ExtensionList.create(jenkins, PluginManagerUIProxy.class);
        }
    }
}
