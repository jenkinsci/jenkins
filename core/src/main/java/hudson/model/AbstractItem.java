package hudson.model;

import hudson.XmlFile;
import hudson.Util;

import java.io.File;
import java.io.IOException;
import java.util.Collection;

import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import javax.servlet.ServletException;

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
     * Project description. Can be HTML.
     */
    protected String description;

    private transient ItemGroup parent;

    protected AbstractItem(ItemGroup parent, String name) {
        this.parent = parent;
        doSetName(name);
    }

    public String getName() {
        return name;
    }

    public String getDisplayName() {
        return getName();
    }

    public File getRootDir() {
        return parent.getRootDirFor(this);
    }

    public ItemGroup getParent() {
        assert parent!=null;
        return parent;
    }

    /**
     * Gets the project description HTML.
     */
    public String getDescription() {
        return description;
    }

    /**
     * Sets the project description HTML.
     */
    public void setDescription(String description) {
        this.description = description;
    }

    /**
     * Just update {@link #name} without performing the rename operation,
     * which would involve copying files and etc.
     */
    protected void doSetName(String name) {
        this.name = name;
        getRootDir().mkdirs();
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
    public void onLoad(ItemGroup<? extends Item> parent, String name) throws IOException {
        this.parent = parent;
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
        return getParent().getUrl()+getShortUrl();
    }

    public String getShortUrl() {
        return getParent().getUrlChildPrefix()+'/'+getName()+'/';
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

    /**
     * Accepts the new description.
     */
    public synchronized void doSubmitDescription( StaplerRequest req, StaplerResponse rsp ) throws IOException, ServletException {
        if(!Hudson.adminCheck(req,rsp))
            return;

        req.setCharacterEncoding("UTF-8");
        setDescription(req.getParameter("description"));
        save();
        rsp.sendRedirect(".");  // go to the top page
    }

    /**
     * Deletes this item.
     */
    public synchronized void doDoDelete( StaplerRequest req, StaplerResponse rsp ) throws IOException {
        if(!Hudson.adminCheck(req,rsp))
            return;
        Util.deleteRecursive(getRootDir());

        if(this instanceof TopLevelItem)
            Hudson.getInstance().deleteJob((TopLevelItem)this);
        rsp.sendRedirect2(req.getContextPath()+"/");
    }
}
