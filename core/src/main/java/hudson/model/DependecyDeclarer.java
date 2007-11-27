package hudson.model;

import hudson.tasks.BuildStep;
import hudson.tasks.Builder;
import hudson.tasks.Publisher;

/**
 * Marker interface for those {@link BuildStep}s that can participate
 * in the dependency graph computation process.
 *
 * <p>
 * {@link Publisher}s, {@link Builder}s, and {@link JobProperty}s
 * can additional implement this method to add additional edges
 * to the dependency graph computation.
 *
 * @author Nicolas Lalevee
 * @author Martin Ficker
 * @author Kohsuke Kawaguchi
 * @since 1.160
 */
public interface DependecyDeclarer {
    // I thought about whether this should extend BuildStep or not and decided not to.
    // so that this concept can be extended elsewhere, like maven projects and so on.

    /**
     * Invoked from {@link AbstractProject#buildDependencyGraph(DependencyGraph)}.
     *
     * @param owner
     *      The project that owns the publishers, builders, etc.
     *      This information is conceptually redundant, as those objects are
     *      only configured against the single owner, but this information is
     *      nevertheless passed in since often owner information is not recorded.
     *      Never null.
     * @param graph
     *      The dependency graph being built. Never null.
     */
    void buildDependencyGraph(AbstractProject owner, DependencyGraph graph);
}
