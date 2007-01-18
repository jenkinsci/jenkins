package hudson.model;

import java.util.Collection;

/**
 * Object that can be displayed in a {@link View}.
 *
 * @author Kohsuke Kawaguchi
 */
public interface ViewItem {
    /**
     * Gets all the jobs that this {@link ViewItem} contains as descendants.
     */
    Collection<Job> getAllJobs();

    /**
     * Gets the name of the view item.
     *
     * <p>
     * The name must be unique among all {@link ViewItem}s in this Hudson,
     * because we allow a single {@link ViewItem} to show up in multiple
     * {@link View}s.
     */
    String getName();
}
