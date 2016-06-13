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
import hudson.cli.declarative.CLIResolver;
import hudson.model.listeners.ItemListener;
import hudson.model.listeners.SaveableListener;
import hudson.security.AccessControlled;
import hudson.security.Permission;
import hudson.security.ACL;
import hudson.util.AlternativeUiTextProvider;
import hudson.util.AlternativeUiTextProvider.Message;
import hudson.util.AtomicFileWriter;
import hudson.util.IOUtils;
import hudson.util.Secret;
import jenkins.model.DirectlyModifiableTopLevelItemGroup;
import jenkins.model.Jenkins;
import jenkins.security.NotReallyRoleSensitiveCallable;
import org.acegisecurity.Authentication;
import jenkins.util.xml.XMLUtils;

import org.apache.tools.ant.taskdefs.Copy;
import org.apache.tools.ant.types.FileSet;
import org.kohsuke.stapler.WebMethod;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Collection;
import java.util.List;
import java.util.ListIterator;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nonnull;

import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.HttpDeletable;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.stapler.interceptor.RequirePOST;
import org.xml.sax.SAXException;

import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.xml.transform.Source;
import javax.xml.transform.TransformerException;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST;
import org.apache.commons.io.FileUtils;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.Ancestor;

/**
 * Partial default implementation of {@link Item}.
 *
 * @author Kohsuke Kawaguchi
 */
// Item doesn't necessarily have to be Actionable, but
// Java doesn't let multiple inheritance.
@ExportedBean
public abstract class AbstractItem extends Actionable implements Item, HttpDeletable, AccessControlled, DescriptorByNameOwner {

    private static final Logger LOGGER = Logger.getLogger(AbstractItem.class.getName());

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
        this.displayName = Util.fixEmptyAndTrim(displayName);
        save();
    }
             
    public File getRootDir() {
        return getParent().getRootDirFor(this);
    }

    /**
     * This bridge method is to maintain binary compatibility with {@link TopLevelItem#getParent()}.
     */
    @WithBridgeMethods(value=Jenkins.class,castRequired=true)
    @Override public @Nonnull ItemGroup getParent() {
        if (parent == null) {
            throw new IllegalStateException("no parent set on " + getClass().getName() + "[" + name + "]");
        }
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
    protected void renameTo(final String newName) throws IOException {
        // always synchronize from bigger objects first
        final ItemGroup parent = getParent();
        String oldName = this.name;
        String oldFullName = getFullName();
        synchronized (parent) {
            synchronized (this) {
                // sanity check
                if (newName == null)
                    throw new IllegalArgumentException("New name is not given");

                // noop?
                if (this.name.equals(newName))
                    return;

                // the test to see if the project already exists or not needs to be done in escalated privilege
                // to avoid overwriting
                ACL.impersonate(ACL.SYSTEM,new NotReallyRoleSensitiveCallable<Void,IOException>() {
                    final Authentication user = Jenkins.getAuthentication();
                    @Override
                    public Void call() throws IOException {
                        Item existing = parent.getItem(newName);
                        if (existing != null && existing!=AbstractItem.this) {
                            if (existing.getACL().hasPermission(user,Item.DISCOVER))
                                // the look up is case insensitive, so we need "existing!=this"
                                // to allow people to rename "Foo" to "foo", for example.
                                // see http://www.nabble.com/error-on-renaming-project-tt18061629.html
                                throw new IllegalArgumentException("Job " + newName + " already exists");
                            else {
                                // can't think of any real way to hide this, but at least the error message could be vague.
                                throw new IOException("Unable to rename to " + newName);
                            }
                        }
                        return null;
                    }
                });

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
                        src.setDir(oldRoot);
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

                try {
                    parent.onRenamed(this, oldName, newName);
                } catch (AbstractMethodError _) {
                    // ignore
                }
            }
        }
        ItemListener.fireLocationChange(this, oldFullName);
    }


    /**
     * Notify this item it's been moved to another location, replaced by newItem (might be the same object, but not guaranteed).
     * This method is executed <em>after</em> the item root directory has been moved to it's new location.
     * <p>
     * Derived classes can override this method to add some specific behavior on move, but have to call parent method
     * so the item is actually setup within it's new parent.
     *
     * @see hudson.model.Items#move(AbstractItem, jenkins.model.DirectlyModifiableTopLevelItemGroup)
     */
    public void movedTo(DirectlyModifiableTopLevelItemGroup destination, AbstractItem newItem, File destDir) throws IOException {
        newItem.onLoad(destination, name);
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
        else                return n+" » "+getDisplayName();
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
     * This is an opportunity to do a post load processing.
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
        String shortUrl = getShortUrl();
        String uri = req == null ? null : req.getRequestURI();
        if (req != null) {
            String seed = Functions.getNearestAncestorUrl(req,this);
            LOGGER.log(Level.FINER, "seed={0} for {1} from {2}", new Object[] {seed, this, uri});
            if(seed!=null) {
                // trim off the context path portion and leading '/', but add trailing '/'
                return seed.substring(req.getContextPath().length()+1)+'/';
            }
            List<Ancestor> ancestors = req.getAncestors();
            if (!ancestors.isEmpty()) {
                Ancestor last = ancestors.get(ancestors.size() - 1);
                if (last.getObject() instanceof View) {
                    View view = (View) last.getObject();
                    if (view.getOwnerItemGroup() == getParent() && !view.isDefault()) {
                        // Showing something inside a view, so should use that as the base URL.
                        String base = last.getUrl().substring(req.getContextPath().length() + 1) + '/';
                        LOGGER.log(Level.FINER, "using {0}{1} for {2} from {3}", new Object[] {base, shortUrl, this, uri});
                        return base + shortUrl;
                    } else {
                        LOGGER.log(Level.FINER, "irrelevant {0} for {1} from {2}", new Object[] {view.getViewName(), this, uri});
                    }
                } else {
                    LOGGER.log(Level.FINER, "inapplicable {0} for {1} from {2}", new Object[] {last.getObject(), this, uri});
                }
            } else {
                LOGGER.log(Level.FINER, "no ancestors for {0} from {1}", new Object[] {this, uri});
            }
        } else {
            LOGGER.log(Level.FINER, "no current request for {0}", this);
        }
        // otherwise compute the path normally
        String base = getParent().getUrl();
        LOGGER.log(Level.FINER, "falling back to {0}{1} for {2} from {3}", new Object[] {base, shortUrl, this, uri});
        return base + shortUrl;
    }

    public String getShortUrl() {
        String prefix = getParent().getUrlChildPrefix();
        String subdir = Util.rawEncode(getName());
        return prefix.equals(".") ? subdir + '/' : prefix + '/' + subdir + '/';
    }

    public String getSearchUrl() {
        return getUrl();
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
    @RequirePOST
    public void doDoDelete( StaplerRequest req, StaplerResponse rsp ) throws IOException, ServletException, InterruptedException {
        delete();
        if (req == null || rsp == null) { // CLI
            return;
        }
        List<Ancestor> ancestors = req.getAncestors();
        ListIterator<Ancestor> it = ancestors.listIterator(ancestors.size());
        String url = getParent().getUrl(); // fallback but we ought to get to Jenkins.instance at the root
        while (it.hasPrevious()) {
            Object a = it.previous().getObject();
            if (a instanceof View) {
                url = ((View) a).getUrl();
                break;
            } else if (a instanceof ViewGroup && a != this) {
                url = ((ViewGroup) a).getUrl();
                break;
            }
        }
        rsp.sendRedirect2(req.getContextPath() + '/' + url);
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
    public void delete() throws IOException, InterruptedException {
        checkPermission(DELETE);
        synchronized (this) { // could just make performDelete synchronized but overriders might not honor that
            performDelete();
        } // JENKINS-19446: leave synch block, but JENKINS-22001: still notify synchronously
        getParent().onDeleted(AbstractItem.this);
        Jenkins.getInstance().rebuildDependencyGraphAsync();
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
            rsp.setContentType("application/xml");
            writeConfigDotXml(rsp.getOutputStream());
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

    static final Pattern SECRET_PATTERN = Pattern.compile(">(" + Secret.ENCRYPTED_VALUE_PATTERN + ")<");
    /**
     * Writes {@code config.xml} to the specified output stream.
     * The user must have at least {@link #EXTENDED_READ}.
     * If he lacks {@link #CONFIGURE}, then any {@link Secret}s detected will be masked out.
     */
    @Restricted(NoExternalUse.class)
    public void writeConfigDotXml(OutputStream os) throws IOException {
        checkPermission(EXTENDED_READ);
        XmlFile configFile = getConfigFile();
        if (hasPermission(CONFIGURE)) {
            IOUtils.copy(configFile.getFile(), os);
        } else {
            String encoding = configFile.sniffEncoding();
            String xml = FileUtils.readFileToString(configFile.getFile(), encoding);
            Matcher matcher = SECRET_PATTERN.matcher(xml);
            StringBuffer cleanXml = new StringBuffer();
            while (matcher.find()) {
                if (Secret.decrypt(matcher.group(1)) != null) {
                    matcher.appendReplacement(cleanXml, ">********<");
                }
            }
            matcher.appendTail(cleanXml);
            org.apache.commons.io.IOUtils.write(cleanXml.toString(), os, encoding);
        }
    }

    /**
     * @deprecated as of 1.473
     *      Use {@link #updateByXml(Source)}
     */
    @Deprecated
    public void updateByXml(StreamSource source) throws IOException {
        updateByXml((Source)source);
    }

    /**
     * Updates an Item by its XML definition.
     * @param source source of the Item's new definition.
     *               The source should be either a <code>StreamSource</code> or a <code>SAXSource</code>, other
     *               sources may not be handled.
     * @since 1.473
     */
    public void updateByXml(Source source) throws IOException {
        checkPermission(CONFIGURE);
        XmlFile configXmlFile = getConfigFile();
        final AtomicFileWriter out = new AtomicFileWriter(configXmlFile.getFile());
        try {
            try {
                XMLUtils.safeTransform(source, new StreamResult(out));
                out.close();
            } catch (TransformerException e) {
                throw new IOException("Failed to persist config.xml", e);
            } catch (SAXException e) {
                throw new IOException("Failed to persist config.xml", e);
            }

            // try to reflect the changes by reloading
            Object o = new XmlFile(Items.XSTREAM, out.getTemporaryFile()).unmarshal(this);
            if (o!=this) {
                // ensure that we've got the same job type. extending this code to support updating
                // to different job type requires destroying & creating a new job type
                throw new IOException("Expecting "+this.getClass()+" but got "+o.getClass()+" instead");
            }

            Items.whileUpdatingByXml(new NotReallyRoleSensitiveCallable<Void,IOException>() {
                @Override public Void call() throws IOException {
                    onLoad(getParent(), getRootDir().getName());
                    return null;
                }
            });
            Jenkins.getInstance().rebuildDependencyGraphAsync();

            // if everything went well, commit this new version
            out.commit();
            SaveableListener.fireOnChange(this, getConfigFile());

        } finally {
            out.abort(); // don't leave anything behind
        }
    }

    /**
     * Reloads this job from the disk.
     *
     * Exposed through CLI as well.
     *
     * TODO: think about exposing this to UI
     *
     * @since 1.556
     */
    @RequirePOST
    public void doReload() throws IOException {
        checkPermission(CONFIGURE);

        // try to reflect the changes by reloading
        getConfigFile().unmarshal(this);
        Items.whileUpdatingByXml(new NotReallyRoleSensitiveCallable<Void, IOException>() {
            @Override
            public Void call() throws IOException {
                onLoad(getParent(), getRootDir().getName());
                return null;
            }
        });
        Jenkins.getInstance().rebuildDependencyGraphAsync();

        SaveableListener.fireOnChange(this, getConfigFile());
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public String getSearchName() {
        // the search name of abstract items should be the name and not display name.
        // this will make suggestions use the names and not the display name
        // so that the links will 302 directly to the thing the user was finding
        return getName();
    }

    @Override public String toString() {
        return super.toString() + '[' + (parent != null ? getFullName() : "?/" + name) + ']';
    }

    /**
     * Used for CLI binding.
     */
    @CLIResolver
    public static AbstractItem resolveForCLI(
            @Argument(required=true,metaVar="NAME",usage="Job name") String name) throws CmdLineException {
        // TODO can this (and its pseudo-override in AbstractProject) share code with GenericItemOptionHandler, used for explicit CLICommand’s rather than CLIMethod’s?
        AbstractItem item = Jenkins.getInstance().getItemByFullName(name, AbstractItem.class);
        if (item==null) {
            AbstractProject project = AbstractProject.findNearest(name);
            throw new CmdLineException(null, project == null ? Messages.AbstractItem_NoSuchJobExistsWithoutSuggestion(name)
                    : Messages.AbstractItem_NoSuchJobExists(name, project.getFullName()));
        }
        return item;
    }

    /**
     * Replaceable pronoun of that points to a job. Defaults to "Job"/"Project" depending on the context.
     */
    public static final Message<AbstractItem> PRONOUN = new Message<AbstractItem>();

}
