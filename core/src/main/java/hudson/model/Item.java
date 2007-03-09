package hudson.model;

import java.io.IOException;
import java.util.Collection;

/**
 * Basic configuration unit in Hudson.
 *
 * <p>
 * Every {@link Item} is hosted in an {@link ItemGroup} called "parent",
 * and some {@link Item}s are {@link ItemGroup}s. This form a tree
 * structure, which is rooted at {@link Hudson}.
 *
 * <p>
 * Unlike file systems, where a file can be moved from one directory
 * to another, {@link Item} inherently belongs to a single {@link ItemGroup}
 * and that relationship will not change.
 * Think of
 * <a href="http://images.google.com/images?q=Windows%20device%20manager">Windows device manager</a>
 * &mdash; an HDD always show up under 'Disk drives' and it can never be moved to another parent.
 *
 * Similarly, {@link ItemGroup} is not a generic container. Each subclass
 * of {@link ItemGroup} can usually only host a certain limited kinds of
 * {@link Item}s.
 *
 * <p>
 * {@link Item}s have unique {@link #getName() name}s that distinguish themselves
 * among their siblings uniquely. The names can be combined by '/' to form an
 * item full name, which uniquely identifies an {@link Item} inside the whole {@link Hudson}.
 *
 * @author Kohsuke Kawaguchi
 * @see Items
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
     * The name must be unique among other {@link Item}s that belong
     * to the same parent.
     *
     * <p>
     * This name is also used for directory name, so it cannot contain
     * any character that's not allowed on the file system. 
     */
    String getName();

    /**
     * Gets the full name of this item, like "abc/def/ghi".
     *
     * <p>
     * Full name consists of {@link #getName() name}s of {@link Item}s
     * that lead from the root {@link Hudson} to this {@link Item},
     * separated by '/'. This is the unique name that identifies this
     * {@link Item} inside the whole {@link Hudson}.
     *
     * @see Hudson#getItemByFullName(String,Class)
     */
    String getFullName();

    /**
     * Returns the URL of this item relative to the context root of the application.
     *
     * @see AbstractItem#getUrl() for how to implement this.
     *
     * @return
     *      URL that ends with '/'.
     */
    String getUrl();

    /**
     * Returns the URL of this item relative to the parent {@link ItemGroup}.
     * @see AbstractItem#getShortUrl() for how to implement this.
     *
     * @return
     *      URL that ends with '/'.
     */
    String getShortUrl();

    /**
     * Called right after when a {@link Item} is loaded from disk.
     * This is an opporunity to do a post load processing.
     *
     * @param name
     *      Name of the directory (not a path --- just the name portion) from
     *      which the configuration was loaded. This usually becomes the
     *      {@link #getName() name} of this item.
     */
    void onLoad(ItemGroup<? extends Item> parent, String name) throws IOException;

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
     * Use {@link Items#getConfigFile(Item)}
     * or {@link AbstractItem#getConfigFile()} to obtain the file
     * to save the data.
     */
    public void save() throws IOException;
}
