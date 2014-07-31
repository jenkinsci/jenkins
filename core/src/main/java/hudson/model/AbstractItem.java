/*
 * The MIT License
 * 
 * Copyright (c) 2004-2011, Sun Microsystems, Inc., Kohsuke Kawaguchi,
 * Daniel Dyer, Tom Huybrechts, Yahoo!, Inc.
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package hudson.model;

import com.infradna.tool.bridge_method_injector.WithBridgeMethods;
import hudson.AbortException;
import hudson.XmlFile;
import hudson.Util;
import hudson.Functions;
import hudson.BulkChange;
import hudson.cli.declarative.CLIMethod;
import hudson.cli.declarative.CLIResolver;
import hudson.model.listeners.ItemListener;
import hudson.model.listeners.SaveableListener;
import hudson.security.AccessControlled;
import hudson.security.Permission;
import hudson.security.ACL;
import hudson.util.AlternativeUiTextProvider;
import hudson.util.AlternativeUiTextProvider.Message;
import hudson.util.AtomicFileWriter;
import hudson.util.IOException2;
import hudson.util.IOUtils;
import jenkins.model.Jenkins;
import org.apache.tools.ant.taskdefs.Copy;
import org.apache.tools.ant.types.FileSet;
import org.kohsuke.stapler.WebMethod;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;

import java.io.File;
import java.io.IOException;
import java.util.Collection;

import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.HttpDeletable;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.stapler.interceptor.RequirePOST;

import javax.servlet.ServletException;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST;

/**
 * Partial default implementation of {@link Item}.
 *
 * @author Kohsuke Kawaguchi
 */
// Item doesn't necessarily have to be Actionable, but
// Java doesn't let multiple inheritance.
@ExportedBean
public abstract class AbstractItem extends Actionable implements Item, HttpDeletable, AccessControlled, DescriptorByNameOwner {
    /**
     * Project name.
     */
    protected /*final*/ transient String name;

    /**
     * Project description. Can be HTML.
     */
    protected volatile String description;

    private transient ItemGroup parent;
    
    protected String displayName;

    protected AbstractItem(ItemGroup parent, String name) {
        this.parent = parent;
        doSetName(name);
    }

    public void onCreatedFromScratch() {
        // noop
    }

    @Exported(visibility=999)
    public String getName() {
        return name;
    }

    /**
     * Get the term used in the UI to represent this kind of
     * {@link Item}. Must start with a capital letter.
     */
    public String getPronoun() {
        return AlternativeUiTextProvider.get(PRONOUN, this, Messages.AbstractItem_Pronoun());
    }

    @Exported
    /**
     * @return The display name of this object, or if it is not set, the name
     * of the object.
     */
    public String getDisplayName() {
        if(null!=displayName) {
            return displayName;
        }
        // if the displayName is not set, then return the name as we use to do
        return getName();
    }
    
    @Exported
    /**
     * This is intended to be used by the Job configuration pages where
     * we want to return null if the display name is not set.
     * @return The display name of this object or null if the display name is not
     * set
     */
    public String getDisplayNameOrNull() {
        return displayName;
    }
    
    /**
     * This method exists so that the Job configuration pages can use 
     * getDisplayNameOrNull so that nothing is shown in the display name text
     * box if the display name is not set.
     * @param displayName
     * @throws IOException
     */
    public void setDisplayNameOrNull(String displayName) throws IOException {
        setDisplayName(displayName);
    }
    
    public void setDisplayName(String displayName) throws IOException {
        this.displayName = Util.fixEmpty(displayName);
        save();
    }
             
    public File getRootDir() {
        return parent.getRootDirFor(this);
    }

    /**
     * This bridge method is to maintain binary compatibility with {@link TopLevelItem#getParent()}.
     */
    @WithBridgeMethods(value=Jenkins.class,castRequired=true)
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
    public void setDescription(String description) throws IOException {
        this.description = description;
        save();
        ItemListener.fireOnUpdated(this);
    }

    /**
     * Just update {@link #name} without performing the rename operation,
     * which would involve copying files and etc.
     */
    protected void doSetName(String name) {
        this.name = name;
    }

    /**
     * Renames this item.
     * Not all the Items need to support this operation, but if you decide to do so,
     * you can use this method.
     */
    protected void renameTo(String newName) throws IOException {
        // always synchronize from bigger objects first
        final ItemGroup parent = getParent();
        synchronized (parent) {
            synchronized (this) {
                // sanity check
                if (newName == null)
                    throw new IllegalArgumentException("New name is not given");

                // noop?
                if (this.name.equals(newName))
                    return;

                Item existing = parent.getItem(newName);
                if (existing != null && existing!=this)
                    // the look up is case insensitive, so we need "existing!=this"
                    // to allow people to rename "Foo" to "foo", for example.
                    // see http://www.nabble.com/error-on-renaming-project-tt18061629.html
                    throw new IllegalArgumentException("Job " + newName
                            + " already exists");

                String oldName = this.name;
                File oldRoot = this.getRootDir();

                doSetName(newName);
                File newRoot = this.getRootDir();

                boolean success = false;

                try {// rename data files
                    boolean interrupted = false;
                    boolean renamed = false;

                    // try to rename the job directory.
                    // this may fail on Windows due to some other processes
                    // accessing a file.
                    // so retry few times before we fall back to copy.
                    for (int retry = 0; retry < 5; retry++) {
                        if (oldRoot.renameTo(newRoot)) {
                            renamed = true;
                            break; // succeeded
                        }
                        try {
                            Thread.sleep(500);
                        } catch (InterruptedException e) {
                            // process the interruption later
                            interrupted = true;
                        }
                    }

                    if (interrupted)
                        Thread.currentThread().interrupt();

                    if (!renamed) {
                        // failed to rename. it must be that some lengthy
                        // process is going on
                        // to prevent a rename operation. So do a copy. Ideally
                        // we'd like to
                        // later delete the old copy, but we can't reliably do
                        // so, as before the VM
                        // shuts down there might be a new job created under the
                        // old name.
                        Copy cp = new Copy();
                        cp.setProject(new org.apache.tools.ant.Project());
                        cp.setTodir(newRoot);
                        FileSet src = new FileSet();
                        src.setDir(getRootDir());
                        cp.addFileset(src);
                        cp.setOverwrite(true);
                        cp.setPreserveLastModified(true);
                        cp.setFailOnError(false); // keep going even if
                                                    // there's an error
                        cp.execute();

                        // try to delete as much as possible
                        try {
                            Util.deleteRecursive(oldRoot);
                        } catch (IOException e) {
                            // but ignore the error, since we expect that
                            e.printStackTrace();
                        }
                    }

                    success = true;
                } finally {
                    // if failed, back out the rename.
                    if (!success)
                        doSetName(oldName);
                }

                callOnRenamed(newName, parent, oldName);

                for (ItemListener l : ItemListener.all())
                    l.onRenamed(this, oldName, newName);
            }
        }
    }

    /**
     * A pointless function to work around what appears to be a HotSpot problem. See JENKINS-5756 and bug 6933067
     * on BugParade for more details.
     */
    private void callOnRenamed(String newName, ItemGroup parent, String oldName) throws IOException {
        try {
            parent.onRenamed(this, oldName, newName);
        } catch (AbstractMethodError _) {
            // ignore
        }
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
     * Gets the display name of the current item relative to the given group.
     *
     * @since 1.515
     * @param p the ItemGroup used as point of reference for the item
     * @return
     *      String like "foo » bar"
     */
    public String getRelativeDisplayNameFrom(ItemGroup p) {
        return Functions.getRelativeDisplayNameFrom(this, p);
    }
    
    /**
     * This method only exists to disambiguate {@link #getRelativeNameFrom(ItemGroup)} and {@link #getRelativeNameFrom(Item)}
     * @since 1.512
     * @see #getRelativeNameFrom(ItemGroup)
     */
    public String getRelativeNameFromGroup(ItemGroup p) {
        return getRelativeNameFrom(p);
    }

    /**
     * @param p
     *  The ItemGroup instance used as context to evaluate the relative name of this AbstractItem
     * @return
     *  The name of the current item, relative to p.
     *  Nested ItemGroups are separated by / character.
     */
    public String getRelativeNameFrom(ItemGroup p) {
        return Functions.getRelativeNameFrom(this, p);
    }

    public String getRelativeNameFrom(Item item) {
        return getRelativeNameFrom(item.getParent());
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
     *
     * <p>
     * 
     *
     * @param src
     *      Item from which it's copied from. The same type as {@code this}. Never null.
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
        String prefix = getParent().getUrlChildPrefix();
        String subdir = Util.rawEncode(getName());
        return prefix.equals(".") ? subdir + '/' : prefix + '/' + subdir + '/';
    }

    public String getSearchUrl() {
        return getShortUrl();
    }

    @Exported(visibility=999,name="url")
    public final String getAbsoluteUrl() {
        String r = Jenkins.getInstance().getRootUrl();
        if(r==null)
            throw new IllegalStateException("Root URL isn't configured yet. Cannot compute absolute URL.");
        return Util.encode(r+getUrl());
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
        return Jenkins.getInstance().getAuthorizationStrategy().getACL(this);
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
        if(BulkChange.contains(this))   return;
        getConfigFile().write(this);
        SaveableListener.fireOnChange(this, getConfigFile());
    }

    public final XmlFile getConfigFile() {
        return Items.getConfigFile(this);
    }

    public Descriptor getDescriptorByName(String className) {
        return Jenkins.getInstance().getDescriptorByName(className);
    }

    /**
     * Accepts the new description.
     */
    public synchronized void doSubmitDescription( StaplerRequest req, StaplerResponse rsp ) throws IOException, ServletException {
        checkPermission(CONFIGURE);

        setDescription(req.getParameter("description"));
        rsp.sendRedirect(".");  // go to the top page
    }

    /**
     * Deletes this item.
     * Note on the funny name: for reasons of historical compatibility, this URL is {@code /doDelete}
     * since it predates {@code <l:confirmationLink>}. {@code /delete} goes to a Jelly page
     * which should now be unused by core but is left in case plugins are still using it.
     */
    @CLIMethod(name="delete-job")
    @RequirePOST
    public void doDoDelete( StaplerRequest req, StaplerResponse rsp ) throws IOException, ServletException, InterruptedException {
        delete();
        if (rsp != null) // null for CLI
            rsp.sendRedirect2(req.getContextPath()+"/"+getParent().getUrl());
    }

    public void delete( StaplerRequest req, StaplerResponse rsp ) throws IOException, ServletException {
        try {
            doDoDelete(req,rsp);
        } catch (InterruptedException e) {
            // TODO: allow this in Stapler
            throw new ServletException(e);
        }
    }

    /**
     * Deletes this item.
     *
     * <p>
     * Any exception indicates the deletion has failed, but {@link AbortException} would prevent the caller
     * from showing the stack trace. This
     */
    public synchronized void delete() throws IOException, InterruptedException {
        checkPermission(DELETE);
        performDelete();

        try {
            invokeOnDeleted();
        } catch (AbstractMethodError e) {
            // ignore
        }

        Jenkins.getInstance().rebuildDependencyGraphAsync();
    }

    /**
     * A pointless function to work around what appears to be a HotSpot problem. See JENKINS-5756 and bug 6933067
     * on BugParade for more details.
     */
    private void invokeOnDeleted() throws IOException {
        getParent().onDeleted(this);
    }

    /**
     * Does the real job of deleting the item.
     */
    protected void performDelete() throws IOException, InterruptedException {
        getConfigFile().delete();
        Util.deleteRecursive(getRootDir());
    }

    /**
     * Accepts <tt>config.xml</tt> submission, as well as serve it.
     */
    @WebMethod(name = "config.xml")
    public void doConfigDotXml(StaplerRequest req, StaplerResponse rsp)
            throws IOException {
        if (req.getMethod().equals("GET")) {
            // read
            checkPermission(EXTENDED_READ);
            rsp.setContentType("application/xml");
            IOUtils.copy(getConfigFile().getFile(),rsp.getOutputStream());
            return;
        }
        if (req.getMethod().equals("POST")) {
            // submission
            updateByXml((Source)new StreamSource(req.getReader()));
            return;
        }

        // huh?
        rsp.sendError(SC_BAD_REQUEST);
    }

    /**
     * @deprecated as of 1.473
     *      Use {@link #updateByXml(Source)}
     */
    public void updateByXml(StreamSource source) throws IOException {
        updateByXml((Source)source);
    }

    /**
     * Updates Job by its XML definition.
     */
    public void updateByXml(Source source) throws IOException {
        checkPermission(CONFIGURE);
        XmlFile configXmlFile = getConfigFile();
        AtomicFileWriter out = new AtomicFileWriter(configXmlFile.getFile());
        try {
            try {
                // this allows us to use UTF-8 for storing data,
                // plus it checks any well-formedness issue in the submitted
                // data
                Transformer t = TransformerFactory.newInstance()
                        .newTransformer();
                t.transform(source,
                        new StreamResult(out));
                out.close();
            } catch (TransformerException e) {
                throw new IOException2("Failed to persist configuration.xml", e);
            }

            // try to reflect the changes by reloading
            new XmlFile(Items.XSTREAM, out.getTemporaryFile()).unmarshal(this);
            Items.updatingByXml.set(true);
            try {
                onLoad(getParent(), getRootDir().getName());
            } finally {
                Items.updatingByXml.set(false);
            }
            Jenkins.getInstance().rebuildDependencyGraphAsync();

            // if everything went well, commit this new version
            out.commit();
            SaveableListener.fireOnChange(this, getConfigFile());
        } finally {
            out.abort(); // don't leave anything behind
        }
    }


    /* (non-Javadoc)
     * @see hudson.model.AbstractModelObject#getSearchName()
     */
    @Override
    public String getSearchName() {
        // the search name of abstract items should be the name and not display name.
        // this will make suggestions use the names and not the display name
        // so that the links will 302 directly to the thing the user was finding
        return getName();
    }

    public String toString() {
        return super.toString()+'['+getFullName()+']';
    }

    /**
     * Used for CLI binding.
     */
    @CLIResolver
    public static AbstractItem resolveForCLI(
            @Argument(required=true,metaVar="NAME",usage="Job name") String name) throws CmdLineException {
        AbstractItem item = Jenkins.getInstance().getItemByFullName(name, AbstractItem.class);
        if (item==null)
            throw new CmdLineException(null,Messages.AbstractItem_NoSuchJobExists(name,AbstractProject.findNearest(name).getFullName()));
        return item;
    }

    /**
     * Replaceable pronoun of that points to a job. Defaults to "Job"/"Project" depending on the context.
     */
    public static final Message<AbstractItem> PRONOUN = new Message<AbstractItem>();
}
