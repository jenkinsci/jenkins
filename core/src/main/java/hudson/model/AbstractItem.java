package hudson.model;

import hudson.XmlFile;
import hudson.Util;
import hudson.Functions;
import hudson.security.AccessControlled;
import hudson.security.Permission;
import hudson.security.ACL;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;

import java.io.File;
import java.io.IOException;
import java.util.Collection;

import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.HttpDeletable;

import javax.servlet.ServletException;
import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST;

/**
 * Partial default implementation of {@link Item}.
 *
 * @author Kohsuke Kawaguchi
 */
// Item doesn't necessarily have to be Actionable, but
// Java doesn't let multiple inheritance.
@ExportedBean
public abstract class AbstractItem extends Actionable implements Item, HttpDeletable, AccessControlled {
    /**
     * Project name.
     */
    protected /*final*/ transient String name;

    /**
     * Project description. Can be HTML.
     */
    protected volatile String description;

    private transient ItemGroup parent;

    protected AbstractItem(ItemGroup parent, String name) {
        this.parent = parent;
        doSetName(name);
    }

    @Exported(visibility=999)
    public String getName() {
        return name;
    }

    @Exported
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
    @Exported
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

    public final String getFullDisplayName() {
        String n = getParent().getFullDisplayName();
        if(n.length()==0)   return getDisplayName();
        else                return n+" \u00BB "+getDisplayName();
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
        // try to stick to the current view if possible
        StaplerRequest req = Stapler.getCurrentRequest();
        if (req != null) {
            String seed = Functions.getNearestAncestorUrl(req,this);
            if(seed!=null) {
                // trim off the context path portion and leading '/', but add trailing '/'
                return seed.substring(req.getContextPath().length()+1)+'/';
            }
        }

        // otherwise compute the path normally
        return getParent().getUrl()+getShortUrl();
    }

    public String getShortUrl() {
        return getParent().getUrlChildPrefix()+'/'+getName()+'/';
    }

    public String getSearchUrl() {
        return getShortUrl();
    }

    @Exported(visibility=999,name="url")
    public final String getAbsoluteUrl() {
        StaplerRequest request = Stapler.getCurrentRequest();
        if(request==null)
            throw new IllegalStateException("Not processing a HTTP request");
        return Util.encode(request.getRootPath()+'/'+getUrl());
    }

    /**
     * Remote API access.
     */
    public final Api getApi() {
        return new Api(this);
    }

    /**
     * Returns the {@link ACL} for this object.
     */
    public ACL getACL() {
        return Hudson.getInstance().getAuthorizationStrategy().getACL(this);
    }

    /**
     * Short for {@code getACL().checkPermission(p)}
     */
    public void checkPermission(Permission p) {
        getACL().checkPermission(p);
    }

    /**
     * Short for {@code getACL().hasPermission(p)}
     */
    public boolean hasPermission(Permission p) {
        return getACL().hasPermission(p);
    }

    /**
     * Save the settings to a file.
     */
    public synchronized void save() throws IOException {
        getConfigFile().write(this);
    }

    public final XmlFile getConfigFile() {
        return Items.getConfigFile(this);
    }

    /**
     * Accepts the new description.
     */
    public synchronized void doSubmitDescription( StaplerRequest req, StaplerResponse rsp ) throws IOException, ServletException {
        checkPermission(CONFIGURE);

        req.setCharacterEncoding("UTF-8");
        setDescription(req.getParameter("description"));
        save();
        rsp.sendRedirect(".");  // go to the top page
    }

    /**
     * Deletes this item.
     */
    public void doDoDelete( StaplerRequest req, StaplerResponse rsp ) throws IOException, ServletException {
        checkPermission(DELETE);
        if(!"POST".equals(req.getMethod())) {
            rsp.setStatus(SC_BAD_REQUEST);
            sendError("Delete request has to be POST",req,rsp);
            return;
        }
        delete();
        rsp.sendRedirect2(req.getContextPath()+"/"+getParent().getUrl());
    }

    public void delete( StaplerRequest req, StaplerResponse rsp ) throws IOException, ServletException {
        doDoDelete(req,rsp);
    }

    /**
     * Deletes this item.
     */
    public synchronized void delete() throws IOException {
        performDelete();

        if(this instanceof TopLevelItem)
            Hudson.getInstance().deleteJob((TopLevelItem)this);

        Hudson.getInstance().rebuildDependencyGraph();
    }

    /**
     * Does the real job of deleting the item.
     */
    protected void performDelete() throws IOException {
        Util.deleteRecursive(getRootDir());
    }

    public String toString() {
        return super.toString()+'['+getFullName()+']';
    }
}
