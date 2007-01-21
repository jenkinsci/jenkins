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
     * Project name.
     */
    protected /*final*/ transient String name;

    /**
     * Root directory for this view item on the file system.
     */
    protected transient File root;

    protected AbstractItem(String name) {
        doSetName(name);
    }

    public String getName() {
        return name;
    }

    public String getDisplayName() {
        return getName();
    }

    public File getRootDir() {
        return root;
    }

    /**
     * Just update {@link #name} and {@link #root}, since they are linked.
     */
    protected void doSetName(String name) {
        this.name = name;
        this.root = new File(new File(Hudson.getInstance().getRootDir(),"jobs"),name);
        this.root.mkdirs();
    }

    /**
     * Gets all the jobs that this {@link Item} contains as descendants.
     */
    public abstract Collection<? extends Job> getAllJobs();

    public final String getFullName() {
        String n = getParent().getFullName();
        if(n.length()==0)   return getName();
        else                return n+'/'+getName();
    }

    /**
     * Called right after when a {@link Item} is loaded from disk.
     * This is an opporunity to do a post load processing.
     */
    public void onLoad(String name) throws IOException {
        doSetName(name);
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
        return Items.getConfigFile(this);
    }
}
