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

import static jakarta.servlet.http.HttpServletResponse.SC_BAD_REQUEST;

import com.infradna.tool.bridge_method_injector.WithBridgeMethods;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.AbortException;
import hudson.BulkChange;
import hudson.Functions;
import hudson.Util;
import hudson.XmlFile;
import hudson.cli.declarative.CLIResolver;
import hudson.model.listeners.ItemListener;
import hudson.model.listeners.SaveableListener;
import hudson.security.ACL;
import hudson.security.ACLContext;
import hudson.security.AccessControlled;
import hudson.util.AlternativeUiTextProvider;
import hudson.util.AlternativeUiTextProvider.Message;
import hudson.util.AtomicFileWriter;
import hudson.util.FormValidation;
import hudson.util.IOUtils;
import hudson.util.Secret;
import io.jenkins.servlet.ServletExceptionWrapper;
import jakarta.servlet.ServletException;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.Collection;
import java.util.List;
import java.util.ListIterator;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.xml.transform.Source;
import javax.xml.transform.TransformerException;
import javax.xml.transform.sax.SAXSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import jenkins.model.DirectlyModifiableTopLevelItemGroup;
import jenkins.model.Jenkins;
import jenkins.model.Loadable;
import jenkins.model.queue.ItemDeletion;
import jenkins.security.ExtendedReadRedaction;
import jenkins.security.NotReallyRoleSensitiveCallable;
import jenkins.security.stapler.StaplerNotDispatchable;
import jenkins.util.SystemProperties;
import jenkins.util.xml.XMLUtils;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.taskdefs.Copy;
import org.apache.tools.ant.types.FileSet;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.stapler.Ancestor;
import org.kohsuke.stapler.HttpDeletable;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.HttpResponses;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.StaplerProxy;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerRequest2;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.StaplerResponse2;
import org.kohsuke.stapler.WebMethod;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;
import org.kohsuke.stapler.interceptor.RequirePOST;
import org.springframework.security.access.AccessDeniedException;
import org.xml.sax.SAXException;

/**
 * Partial default implementation of {@link Item}.
 *
 * @author Kohsuke Kawaguchi
 */
// Item doesn't necessarily have to be Actionable, but
// Java doesn't let multiple inheritance.
@ExportedBean
public abstract class AbstractItem extends Actionable implements Loadable, Item, HttpDeletable, AccessControlled, DescriptorByNameOwner, StaplerProxy {

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

    @Override
    @Exported(visibility = 999)
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

    /**
     * Gets the term used in the UI to represent the kind of {@link Queue.Task} associated with this kind of
     * {@link Item}. Must start with a capital letter. Defaults to "Build".
     * @since 2.50
     */
    public String getTaskNoun() {
        return AlternativeUiTextProvider.get(TASK_NOUN, this, Messages.AbstractItem_TaskNoun());
    }

    /**
     * @return The display name of this object, or if it is not set, the name
     * of the object.
     */
    @Override
    @Exported
    public String getDisplayName() {
        if (null != displayName) {
            return displayName;
        }
        // if the displayName is not set, then return the name as we use to do
        return getName();
    }

    /**
     * This is intended to be used by the Job configuration pages where
     * we want to return null if the display name is not set.
     * @return The display name of this object or null if the display name is not
     * set
     */
    @Exported
    public String getDisplayNameOrNull() {
        return displayName;
    }

    /**
     * This method exists so that the Job configuration pages can use
     * getDisplayNameOrNull so that nothing is shown in the display name text
     * box if the display name is not set.
     */
    public void setDisplayNameOrNull(String displayName) throws IOException {
        setDisplayName(displayName);
    }

    public void setDisplayName(String displayName) throws IOException {
        this.displayName = Util.fixEmptyAndTrim(displayName);
        save();
    }

    @Override
    public File getRootDir() {
        return getParent().getRootDirFor(this);
    }

    /**
     * This bridge method is to maintain binary compatibility with {@link TopLevelItem#getParent()}.
     */
    @WithBridgeMethods(value = Jenkins.class, castRequired = true)
    @Override public @NonNull ItemGroup getParent() {
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
     * Controls whether the default rename action is available for this item.
     *
     * @return whether {@link #name} can be modified by a user
     * @see #checkRename
     * @see #renameTo
     * @since 2.110
     */
    public boolean isNameEditable() {
        return false;
    }

    /**
     * Renames this item
     */
    @RequirePOST
    @Restricted(NoExternalUse.class)
    public HttpResponse doConfirmRename(@QueryParameter String newName) throws IOException {
        newName = newName == null ? null : newName.trim();
        FormValidation validationError = doCheckNewName(newName);
        if (validationError.kind != FormValidation.Kind.OK) {
            throw new Failure(validationError.getMessage());
        }

        renameTo(newName);
        // send to the new job page
        // note we can't use getUrl() because that would pick up old name in the
        // Ancestor.getUrl()
        return HttpResponses.redirectTo("../" + Functions.encode(newName));
    }

    /**
     * Called by {@link #doConfirmRename} and {@code rename.jelly} to validate renames.
     * @return {@link FormValidation#ok} if this item can be renamed as specified, otherwise
     * {@link FormValidation#error} with a message explaining the problem.
     */
    @Restricted(NoExternalUse.class)
    public @NonNull FormValidation doCheckNewName(@QueryParameter String newName) {

        if (!isNameEditable()) {
            return FormValidation.error("Trying to rename an item that does not support this operation.");
        }

        // TODO: Create an Item.RENAME permission to use here, see JENKINS-18649.
        if (!hasPermission(Item.CONFIGURE)) {
            if (parent instanceof AccessControlled) {
                ((AccessControlled) parent).checkPermission(Item.CREATE);
            }
            checkPermission(Item.DELETE);
        }

        newName = newName == null ? null : newName.trim();
        try {
            Jenkins.checkGoodName(newName);
            assert newName != null; // Would have thrown Failure
            if (newName.equals(name)) {
                return FormValidation.warning(Messages.AbstractItem_NewNameUnchanged());
            }
            Jenkins.get().getProjectNamingStrategy().checkName(getParent().getFullName(), newName);
            checkIfNameIsUsed(newName);
            checkRename(newName);
        } catch (Failure e) {
            return FormValidation.error(e.getMessage());
        }
        return FormValidation.ok();
    }

    /**
     * Check new name for job
     * @param newName - New name for job.
     */
    private void checkIfNameIsUsed(@NonNull String newName) throws Failure {
        try {
            Item item = getParent().getItem(newName);
            if (item != null) {
                throw new Failure(Messages.AbstractItem_NewNameInUse(newName));
            }
            try (ACLContext ctx = ACL.as2(ACL.SYSTEM2)) {
                item = getParent().getItem(newName);
                if (item != null) {
                    if (LOGGER.isLoggable(Level.FINE)) {
                        LOGGER.log(Level.FINE, "Unable to rename the job {0}: name {1} is already in use. " +
                                "User {2} has no {3} permission for existing job with the same name",
                                new Object[] {this.getFullName(), newName, ctx.getPreviousContext2().getAuthentication().getName(), Item.DISCOVER.name});
                    }
                    // Don't explicitly mention that there is another item with the same name.
                    throw new Failure(Messages.Jenkins_NotAllowedName(newName));
                }
            }
        } catch (AccessDeniedException ex) {
            if (LOGGER.isLoggable(Level.FINE)) {
                LOGGER.log(Level.FINE, "Unable to rename the job {0}: name {1} is already in use. " +
                        "User {2} has {3} permission, but no {4} for existing job with the same name",
                        new Object[] {this.getFullName(), newName, User.current(), Item.DISCOVER.name, Item.READ.name});
            }
            throw new Failure(Messages.AbstractItem_NewNameInUse(newName));
        }
    }

    /**
     * Allows subclasses to block renames for domain-specific reasons. Generic validation of the new name
     * (e.g., null checking, checking for illegal characters, and checking that the name is not in use)
     * always happens prior to calling this method.
     *
     * @param newName the new name for the item
     * @throws Failure if the rename should be blocked
     * @since 2.110
     * @see Job#checkRename
     */
    protected void checkRename(@NonNull String newName) throws Failure {

    }

    /**
     * Renames this item.
     * Not all the Items need to support this operation, but if you decide to do so,
     * you can use this method.
     */
    @SuppressFBWarnings(value = "SWL_SLEEP_WITH_LOCK_HELD", justification = "no big deal")
    protected void renameTo(final String newName) throws IOException {

        if (!isNameEditable()) {
            throw new IOException("Trying to rename an item that does not support this operation.");
        }

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

                // the lookup is case insensitive, so we should not fail if this item was the “existing” one
                // to allow people to rename "Foo" to "foo", for example.
                // see http://www.nabble.com/error-on-renaming-project-tt18061629.html
                Items.verifyItemDoesNotAlreadyExist(parent, newName, this);

                File oldRoot = this.getRootDir();

                doSetName(newName);
                File newRoot = this.getRootDir();

                boolean success = false;

                try { // rename data files
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
                        cp.setProject(new Project());
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
                            LOGGER.log(Level.WARNING, "Ignoring IOException while deleting", e);
                        }
                    }

                    success = true;
                } finally {
                    // if failed, back out the rename.
                    if (!success)
                        doSetName(oldName);
                }

                parent.onRenamed(this, oldName, newName);
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
    @Override
    public abstract Collection<? extends Job> getAllJobs();

    @Override
    @Exported
    public final String getFullName() {
        String n = getParent().getFullName();
        if (n.isEmpty())   return getName();
        else                return n + '/' + getName();
    }

    @Override
    @Exported
    public final String getFullDisplayName() {
        String n = getParent().getFullDisplayName();
        if (n.isEmpty())   return getDisplayName();
        else                return n + " » " + getDisplayName();
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
     * Called right after when a {@link Item} is loaded from disk.
     * This is an opportunity to do a post load processing.
     */
    @Override
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
     * @param src
     *      Item from which it's copied from. The same type as {@code this}. Never null.
     */
    @Override
    public void onCopiedFrom(Item src) {
    }

    @Override
    public final String getUrl() {
        // try to stick to the current view if possible
        StaplerRequest2 req = Stapler.getCurrentRequest2();
        String shortUrl = getShortUrl();
        String uri = req == null ? null : req.getRequestURI();
        if (req != null) {
            String seed = Functions.getNearestAncestorUrl(req, this);
            LOGGER.log(Level.FINER, "seed={0} for {1} from {2}", new Object[] {seed, this, uri});
            if (seed != null) {
                // trim off the context path portion and leading '/', but add trailing '/'
                return seed.substring(req.getContextPath().length() + 1) + '/';
            }
            List<Ancestor> ancestors = req.getAncestors();
            if (!ancestors.isEmpty()) {
                Ancestor last = ancestors.get(ancestors.size() - 1);
                if (last.getObject() instanceof View view) {
                    if (view.getOwner().getItemGroup() == getParent() && !view.isDefault()) {
                        // Showing something inside a view, so should use that as the base URL.
                        String prefix = req.getContextPath() + "/";
                        String url = last.getUrl();
                        if (url.startsWith(prefix)) {
                            String base = url.substring(prefix.length()) + '/';
                            LOGGER.log(Level.FINER, "using {0}{1} for {2} from {3} given {4}", new Object[] {base, shortUrl, this, uri, prefix});
                            return base + shortUrl;
                        } else {
                            LOGGER.finer(() -> url + " does not start with " + prefix + " as expected");
                        }
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

    @Override
    public String getShortUrl() {
        String prefix = getParent().getUrlChildPrefix();
        String subdir = Util.rawEncode(getName());
        return prefix.equals(".") ? subdir + '/' : prefix + '/' + subdir + '/';
    }

    @Override
    public String getSearchUrl() {
        return getShortUrl();
    }

    @Override
    @Exported(visibility = 999, name = "url")
    public final String getAbsoluteUrl() {
        return Item.super.getAbsoluteUrl();
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
    @NonNull
    @Override
    public ACL getACL() {
        return Jenkins.get().getAuthorizationStrategy().getACL(this);
    }

    /**
     * Save the settings to a file.
     */
    @Override
    public synchronized void save() throws IOException {
        if (BulkChange.contains(this))   return;
        getConfigFile().write(this);
        SaveableListener.fireOnChange(this, getConfigFile());
    }

    public final XmlFile getConfigFile() {
        return Items.getConfigFile(this);
    }

    protected Object writeReplace() {
        return XmlFile.replaceIfNotAtTopLevel(this, () -> new Replacer(this));
    }

    private static class Replacer {
        private final String fullName;

        Replacer(AbstractItem i) {
            fullName = i.getFullName();
        }

        private Object readResolve() {
            Jenkins j = Jenkins.getInstanceOrNull();
            if (j == null) {
                return null;
            }
            // Will generally only work if called after job loading:
            return j.getItemByFullName(fullName);
        }
    }

    /**
     * Accepts the new description.
     *
     * @since 2.475
     */
    @RequirePOST
    public synchronized void doSubmitDescription(StaplerRequest2 req, StaplerResponse2 rsp) throws IOException, ServletException {
        if (Util.isOverridden(AbstractItem.class, getClass(), "doSubmitDescription", StaplerRequest.class, StaplerResponse.class)) {
            try {
                doSubmitDescription(StaplerRequest.fromStaplerRequest2(req), StaplerResponse.fromStaplerResponse2(rsp));
            } catch (javax.servlet.ServletException e) {
                throw ServletExceptionWrapper.toJakartaServletException(e);
            }
        } else {
            doSubmitDescriptionImpl(req, rsp);
        }
    }

    /**
     * @deprecated use {@link #doSubmitDescription(StaplerRequest2, StaplerResponse2)}
     */
    @Deprecated
    @StaplerNotDispatchable
    public synchronized void doSubmitDescription(StaplerRequest req, StaplerResponse rsp) throws IOException, javax.servlet.ServletException {
        try {
            doSubmitDescriptionImpl(StaplerRequest.toStaplerRequest2(req), StaplerResponse.toStaplerResponse2(rsp));
        } catch (ServletException e) {
            throw ServletExceptionWrapper.fromJakartaServletException(e);
        }
    }

    private void doSubmitDescriptionImpl(StaplerRequest2 req, StaplerResponse2 rsp) throws IOException, ServletException {
        checkPermission(CONFIGURE);

        setDescription(req.getParameter("description"));
        rsp.sendRedirect(".");  // go to the top page
    }

    /**
     * Deletes this item.
     * Note on the funny name: for reasons of historical compatibility, this URL is {@code /doDelete}
     * since it predates {@code <l:confirmationLink>}. {@code /delete} goes to a Jelly page
     * which should now be unused by core but is left in case plugins are still using it.
     *
     * @since 2.475
     */
    @RequirePOST
    public void doDoDelete(StaplerRequest2 req, StaplerResponse2 rsp) throws IOException, ServletException, InterruptedException {
        if (Util.isOverridden(AbstractItem.class, getClass(), "doDoDelete", StaplerRequest.class, StaplerResponse.class)) {
            try {
                doDoDelete(StaplerRequest.fromStaplerRequest2(req), StaplerResponse.fromStaplerResponse2(rsp));
            } catch (javax.servlet.ServletException e) {
                throw ServletExceptionWrapper.toJakartaServletException(e);
            }
        } else {
            doDoDeleteImpl(req, rsp);
        }
    }

    /**
     * @deprecated use {@link #doDoDelete(StaplerRequest2, StaplerResponse2)}
     */
    @Deprecated
    @StaplerNotDispatchable
    public void doDoDelete(StaplerRequest req, StaplerResponse rsp) throws IOException, javax.servlet.ServletException, InterruptedException {
        doDoDeleteImpl(StaplerRequest.toStaplerRequest2(req), StaplerResponse.toStaplerResponse2(rsp));
    }

    private void doDoDeleteImpl(StaplerRequest2 req, StaplerResponse2 rsp) throws IOException, InterruptedException {
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

    /**
     * @since 2.475
     */
    @Override
    public void delete(StaplerRequest2 req, StaplerResponse2 rsp) throws IOException, ServletException {
        deleteImpl(rsp);
    }

    /**
     * @deprecated use {@link #delete(StaplerRequest2, StaplerResponse2)}
     */
    @Deprecated
    @Override
    public void delete(StaplerRequest req, StaplerResponse rsp) throws IOException, javax.servlet.ServletException {
        try {
            deleteImpl(StaplerResponse.toStaplerResponse2(rsp));
        } catch (ServletException e) {
            throw ServletExceptionWrapper.fromJakartaServletException(e);
        }
    }

    private void deleteImpl(StaplerResponse2 rsp) throws IOException, ServletException {
        try {
            delete();
            rsp.setStatus(204);
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
     * from showing the stack trace.
     * @see ItemDeletion
     */
    @Override
    public void delete() throws IOException, InterruptedException {
        checkPermission(DELETE);
        ItemListener.checkBeforeDelete(this);
        boolean responsibleForAbortingBuilds = !ItemDeletion.contains(this);
        boolean ownsRegistration = ItemDeletion.register(this);
        if (!ownsRegistration && ItemDeletion.isRegistered(this)) {
            // we are not the owning thread and somebody else is concurrently deleting this exact item
            throw new Failure(Messages.AbstractItem_BeingDeleted(getPronoun()));
        }
        try {
            // if a build is in progress. Cancel it.
            if (responsibleForAbortingBuilds || ownsRegistration) {
                ItemDeletion.cancelBuildsInProgress(this);
            }
            if (this instanceof ItemGroup) {
                // delete individual items first
                // (disregard whether they would be deletable in isolation)
                // JENKINS-34939: do not hold the monitor on this folder while deleting them
                // (thus we cannot do this inside performDelete)
                try (ACLContext oldContext = ACL.as2(ACL.SYSTEM2)) {
                    for (Item i : ((ItemGroup<?>) this).getItems(TopLevelItem.class::isInstance)) {
                        try {
                            i.delete();
                        } catch (AbortException e) {
                            throw (AbortException) new AbortException(
                                    "Failed to delete " + i.getFullDisplayName() + " : " + e.getMessage()).initCause(e);
                        } catch (IOException e) {
                            throw new IOException("Failed to delete " + i.getFullDisplayName(), e);
                        }
                    }
                }
            }
            synchronized (this) { // could just make performDelete synchronized but overriders might not honor that
                performDelete();
            } // JENKINS-19446: leave synch block, but JENKINS-22001: still notify synchronously
        } finally {
            if (ownsRegistration) {
                ItemDeletion.deregister(this);
            }
        }
        SaveableListener.fireOnDeleted(this, getConfigFile());
        getParent().onDeleted(AbstractItem.this);
        Jenkins.get().rebuildDependencyGraphAsync();
    }

    /**
     * Does the real job of deleting the item.
     */
    protected void performDelete() throws IOException, InterruptedException {
        getConfigFile().delete();
        Util.deleteRecursive(getRootDir());
    }

    /**
     * Accepts {@code config.xml} submission, as well as serve it.
     *
     * @since 2.475
     */
    @WebMethod(name = "config.xml")
    public void doConfigDotXml(StaplerRequest2 req, StaplerResponse2 rsp)
            throws IOException {
        if (Util.isOverridden(AbstractItem.class, getClass(), "doConfigDotXml", StaplerRequest.class, StaplerResponse.class)) {
            doConfigDotXml(StaplerRequest.fromStaplerRequest2(req), StaplerResponse.fromStaplerResponse2(rsp));
        } else {
            doConfigDotXmlImpl(req, rsp);
        }
    }

    /**
     * @deprecated use {@link #doConfigDotXml(StaplerRequest2, StaplerResponse2)}
     */
    @Deprecated
    @StaplerNotDispatchable
    public void doConfigDotXml(StaplerRequest req, StaplerResponse rsp)
            throws IOException {
        doConfigDotXmlImpl(StaplerRequest.toStaplerRequest2(req), StaplerResponse.toStaplerResponse2(rsp));
    }

    private void doConfigDotXmlImpl(StaplerRequest2 req, StaplerResponse2 rsp)
            throws IOException {
        if (req.getMethod().equals("GET")) {
            // read
            rsp.setContentType("application/xml");
            writeConfigDotXml(rsp.getOutputStream());
            return;
        }
        if (req.getMethod().equals("POST")) {
            // submission
            updateByXml((Source) new StreamSource(req.getReader()));
            return;
        }

        // huh?
        rsp.sendError(SC_BAD_REQUEST);
    }

    /**
     * Writes {@code config.xml} to the specified output stream.
     * The user must have at least {@link #EXTENDED_READ}.
     * If he lacks {@link #CONFIGURE}, then any {@link Secret}s or other sensitive information detected will be masked out.
     * @see jenkins.security.ExtendedReadRedaction
     */

    @Restricted(NoExternalUse.class)
    public void writeConfigDotXml(OutputStream os) throws IOException {
        checkPermission(EXTENDED_READ);
        XmlFile configFile = getConfigFile();
        if (hasPermission(CONFIGURE)) {
            IOUtils.copy(configFile.getFile(), os);
        } else {
            String encoding = configFile.sniffEncoding();
            String xml = Files.readString(Util.fileToPath(configFile.getFile()), Charset.forName(encoding));

            for (ExtendedReadRedaction redaction : ExtendedReadRedaction.all()) {
                LOGGER.log(Level.FINE, () -> "Applying redaction " + redaction.getClass().getName());
                xml = redaction.apply(xml);
            }

            org.apache.commons.io.IOUtils.write(xml, os, encoding);
        }
    }

    /**
     * @deprecated as of 1.473
     *      Use {@link #updateByXml(Source)}
     */
    @Deprecated
    public void updateByXml(StreamSource source) throws IOException {
        updateByXml((Source) source);
    }

    /**
     * Updates an Item by its XML definition.
     * @param source source of the Item's new definition.
     *               The source should be either a {@link StreamSource} or a {@link SAXSource}, other
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
            } catch (TransformerException | SAXException e) {
                throw new IOException("Failed to persist config.xml", e);
            }

            // try to reflect the changes by reloading
            Object o = new XmlFile(Items.XSTREAM, out.getTemporaryPath().toFile()).unmarshalNullingOut(this);
            if (o != this) {
                // ensure that we've got the same job type. extending this code to support updating
                // to different job type requires destroying & creating a new job type
                throw new IOException("Expecting " + this.getClass() + " but got " + o.getClass() + " instead");
            }

            Items.whileUpdatingByXml(new NotReallyRoleSensitiveCallable<Void, IOException>() {
                @Override public Void call() throws IOException {
                    onLoad(getParent(), getRootDir().getName());
                    return null;
                }
            });
            Jenkins.get().rebuildDependencyGraphAsync();

            // if everything went well, commit this new version
            out.commit();
            SaveableListener.fireOnChange(this, getConfigFile());
            ItemListener.fireOnUpdated(this);

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
        load();
    }

    @Override
    public void load() throws IOException {
        checkPermission(CONFIGURE);

        // try to reflect the changes by reloading
        getConfigFile().unmarshal(this);
        Items.whileUpdatingByXml(new NotReallyRoleSensitiveCallable<Void, IOException>() {
            @Override
            public Void call() throws IOException {
                onLoad(getParent(), getParent().getItemName(getRootDir(), AbstractItem.this));
                return null;
            }
        });
        Jenkins.get().rebuildDependencyGraphAsync();
    }


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

    @Override
    @Restricted(NoExternalUse.class)
    public Object getTarget() {
        if (!SKIP_PERMISSION_CHECK) {
            if (!hasPermission(Item.DISCOVER)) {
                return null;
            }
            checkPermission(Item.READ);
        }
        return this;
    }

    /**
     * Escape hatch for StaplerProxy-based access control
     */
    @Restricted(NoExternalUse.class)
    @SuppressFBWarnings(value = "MS_SHOULD_BE_FINAL", justification = "for script console")
    public static /* Script Console modifiable */ boolean SKIP_PERMISSION_CHECK = SystemProperties.getBoolean(AbstractItem.class.getName() + ".skipPermissionCheck");

    /**
     * Used for CLI binding.
     */
    @CLIResolver
    public static AbstractItem resolveForCLI(
            @Argument(required = true, metaVar = "NAME", usage = "Item name") String name) throws CmdLineException {
        // TODO can this (and its pseudo-override in AbstractProject) share code with GenericItemOptionHandler, used for explicit CLICommand’s rather than CLIMethod’s?
        AbstractItem item = Jenkins.get().getItemByFullName(name, AbstractItem.class);
        if (item == null) {
            AbstractItem project = Items.findNearest(AbstractItem.class, name, Jenkins.get());
            throw new CmdLineException(null, project == null ? Messages.AbstractItem_NoSuchJobExistsWithoutSuggestion(name)
                    : Messages.AbstractItem_NoSuchJobExists(name, project.getFullName()));
        }
        return item;
    }

    /**
     * Replaceable pronoun of that points to a job. Defaults to "Job"/"Project" depending on the context.
     */
    public static final Message<AbstractItem> PRONOUN = new Message<>();

    /**
     * Replaceable noun for describing the kind of task that this item represents. Defaults to "Build".
     */
    public static final Message<AbstractItem> TASK_NOUN = new Message<>();

}
