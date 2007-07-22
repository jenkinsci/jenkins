package hudson.model;

/**
 * Activity that requires certain resources for its execution.
 *
 * @author Kohsuke Kawaguchi
 */
public interface ResourceActivity {
    /**
     * Gets the list of {@link Resource}s that this task requires.
     * Used to make sure no two conflicting tasks run concurrently.
     * <p>
     * This method must always return the {@link ResourceList}
     * that contains the exact same set of {@link Resource}s.
     */
    ResourceList getResourceList();
    
    /**
     * Used for rendering HTML.
     */
    String getDisplayName();
}
