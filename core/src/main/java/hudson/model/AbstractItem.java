package hudson.model;

import hudson.XmlFile;

import java.io.File;
import java.io.IOException;
import java.util.Collection;

/**
 * Partial default implementation of {@link Item}.
 *
 * @author Kohsuke Kawaguchi
 */
// Item doesn't necessarily have to be Actionable, but
// Java doesn't let multiple inheritance.
public abstract class AbstractItem extends Actionable implements Item {
    /**
     * Root directory for this view item on the file system.
     */
    protected transient File root;

    public File getRootDir() {
        return root;
    }

    /**
     * Gets all the jobs that this {@link Item} contains as descendants.
     */
    public abstract Collection<? extends Job> getAllJobs();

    /**
     * Gets the name of the view item.
     *
     * <p>
     * The name must be unique among all {@link Item}s in this Hudson,
     * because we allow a single {@link Item} to show up in multiple
     * {@link View}s.
     */
    public abstract String getName();

    /**
     * Called right after when a {@link Item} is loaded from disk.
     * This is an opporunity to do a post load processing.
     */
    public void onLoad(String name) throws IOException {
    }

    /**
     * When a {@link Item} is copied from existing one,
     * the files are first copied on the file system,
     * then it will be loaded, then this method will be invoked
     * to perform any implementation-specific work.
     */
    public void onCopiedFrom(Item src) {
    }

    public final String getUrl() {
        return getParent().getUrl()+getParent().getUrlChildPrefix()+'/'+getName()+'/';
    }

    /**
     * Save the settings to a file.
     */
    public synchronized void save() throws IOException {
        getConfigFile().write(this);
    }

    protected final XmlFile getConfigFile() {
        return ItemLoader.getConfigFile(this);
    }
}
