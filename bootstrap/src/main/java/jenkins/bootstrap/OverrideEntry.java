package jenkins.bootstrap;

/**
 * Record of one component overriden by another.
 *
 * @author Kohsuke Kawaguchi
 */
public class OverrideEntry {
    /**
     * This is the core component that got overridden.
     */
    private final Dependency from;
    /**
     * This is the component that overrode {@link #from}
     */
    private final Dependency to;
    /**
     * Plugin that provided the override.
     */
    private final Plugin plugin;

    public OverrideEntry(Dependency from, Dependency to, Plugin plugin) {
        this.from = from;
        this.to = to;
        this.plugin = plugin;
    }

    public Dependency getFrom() {
        return from;
    }

    public Dependency getTo() {
        return to;
    }

    public String getPluginShortName() {
        return plugin.getShortName();
    }
}
