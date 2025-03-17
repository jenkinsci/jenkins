/*
 * The MIT License
 *
 * Copyright (c) 2004-2011, Sun Microsystems, Inc., Kohsuke Kawaguchi, Tom Huybrechts,
 * Yahoo!, Inc.
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

import com.thoughtworks.xstream.converters.ConversionException;
import com.thoughtworks.xstream.io.StreamException;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.DescriptorExtensionList;
import hudson.Extension;
import hudson.ExtensionPoint;
import hudson.Functions;
import hudson.Indenter;
import hudson.Util;
import hudson.init.InitMilestone;
import hudson.init.Initializer;
import hudson.model.Descriptor.FormException;
import hudson.model.listeners.ItemListener;
import hudson.search.CollectionSearchIndex;
import hudson.search.SearchIndexBuilder;
import hudson.security.ACL;
import hudson.security.AccessControlled;
import hudson.security.Permission;
import hudson.security.PermissionGroup;
import hudson.security.PermissionScope;
import hudson.util.AlternativeUiTextProvider;
import hudson.util.AlternativeUiTextProvider.Message;
import hudson.util.DescribableList;
import hudson.util.DescriptorList;
import hudson.util.FormApply;
import hudson.util.FormValidation;
import hudson.util.RunList;
import hudson.util.XStream2;
import hudson.views.ListViewColumn;
import io.jenkins.servlet.ServletExceptionWrapper;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletResponse;
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.io.StringWriter;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import javax.xml.transform.Source;
import javax.xml.transform.TransformerException;
import javax.xml.transform.sax.SAXSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import jenkins.model.Jenkins;
import jenkins.model.ModelObjectWithChildren;
import jenkins.model.ModelObjectWithContextMenu;
import jenkins.model.item_category.Categories;
import jenkins.model.item_category.Category;
import jenkins.model.item_category.ItemCategory;
import jenkins.search.SearchGroup;
import jenkins.security.ExtendedReadRedaction;
import jenkins.security.stapler.StaplerNotDispatchable;
import jenkins.util.xml.XMLUtils;
import jenkins.widgets.HasWidgets;
import net.sf.json.JSONObject;
import org.jenkins.ui.icon.Icon;
import org.jenkins.ui.icon.IconSet;
import org.jenkins.ui.symbol.Symbol;
import org.jenkins.ui.symbol.SymbolRequest;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.DoNotUse;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.HttpResponses;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerRequest2;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.StaplerResponse2;
import org.kohsuke.stapler.WebMethod;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;
import org.kohsuke.stapler.interceptor.RequirePOST;
import org.kohsuke.stapler.verb.POST;
import org.xml.sax.SAXException;

/**
 * Encapsulates the rendering of the list of {@link TopLevelItem}s
 * that {@link Jenkins} owns.
 *
 * <p>
 * This is an extension point in Hudson, allowing different kind of
 * rendering to be added as plugins.
 *
 * <h2>Note for implementers</h2>
 * <ul>
 * <li>
 * {@link View} subtypes need the {@code newViewDetail.jelly} page,
 * which is included in the "new view" page. This page should have some
 * description of what the view is about.
 * </ul>
 *
 * @author Kohsuke Kawaguchi
 * @see ViewDescriptor
 * @see ViewGroup
 */
@ExportedBean
public abstract class View extends AbstractModelObject implements AccessControlled, Describable<View>, ExtensionPoint, Saveable, ModelObjectWithChildren, DescriptorByNameOwner, HasWidgets {

    /**
     * Container of this view. Set right after the construction
     * and never change thereafter.
     */
    protected /*final*/ ViewGroup owner;

    /**
     * Name of this view.
     */
    protected String name;

    /**
     * Message displayed in the view page.
     */
    protected String description;

    /**
     * If true, only show relevant executors
     */
    protected boolean filterExecutors;

    /**
     * If true, only show relevant queue items
     */
    protected boolean filterQueue;

    /**
     * List of {@link ViewProperty}s configured for this view.
     * @since 1.406
     */
    private volatile DescribableList<ViewProperty, ViewPropertyDescriptor> properties = new PropertyList(this);

    protected View(String name) {
        this.name = name;
    }

    protected View(String name, ViewGroup owner) {
        this.name = name;
        this.owner = owner;
    }

    /**
     * Gets all the items in this collection in a read-only view.
     */
    @NonNull
    @Exported(name = "jobs")
    public abstract Collection<TopLevelItem> getItems();

    /**
     * Gets all the items recursively contained in this collection in a read-only view.
     * <p>
     * The default implementation recursively adds the items of all contained Views
     * in case this view implements {@link ViewGroup}, which should be enough for most cases.
     *
     * @since 1.520
     */
    public Collection<TopLevelItem> getAllItems() {

        if (this instanceof ViewGroup) {
            final Collection<TopLevelItem> items = new LinkedHashSet<>(getItems());

            for (View view : ((ViewGroup) this).getViews()) {
                items.addAll(view.getAllItems());
            }
            return Collections.unmodifiableCollection(items);
        } else {
            return getItems();
        }
    }

    /**
     * Gets the {@link TopLevelItem} of the given name.
     */
    public TopLevelItem getItem(String name) {
        return getOwner().getItemGroup().getItem(name);
    }

    /**
     * Alias for {@link #getItem(String)}. This is the one used in the URL binding.
     */
    public final TopLevelItem getJob(String name) {
        return getItem(name);
    }

    /**
     * Checks if the job is in this collection.
     */
    public abstract boolean contains(TopLevelItem item);

    /**
     * Gets the name of all this collection.
     *
     * @see #rename(String)
     */
    @Exported(visibility = 2, name = "name")
    @NonNull
    public String getViewName() {
        return name;
    }

    /**
     * Renames this view.
     */
    public void rename(String newName) throws Failure, FormException {
        if (name.equals(newName))    return; // noop
        Jenkins.checkGoodName(newName);
        if (owner.getView(newName) != null)
            throw new FormException(Messages.Hudson_ViewAlreadyExists(newName), "name");
        String oldName = name;
        name = newName;
        owner.onViewRenamed(this, oldName, newName);
    }

    /**
     * Gets the {@link ViewGroup} that this view belongs to.
     */
    public ViewGroup getOwner() {
        return owner;
    }

    /** @deprecated call {@link ViewGroup#getItemGroup} directly */
    @Deprecated
    public ItemGroup<? extends TopLevelItem> getOwnerItemGroup() {
        return owner.getItemGroup();
    }

    /** @deprecated call {@link ViewGroup#getPrimaryView} directly */
    @Deprecated
    public View getOwnerPrimaryView() {
        return owner.getPrimaryView();
    }

    /** @deprecated call {@link ViewGroup#getViewActions} directly */
    @Deprecated
    public List<Action> getOwnerViewActions() {
        return owner.getViewActions();
    }

    /**
     * Message displayed in the top page. Can be null. Includes HTML.
     */
    @Exported
    public synchronized String getDescription() {
        return description;
    }

    @DataBoundSetter
    public synchronized void setDescription(String description) {
        this.description = Util.nullify(description);
    }

    /**
     * Gets the view properties configured for this view.
     * @since 1.406
     */
    public DescribableList<ViewProperty, ViewPropertyDescriptor> getProperties() {
        // readResolve was the best place to do this, but for compatibility reasons,
        // this class can no longer have readResolve() (the mechanism itself isn't suitable for class hierarchy)
        // see JENKINS-9431
        //
        // until we have that, putting this logic here.
        synchronized (PropertyList.class) {
            if (properties == null) {
                properties = new PropertyList(this);
            } else {
                properties.setOwner(this);
            }
            return properties;
        }
    }

    /**
     * Returns all the {@link ViewPropertyDescriptor}s that can be potentially configured
     * on this view. Returns both {@link ViewPropertyDescriptor}s visible and invisible for user, see
     * {@link View#getVisiblePropertyDescriptors} to filter invisible one.
     */
    public List<ViewPropertyDescriptor> getApplicablePropertyDescriptors() {
        List<ViewPropertyDescriptor> r = new ArrayList<>();
        for (ViewPropertyDescriptor pd : ViewProperty.all()) {
            if (pd.isEnabledFor(this))
                r.add(pd);
        }
        return r;
    }

    /**
     * @return all the {@link ViewPropertyDescriptor}s that can be potentially configured on this View and are visible
     * for the user. Use {@link DescriptorVisibilityFilter} to make a View property invisible for users.
     * @since 2.214
     */
    public List<ViewPropertyDescriptor> getVisiblePropertyDescriptors() {
        return DescriptorVisibilityFilter.apply(this, getApplicablePropertyDescriptors());
    }

    @Override
    public void save() throws IOException {
        // persistence is a part of the owner
        // due to initialization timing issue, it can be null when this method is called
        if (owner != null) {
            owner.save();
        }
    }

    /**
     * List of all {@link ViewProperty}s exposed primarily for the remoting API.
     * @since 1.406
     */
    @Exported(name = "property", inline = true)
    public List<ViewProperty> getAllProperties() {
        return getProperties().toList();
    }

    @Override
    public ViewDescriptor getDescriptor() {
        return (ViewDescriptor) Jenkins.get().getDescriptorOrDie(getClass());
    }

    @Override
    public String getDisplayName() {
        return getViewName();
    }

    public String getNewPronoun() {
        return AlternativeUiTextProvider.get(NEW_PRONOUN, this, Messages.AbstractItem_Pronoun());
    }

    /**
     * By default, return true to render the "Edit view" link on the page.
     * This method is really just for the default "All" view to hide the edit link
     * so that the default Hudson top page remains the same as before 1.316.
     *
     * @since 1.316
     */
    public boolean isEditable() {
        return true;
    }

    /**
     * Used to enable or disable automatic refreshes of the view.
     *
     * @since 1.557
     *
     * @deprecated Auto-refresh has been removed
     */
    @Deprecated
    public boolean isAutomaticRefreshEnabled() {
        return false;
    }

    /**
     * If true, only show relevant executors
     */
    public boolean isFilterExecutors() {
        return filterExecutors;
    }

    /**
     * @since 2.426
     */
    @DataBoundSetter
    public void setFilterExecutors(boolean filterExecutors) {
        this.filterExecutors = filterExecutors;
    }

    /**
     * If true, only show relevant queue items
     */
    public boolean isFilterQueue() {
        return filterQueue;
    }

    /**
     * @since 2.426
     */
    @DataBoundSetter
    public void setFilterQueue(boolean filterQueue) {
        this.filterQueue = filterQueue;
    }

    /**
     * If this view uses {@code <t:projectView>} for rendering, this method returns columns to be displayed.
     */
    public Iterable<? extends ListViewColumn> getColumns() {
        return ListViewColumn.createDefaultInitialColumnList(this);
    }

    /**
     * If this view uses {@code t:projectView} for rendering, this method returns the indenter used
     * to indent each row.
     */
    public Indenter getIndenter() {
        return null;
    }

    /**
     * If true, this is a view that renders the top page of Hudson.
     */
    public boolean isDefault() {
        return getOwner().getPrimaryView() == this;
    }

    public List<Computer> getComputers() {
        Computer[] computers = Jenkins.get().getComputers();

        if (!isFilterExecutors()) {
            return Arrays.asList(computers);
        }

        List<Computer> result = new ArrayList<>();

        HashSet<Label> labels = new HashSet<>();
        for (Item item : getItems()) {
            if (item instanceof AbstractProject<?, ?>) {
                labels.addAll(((AbstractProject<?, ?>) item).getRelevantLabels());
            }
        }

        for (Computer c : computers) {
            if (isRelevant(labels, c)) result.add(c);
        }

        return result;
    }

    private boolean isRelevant(Collection<Label> labels, Computer computer) {
        Node node = computer.getNode();
        if (node == null) return false;
        if (labels.contains(null) && node.getMode() == Node.Mode.NORMAL) return true;

        for (Label l : labels)
            if (l != null && l.contains(node))
                return true;
        return false;
    }

    private static final int FILTER_LOOP_MAX_COUNT = 10;

    private List<Queue.Item> filterQueue(List<Queue.Item> base) {
        if (!isFilterQueue()) {
            return base;
        }
        Collection<TopLevelItem> items = getItems();
        return base.stream().filter(qi -> filterQueueItemTest(qi, items))
                .collect(Collectors.toList());
    }

    private boolean filterQueueItemTest(Queue.Item item, Collection<TopLevelItem> viewItems) {
        // Check if the task of parent tasks are in the list of viewItems.
        // Pipeline jobs and other jobs which allow parts require us to
        // check owner tasks as well.
        Queue.Task currentTask = item.task;
        for (int count = 1;; count++) {
            if (viewItems.contains(currentTask)) {
                return true;
            }
            Queue.Task next = currentTask.getOwnerTask();
            if (next == currentTask) {
                break;
            } else {
                currentTask = next;
            }
            if (count == FILTER_LOOP_MAX_COUNT) {
                LOGGER.warning(String.format(
                        "Failed to find root task for queue item '%s' for " +
                        "view '%s' in under %d iterations, aborting!",
                        item.getDisplayName(), getDisplayName(),
                        FILTER_LOOP_MAX_COUNT));
                break;
            }
        }
        // Check root project for sub-job projects (e.g. matrix jobs).
        if (item.task instanceof AbstractProject<?, ?> project) {
            return viewItems.contains(project.getRootProject());
        }
        return false;
    }

    public List<Queue.Item> getQueueItems() {
        return filterQueue(Arrays.asList(Jenkins.get().getQueue().getItems()));
    }

    /**
     * @return The items in the queue.
     * @deprecated Use {@link #getQueueItems()}. As of 1.607 the approximation is no longer needed.
     */
    @Deprecated
    public List<Queue.Item> getApproximateQueueItemsQuickly() {
        return filterQueue(Jenkins.get().getQueue().getApproximateItemsQuickly());
    }

    /**
     * Returns the path relative to the context root.
     *
     * Doesn't start with '/' but ends with '/' (except returns
     * empty string when this is the default view).
     */
    public String getUrl() {
        return isDefault() ? (owner != null ? owner.getUrl() : "") : getViewUrl();
    }

    /**
     * Same as {@link #getUrl()} except this returns a view/{name} path
     * even for the default view.
     */
    public String getViewUrl() {
        return (owner != null ? owner.getUrl() : "") + "view/" + Util.rawEncode(getViewName()) + '/';
    }

    @Override public String toString() {
        return super.toString() + "[" + getViewUrl() + "]";
    }

    @Override
    public String getSearchUrl() {
        return getUrl();
    }

    @Override
    public String getSearchIcon() {
        return "symbol-jobs";
    }

    @Override
    public SearchGroup getSearchGroup() {
        return SearchGroup.get(SearchGroup.ViewSearchGroup.class);
    }

    /**
     * Returns the transient {@link Action}s associated with the top page.
     *
     * <p>
     * If views don't want to show top-level actions, this method
     * can be overridden to return different objects.
     *
     * @see Jenkins#getActions()
     */
    public List<Action> getActions() {
        List<Action> result = new ArrayList<>();
        result.addAll(getOwner().getViewActions());
        result.addAll(TransientViewActionFactory.createAllFor(this));
        return result;
    }

    /**
     * No-op. Included to maintain backwards compatibility.
     * @deprecated This method does nothing and should not be used
     */
    @Restricted(DoNotUse.class)
    @Deprecated
    public void updateTransientActions() {}

    public Object getDynamic(String token) {
        for (Action a : getActions()) {
            String url = a.getUrlName();
            if (url == null)  continue;
            if (url.equals(token))
                return a;
        }
        return null;
    }

    /**
     * Gets the absolute URL of this view.
     */
    @Exported(visibility = 2, name = "url")
    public String getAbsoluteUrl() {
        return Jenkins.get().getRootUrl() + getUrl();
    }

    public Api getApi() {
        return new Api(this);
    }

    /**
     * Returns the page to redirect the user to, after the view is created.
     *
     * The returned string is appended to "/view/foobar/", so for example
     * to direct the user to the top page of the view, return "", etc.
     */
    public String getPostConstructLandingPage() {
        return "configure";
    }

    /**
     * Returns the {@link ACL} for this object.
     */
    @NonNull
    @Override
    public ACL getACL() {
        return Jenkins.get().getAuthorizationStrategy().getACL(this);
    }

    /** @deprecated Does not work properly with moved jobs. Use {@link ItemListener#onLocationChanged} instead. */
    @Deprecated
    public void onJobRenamed(Item item, String oldName, String newName) {}

    void addDisplayNamesToSearchIndex(SearchIndexBuilder sib, Collection<TopLevelItem> items) {
        for (TopLevelItem item : items) {

            if (LOGGER.isLoggable(Level.FINE)) {
                LOGGER.fine(String.format("Adding url=%s,displayName=%s",
                            item.getSearchUrl(), item.getDisplayName()));
            }
            sib.add(item.getSearchUrl(), item.getDisplayName());
        }
    }

    /**
     * Add a simple CollectionSearchIndex object to sib
     *
     * @param sib the SearchIndexBuilder
     * @since 2.200
     */
    protected void makeSearchIndex(SearchIndexBuilder sib) {
        sib.add(new CollectionSearchIndex<TopLevelItem>() { // for jobs in the view
            @Override
            protected TopLevelItem get(String key) { return getItem(key); }

            @Override
            protected Collection<TopLevelItem> all() { return getItems(); }

            @Override
            protected String getName(TopLevelItem o) {
                // return the name instead of the display for suggestion searching
                return o.getName();
            }
        });
    }

    @Override
    public SearchIndexBuilder makeSearchIndex() {
        SearchIndexBuilder sib = super.makeSearchIndex();
        makeSearchIndex(sib);

        // add the display name for each item in the search index
        addDisplayNamesToSearchIndex(sib, getItems());

        return sib;
    }

    /**
     * Accepts the new description.
     */
    @RequirePOST
    public synchronized void doSubmitDescription(StaplerRequest2 req, StaplerResponse2 rsp) throws IOException, ServletException {
        if (Util.isOverridden(View.class, getClass(), "doSubmitDescription", StaplerRequest.class, StaplerResponse.class)) {
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
        doSubmitDescriptionImpl(StaplerRequest.toStaplerRequest2(req), StaplerResponse.toStaplerResponse2(rsp));
    }

    private void doSubmitDescriptionImpl(StaplerRequest2 req, StaplerResponse2 rsp) throws IOException {
        checkPermission(CONFIGURE);

        description = req.getParameter("description");
        save();
        rsp.sendRedirect(".");  // go to the top page
    }

    /**
     * Accepts submission from the configuration page.
     *
     * Subtypes should override the {@link #submit(StaplerRequest2)} method.
     */
    @POST
    public final synchronized void doConfigSubmit(StaplerRequest2 req, StaplerResponse2 rsp) throws IOException, ServletException, FormException {
        checkPermission(CONFIGURE);

        submit(req);

        var json = req.getSubmittedForm();
        setDescription(json.optString("description"));
        setFilterExecutors(json.optBoolean("filterExecutors"));
        setFilterQueue(json.optBoolean("filterQueue"));
        rename(req.getParameter("name"));

        getProperties().rebuild(req, json, getApplicablePropertyDescriptors());

        save();

        FormApply.success("../" + Util.rawEncode(name)).generateResponse(req, rsp, this);
    }

    /**
     * Handles the configuration submission.
     *
     * Load view-specific properties here.
     */
    protected /* abstract */ void submit(StaplerRequest2 req) throws IOException, ServletException, FormException {
        if (Util.isOverridden(View.class, getClass(), "submit", StaplerRequest.class)) {
            try {
                submit(StaplerRequest.fromStaplerRequest2(req));
            } catch (javax.servlet.ServletException e) {
                throw ServletExceptionWrapper.toJakartaServletException(e);
            }
        } else {
            throw new AbstractMethodError("The class " + getClass().getName() + " must override at least one of the "
                    + View.class.getSimpleName() + ".submit methods");
        }
    }

    /**
     * @deprecated use {@link #submit(StaplerRequest2)}
     */
    @Deprecated
    protected void submit(StaplerRequest req) throws IOException, javax.servlet.ServletException, FormException {
        if (Util.isOverridden(View.class, getClass(), "submit", StaplerRequest2.class)) {
            try {
                submit(StaplerRequest.toStaplerRequest2(req));
            } catch (ServletException e) {
                throw ServletExceptionWrapper.fromJakartaServletException(e);
            }
        } else {
            throw new AbstractMethodError("The class " + getClass().getName() + " must override at least one of the "
                    + View.class.getSimpleName() + ".submit methods");
        }
    }


    /**
     * Deletes this view.
     */
    @RequirePOST
    public synchronized void doDoDelete(StaplerRequest2 req, StaplerResponse2 rsp) throws IOException, ServletException {
        checkPermission(DELETE);

        owner.deleteView(this);

        rsp.sendRedirect2(req.getContextPath() + "/" + owner.getUrl());
    }


    /**
     * Creates a new {@link Item} in this collection.
     *
     * <p>
     * This method should call {@link ModifiableItemGroup#doCreateItem(StaplerRequest2, StaplerResponse2)}
     * and then add the newly created item to this view.
     *
     * @return
     *      null if fails.
     * @since 2.475
     */
    @RequirePOST
    public /* abstract */ Item doCreateItem(StaplerRequest2 req, StaplerResponse2 rsp) throws IOException, ServletException {
        if (Util.isOverridden(View.class, getClass(), "doCreateItem", StaplerRequest.class, StaplerResponse.class)) {
            try {
                return doCreateItem(StaplerRequest.fromStaplerRequest2(req), StaplerResponse.fromStaplerResponse2(rsp));
            } catch (javax.servlet.ServletException e) {
                throw ServletExceptionWrapper.toJakartaServletException(e);
            }
        } else {
            throw new AbstractMethodError("The class " + getClass().getName() + " must override at least one of the "
                    + View.class.getSimpleName() + ".doCreateItem methods");
        }
    }

    /**
     * @deprecated use {@link #doCreateItem(StaplerRequest2, StaplerResponse2)}
     */
    @Deprecated
    @StaplerNotDispatchable
    public Item doCreateItem(StaplerRequest req, StaplerResponse rsp) throws IOException, javax.servlet.ServletException {
        if (Util.isOverridden(View.class, getClass(), "doCreateItem", StaplerRequest2.class, StaplerResponse2.class)) {
            try {
                return doCreateItem(StaplerRequest.toStaplerRequest2(req), StaplerResponse.toStaplerResponse2(rsp));
            } catch (ServletException e) {
                throw ServletExceptionWrapper.fromJakartaServletException(e);
            }
        } else {
            throw new AbstractMethodError("The class " + getClass().getName() + " must override at least one of the "
                    + View.class.getSimpleName() + ".doCreateItem methods");
        }
    }

    /**
     * Makes sure that the given name is good as a job name.
     * For use from {@code newJob}.
     */
    @Restricted(DoNotUse.class) // called from newJob view
    public FormValidation doCheckJobName(@QueryParameter String value) {
        // this method can be used to check if a file exists anywhere in the file system,
        // so it should be protected.
        getOwner().checkPermission(Item.CREATE);

        if (Util.fixEmpty(value) == null) {
            return FormValidation.ok();
        }

        try {
            Jenkins.checkGoodName(value);
            value = value.trim(); // why trim *after* checkGoodName? not sure, but ItemGroupMixIn.createTopLevelItem does the same
            ItemGroup<?> parent = getOwner().getItemGroup();
            Jenkins.get().getProjectNamingStrategy().checkName(parent.getFullName(), value);
        } catch (Failure e) {
            return FormValidation.error(e.getMessage());
        }

        if (getOwner().getItemGroup().getItem(value) != null) {
            return FormValidation.error(Messages.Hudson_JobAlreadyExists(value));
        }

        // looks good
        return FormValidation.ok();
    }

    /**
     * An API REST method to get the allowed {$link TopLevelItem}s and its categories.
     *
     * @return A {@link Categories} entity that is shown as JSON file.
     */
    @Restricted(DoNotUse.class)
    public Categories doItemCategories(StaplerRequest2 req, StaplerResponse2 rsp, @QueryParameter String iconStyle) throws IOException, ServletException {
        getOwner().checkPermission(Item.CREATE);

        rsp.addHeader("Cache-Control", "no-cache, no-store, must-revalidate");
        rsp.addHeader("Pragma", "no-cache");
        rsp.addHeader("Expires", "0");
        Categories categories = new Categories();
        int order = 0;
        String resUrl;

        if (iconStyle != null && !iconStyle.isBlank()) {
            resUrl = req.getContextPath() + Jenkins.RESOURCE_PATH;
        } else {
            resUrl = null;
        }
        for (TopLevelItemDescriptor descriptor : DescriptorVisibilityFilter.apply(getOwner().getItemGroup(), Items.all2(Jenkins.getAuthentication2(), getOwner().getItemGroup()))) {
            ItemCategory ic = ItemCategory.getCategory(descriptor);
            Map<String, Serializable> metadata = new HashMap<>();

            // Information about Item.
            metadata.put("class", descriptor.getId());
            metadata.put("order", ++order);
            metadata.put("displayName", descriptor.getDisplayName());
            metadata.put("description", descriptor.getDescription());
            metadata.put("iconFilePathPattern", descriptor.getIconFilePathPattern());
            String iconClassName = descriptor.getIconClassName();
            if (iconClassName != null && !iconClassName.isBlank()) {
                metadata.put("iconClassName", iconClassName);
                if (iconClassName.startsWith("symbol-")) {
                    String iconXml = Symbol.get(new SymbolRequest.Builder()
                            .withName(iconClassName.split(" ")[0].substring(7))
                            .withPluginName(Functions.extractPluginNameFromIconSrc(iconClassName))
                            .withClasses("icon-xlg")
                            .build());
                    metadata.put("iconXml", iconXml);
                } else {
                    if (resUrl != null) {
                        Icon icon = IconSet.icons
                                .getIconByClassSpec(String.join(" ", iconClassName, iconStyle));
                        if (icon != null) {
                            metadata.put("iconQualifiedUrl", icon.getQualifiedUrl(resUrl));
                        }
                    }
                }
            }

            Category category = categories.getItem(ic.getId());
            if (category != null) {
                category.getItems().add(metadata);
            } else {
                List<Map<String, Serializable>> temp = new ArrayList<>();
                temp.add(metadata);
                category = new Category(ic.getId(), ic.getDisplayName(), ic.getDescription(), ic.getOrder(), ic.getMinToShow(), temp);
                categories.getItems().add(category);
            }
        }
        return categories;
    }

    public void doRssAll(StaplerRequest2 req, StaplerResponse2 rsp) throws IOException, ServletException {
        RSS.rss(req, rsp, "Jenkins:" + getDisplayName() + " (all builds)", getUrl(), getBuilds().newBuilds());
    }

    public void doRssFailed(StaplerRequest2 req, StaplerResponse2 rsp) throws IOException, ServletException {
        RSS.rss(req, rsp, "Jenkins:" + getDisplayName() + " (failed builds)", getUrl(), getBuilds().failureOnly().newBuilds());
    }

    public RunList getBuilds() {
        return new RunList(this);
    }

    @Deprecated
    @Restricted(DoNotUse.class)
    public BuildTimelineWidget getTimeline() {
        return new BuildTimelineWidget(getBuilds());
    }

    public void doRssLatest(StaplerRequest2 req, StaplerResponse2 rsp) throws IOException, ServletException {
        List<Run> lastBuilds = new ArrayList<>();
        for (TopLevelItem item : getItems()) {
            if (item instanceof Job job) {
                Run lb = job.getLastBuild();
                if (lb != null)    lastBuilds.add(lb);
            }
        }
        RSS.rss(req, rsp, "Jenkins:" + getDisplayName() + " (latest builds)", getUrl(), RunList.fromRuns(lastBuilds), Run.FEED_ADAPTER_LATEST);
    }

    /**
     * Accepts {@code config.xml} submission, as well as serve it.
     *
     * @since 2.475
     */
    @WebMethod(name = "config.xml")
    public HttpResponse doConfigDotXml(StaplerRequest2 req) throws IOException {
        if (Util.isOverridden(View.class, getClass(), "doConfigDotXml", StaplerRequest.class)) {
            return doConfigDotXml(StaplerRequest.fromStaplerRequest2(req));
        } else {
            return doConfigDotXmlImpl(req);
        }
    }

    /**
     * @deprecated use {@link #doConfigDotXml(StaplerRequest2)}
     */
    @Deprecated
    @StaplerNotDispatchable
    public HttpResponse doConfigDotXml(StaplerRequest req) throws IOException {
        return doConfigDotXmlImpl(StaplerRequest.toStaplerRequest2(req));
    }

    private HttpResponse doConfigDotXmlImpl(StaplerRequest2 req) throws IOException {
        if (req.getMethod().equals("GET")) {
            // read
            checkPermission(READ);
            return new HttpResponse() {
                @Override
                public void generateResponse(StaplerRequest2 req, StaplerResponse2 rsp, Object node) throws IOException, ServletException {
                    rsp.setContentType("application/xml");
                    if (hasPermission(CONFIGURE)) {
                        View.this.writeXml(rsp.getOutputStream());
                    } else {
                        var baos = new ByteArrayOutputStream();
                        View.this.writeXml(baos);
                        String xml = baos.toString(StandardCharsets.UTF_8);

                        xml = ExtendedReadRedaction.applyAll(xml);
                        org.apache.commons.io.IOUtils.write(xml, rsp.getOutputStream(), StandardCharsets.UTF_8);
                    }
                }
            };
        }
        if (req.getMethod().equals("POST")) {
            // submission
            updateByXml(new StreamSource(req.getReader()));
            return HttpResponses.ok();
        }

        // huh?
        return HttpResponses.error(SC_BAD_REQUEST, "Unexpected request method " + req.getMethod());
    }

    /**
     * @since 1.538
     */
    public void writeXml(OutputStream out) throws IOException {
        // pity we don't have a handy way to clone Jenkins.XSTREAM to temp add the omit Field
        XStream2 xStream2 = new XStream2();
        xStream2.omitField(View.class, "owner");
        xStream2.toXMLUTF8(View.this,  out);
    }

    /**
     * Updates the View with the new XML definition.
     * @param source source of the Item's new definition.
     *               The source should be either a {@link StreamSource} or {@link SAXSource}, other sources
     *               may not be handled.
     */
    public void updateByXml(Source source) throws IOException {
        checkPermission(CONFIGURE);
        StringWriter out = new StringWriter();
        try {
            // this allows us to use UTF-8 for storing data,
            // plus it checks any well-formedness issue in the submitted
            // data
            XMLUtils.safeTransform(source, new StreamResult(out));
            out.close();
        } catch (TransformerException | SAXException e) {
            throw new IOException("Failed to persist configuration.xml", e);
        }

        // try to reflect the changes by reloading
        try (InputStream in = new BufferedInputStream(new ByteArrayInputStream(out.toString().getBytes(StandardCharsets.UTF_8)))) {
            // Do not allow overwriting view name as it might collide with another
            // view in same ViewGroup and might not satisfy Jenkins.checkGoodName.
            String oldname = name;
            ViewGroup oldOwner = owner; // oddly, this field is not transient
            Object o = Jenkins.XSTREAM2.unmarshal(XStream2.getDefaultDriver().createReader(in), this, null, true);
            if (!o.getClass().equals(getClass())) {
                // ensure that we've got the same view type. extending this code to support updating
                // to different view type requires destroying & creating a new view type
                throw new IOException("Expecting view type: " + this.getClass() + " but got: " + o.getClass() + " instead." +
                    "\nShould you needed to change to a new view type, you must first delete and then re-create " +
                    "the view with the new view type.");
            }
            name = oldname;
            owner = oldOwner;
        } catch (StreamException | ConversionException | Error e) { // mostly reflection errors
            throw new IOException("Unable to read", e);
        }
        save();
    }

    @Override
    public ModelObjectWithContextMenu.ContextMenu doChildrenContextMenu(StaplerRequest2 request, StaplerResponse2 response) throws Exception {
        ModelObjectWithContextMenu.ContextMenu m = new ModelObjectWithContextMenu.ContextMenu();
        for (TopLevelItem i : getItems())
            m.add(Functions.getRelativeLinkTo(i), Functions.getRelativeDisplayNameFrom(i, getOwner().getItemGroup()));
        return m;
    }

    /**
     * A list of available view types.
     * @deprecated as of 1.286
     *      Use {@link #all()} for read access, and use {@link Extension} for registration.
     */
    @Deprecated
    public static final DescriptorList<View> LIST = new DescriptorList<>(View.class);

    /**
     * Returns all the registered {@link ViewDescriptor}s.
     */
    public static DescriptorExtensionList<View, ViewDescriptor> all() {
        return Jenkins.get().getDescriptorList(View.class);
    }

    /**
     * Returns the {@link ViewDescriptor} instances that can be instantiated for the {@link ViewGroup} in the current
     * {@link StaplerRequest2}.
     * <p>
     * <strong>NOTE: Historically this method is only ever called from a {@link StaplerRequest2}</strong>
     * @return the list of instantiable {@link ViewDescriptor} instances for the current {@link StaplerRequest2}
     */
    @NonNull
    public static List<ViewDescriptor> allInstantiable() {
        List<ViewDescriptor> r = new ArrayList<>();
        StaplerRequest2 request = Stapler.getCurrentRequest2();
        if (request == null) {
            throw new IllegalStateException("This method can only be invoked from a stapler request");
        }
        ViewGroup owner = request.findAncestorObject(ViewGroup.class);
        if (owner == null) {
            throw new IllegalStateException("This method can only be invoked from a request with a ViewGroup ancestor");
        }
        for (ViewDescriptor d : DescriptorVisibilityFilter.apply(owner, all())) {
            if (d.isApplicableIn(owner) && d.isInstantiable()
                    && owner.getACL().hasCreatePermission2(Jenkins.getAuthentication2(), owner, d)) {
                r.add(d);
            }
        }
        return r;
    }

    public static final Comparator<View> SORTER = Comparator.comparing(View::getViewName);

    public static final PermissionGroup PERMISSIONS = new PermissionGroup(View.class, Messages._View_Permissions_Title());
    /**
     * Permission to create new views.
     */
    public static final Permission CREATE = new Permission(PERMISSIONS, "Create", Messages._View_CreatePermission_Description(), Permission.CREATE, PermissionScope.ITEM_GROUP);
    public static final Permission DELETE = new Permission(PERMISSIONS, "Delete", Messages._View_DeletePermission_Description(), Permission.DELETE, PermissionScope.ITEM_GROUP);
    public static final Permission CONFIGURE = new Permission(PERMISSIONS, "Configure", Messages._View_ConfigurePermission_Description(), Permission.CONFIGURE, PermissionScope.ITEM_GROUP);
    public static final Permission READ = new Permission(PERMISSIONS, "Read", Messages._View_ReadPermission_Description(), Permission.READ, PermissionScope.ITEM_GROUP);

    @SuppressFBWarnings(value = "RV_RETURN_VALUE_IGNORED_NO_SIDE_EFFECT", justification = "to guard against potential future compiler optimizations")
    @Initializer(before = InitMilestone.SYSTEM_CONFIG_LOADED)
    @Restricted(DoNotUse.class)
    public static void registerPermissions() {
        // Pending JENKINS-17200, ensure that the above permissions have been registered prior to
        // allowing plugins to adapt the system configuration, which may depend on these permissions
        // having been registered. Since this method is static and since it follows the above
        // construction of static permission objects (and therefore their calls to
        // PermissionGroup#register), there is nothing further to do in this method. We call
        // Objects.hash() to guard against potential future compiler optimizations.
        Objects.hash(PERMISSIONS, CREATE, DELETE, CONFIGURE, READ);
    }

    // to simplify access from Jelly
    public static Permission getItemCreatePermission() {
        return Item.CREATE;
    }

    /**
     * @since 2.475
     */
    public static View create(StaplerRequest2 req, StaplerResponse2 rsp, ViewGroup owner)
            throws FormException, IOException, ServletException {
        String mode = req.getParameter("mode");

        String requestContentType = req.getContentType();
        if (requestContentType == null
                && !(mode != null && mode.equals("copy")))
            throw new Failure("No Content-Type header set");

        boolean isXmlSubmission = requestContentType != null
                && (requestContentType.startsWith("application/xml")
                        || requestContentType.startsWith("text/xml"));

        String name = req.getParameter("name");
        Jenkins.checkGoodName(name);
        if (owner.getView(name) != null)
            throw new Failure(Messages.Hudson_ViewAlreadyExists(name));

        if (mode == null || mode.isEmpty()) {
            if (isXmlSubmission) {
                View v = createViewFromXML(name, req.getInputStream());
                owner.getACL().checkCreatePermission(owner, v.getDescriptor());
                v.owner = owner;
                rsp.setStatus(HttpServletResponse.SC_OK);
                return v;
            } else
                throw new Failure(Messages.View_MissingMode());
        }

        View v;
        if ("copy".equals(mode)) {
            v = copy(req, owner, name);
        } else {
            ViewDescriptor descriptor = all().findByName(mode);
            if (descriptor == null) {
                throw new Failure("No view type ‘" + mode + "’ is known");
            }

            // create a view
            JSONObject submittedForm = req.getSubmittedForm();
            submittedForm.put("name", name);
            v = descriptor.newInstance(req, submittedForm);
        }
        owner.getACL().checkCreatePermission(owner, v.getDescriptor());
        v.owner = owner;

        // redirect to the config screen
        rsp.sendRedirect2(req.getContextPath() + '/' + v.getUrl() + v.getPostConstructLandingPage());

        return v;
    }

    /**
     * @deprecated use {@link #create(StaplerRequest2, StaplerResponse2, ViewGroup)}
     */
    @Deprecated
    public static View create(StaplerRequest req, StaplerResponse rsp, ViewGroup owner)
            throws FormException, IOException, javax.servlet.ServletException {
        try {
            return create(StaplerRequest.toStaplerRequest2(req), StaplerResponse.toStaplerResponse2(rsp), owner);
        } catch (ServletException e) {
            throw ServletExceptionWrapper.fromJakartaServletException(e);
        }
    }

    private static View copy(StaplerRequest2 req, ViewGroup owner, String name) throws IOException {
        View v;
        String from = req.getParameter("from");
        View src = owner.getView(from);

        if (src == null) {
            if (Util.fixEmpty(from) == null)
                throw new Failure("Specify which view to copy");
            else
                throw new Failure("No such view: " + from);
        }
        String xml = Jenkins.XSTREAM.toXML(src);
        v = createViewFromXML(name, new ByteArrayInputStream(xml.getBytes(Charset.defaultCharset())));
        return v;
    }

    /**
     * Instantiate View subtype from XML stream.
     *
     * @param name Alternative name to use or {@code null} to keep the one in xml.
     */
    public static View createViewFromXML(String name, InputStream xml) throws IOException {

        try (InputStream in = new BufferedInputStream(xml)) {
            View v = (View) Jenkins.XSTREAM.fromXML(in);
            if (name != null) v.name = name;
            Jenkins.checkGoodName(v.name);
            return v;
        } catch (StreamException | ConversionException | Error e) { // mostly reflection errors
            throw new IOException("Unable to read", e);
        }
    }

    public static class PropertyList extends DescribableList<ViewProperty, ViewPropertyDescriptor> {
        private PropertyList(View owner) {
            super(owner);
        }

        public PropertyList() {// needed for XStream deserialization
        }

        public View getOwner() {
            return (View) owner;
        }

        @Override
        protected void onModified() throws IOException {
            for (ViewProperty p : this)
                p.setView(getOwner());
        }
    }

    /**
     * "Job" in "New Job". When a view is used in a context that restricts the child type,
     * It might be useful to override this.
     */
    public static final Message<View> NEW_PRONOUN = new Message<>();

    private static final Logger LOGGER = Logger.getLogger(View.class.getName());
}
