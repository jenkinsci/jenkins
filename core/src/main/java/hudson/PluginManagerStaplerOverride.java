package hudson;


import javax.annotation.Nonnull;

/**
 * Extension point for selectively overriding parts of the {@link PluginManager} views
 * Anything extending this and registered with an @Extension can replace existing views and define new views.
 *
 * It is also possible to add/modify API calls coming via Stapler, but this requires caution.
 *
 * In both cases, this is simply done by defining a resource or method that matches the existing one
 *
 * @author Sam Van Oort
 * @since 1.627
 */
public abstract class PluginManagerStaplerOverride implements ExtensionPoint {

    /**
     * Return all implementations of this extension point
     * @return All implementations of this extension point
     */
    public static @Nonnull ExtensionList<PluginManagerStaplerOverride> all() {
        return ExtensionList.lookup(PluginManagerStaplerOverride.class);
    }
}
