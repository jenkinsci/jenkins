package hudson.model;

import java.io.IOException;
import java.util.Collection;

/**
 * Object that can be displayed in a {@link ListView}.
 *
 * {@link Item}s are allowed to show up in multiple {@link View}s,
 * so they need to have unique names among all {@link Item}s.
 * This uniqueness is also used for allocating file system storage
 * for each {@link Item}.
 *
 * @author Kohsuke Kawaguchi
 * @see ItemLoader
 */
public interface Item extends PersistenceRoot {
    /**
     * Gets the parent that contains this item.
     */
    ItemGroup<? extends Item> getParent();

    /**
     * Gets all the jobs that this {@link Item} contains as descendants.
     */
    abstract Collection<? extends Job> getAllJobs();

    /**
     * Gets the name of the item.
     *
     * <p>
     * The name must be unique among all {@link Item}s in this Hudson,
     * because we allow a single {@link Item} to show up in multiple
     * {@link View}s.
     */
    String getName();

    /**
     * Returns the URL of this project relative to the context root of the application.
     *
     * @see AbstractItem#getUrl() for how to implement this
     */
    String getUrl();

    /**
     * Called right after when a {@link Item} is loaded from disk.
     * This is an opporunity to do a post load processing.
     */
    void onLoad(String name) throws IOException;

    /**
     * When a {@link Item} is copied from existing one,
     * the files are first copied on the file system,
     * then it will be loaded, then this method will be invoked
     * to perform any implementation-specific work.
     */
    void onCopiedFrom(Item src);

    /**
     * Save the settings to a file.
     *
     * Use {@link ItemLoader#getConfigFile(Item)}
     * or {@link AbstractItem#getConfigFile()} to obtain the file
     * to save the data.
     */
    public void save() throws IOException;
}
