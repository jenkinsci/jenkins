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

import com.thoughtworks.xstream.converters.ConversionException;
import com.thoughtworks.xstream.io.StreamException;
import com.thoughtworks.xstream.io.xml.XppDriver;
import hudson.DescriptorExtensionList;
import hudson.Extension;
import hudson.ExtensionPoint;
import hudson.Functions;
import hudson.Indenter;
import hudson.Util;
import hudson.model.Descriptor.FormException;
import hudson.model.labels.LabelAtomPropertyDescriptor;
import hudson.model.listeners.ItemListener;
import hudson.scm.ChangeLogSet;
import hudson.scm.ChangeLogSet.Entry;
import hudson.search.CollectionSearchIndex;
import hudson.search.SearchIndexBuilder;
import hudson.security.ACL;
import hudson.security.AccessControlled;
import hudson.security.Permission;
import hudson.security.PermissionGroup;
import hudson.security.PermissionScope;
import hudson.tasks.UserAvatarResolver;
import hudson.util.AlternativeUiTextProvider;
import hudson.util.AlternativeUiTextProvider.Message;
import hudson.util.DescribableList;
import hudson.util.DescriptorList;
import hudson.util.FormApply;
import hudson.util.RunList;
import hudson.util.XStream2;
import hudson.views.ListViewColumn;
import hudson.widgets.Widget;
import jenkins.model.Jenkins;
import jenkins.model.ModelObjectWithChildren;
import jenkins.util.ProgressiveRendering;
import jenkins.util.xml.XMLUtils;

import net.sf.json.JSON;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.apache.tools.ant.filters.StringInputStream;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.HttpResponses;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.WebMethod;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;
import org.kohsuke.stapler.interceptor.RequirePOST;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletResponse;
import javax.xml.transform.Source;
import javax.xml.transform.TransformerException;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.logging.Level;
import java.util.logging.Logger;

import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST;
import static jenkins.model.Jenkins.*;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
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
 * {@link View} subtypes need the <tt>newViewDetail.jelly</tt> page,
 * which is included in the "new view" page. This page should have some
 * description of what the view is about. 
 * </ul>
 *
 * @author Kohsuke Kawaguchi
 * @see ViewDescriptor
 * @see ViewGroup
 */
@ExportedBean
public abstract class View extends AbstractModelObject implements AccessControlled, Describable<View>, ExtensionPoint, Saveable, ModelObjectWithChildren {

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
    
    protected transient List<Action> transientActions;

    /**
     * List of {@link ViewProperty}s configured for this view.
     * @since 1.406
     */
    private volatile DescribableList<ViewProperty,ViewPropertyDescriptor> properties = new PropertyList(this);

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
    @Exported(name="jobs")
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
            final Collection<TopLevelItem> items = new LinkedHashSet<TopLevelItem>(getItems());

            for(View view: ((ViewGroup) this).getViews()) {
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
        return getOwnerItemGroup().getItem(name);
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
    @Exported(visibility=2,name="name")
    public String getViewName() {
        return name;
    }

    /**
     * Renames this view.
     */
    public void rename(String newName) throws Failure, FormException {
        if(name.equals(newName))    return; // noop
        checkGoodName(newName);
        if(owner.getView(newName)!=null)
            throw new FormException(Messages.Hudson_ViewAlreadyExists(newName),"name");
        String oldName = name;
        name = newName;
        owner.onViewRenamed(this,oldName,newName);
    }

    /**
     * Gets the {@link ViewGroup} that this view belongs to.
     */
    public ViewGroup getOwner() {
        return owner;
    }

    /**
     * Backward-compatible way of getting {@code getOwner().getItemGroup()}
     */
    public ItemGroup<? extends TopLevelItem> getOwnerItemGroup() {
        try {
            return owner.getItemGroup();
        } catch (AbstractMethodError e) {
            return Jenkins.getInstance();
        }
    }

    public View getOwnerPrimaryView() {
        try {
            return _getOwnerPrimaryView();
        } catch (AbstractMethodError e) {
            return null;
        }
    }

    private View _getOwnerPrimaryView() {
        return owner.getPrimaryView();
    }

    public List<Action> getOwnerViewActions() {
        try {
            return _getOwnerViewActions();
        } catch (AbstractMethodError e) {
            return Jenkins.getInstance().getActions();
        }
    }

    private List<Action> _getOwnerViewActions() {
        return owner.getViewActions();
    }

    /**
     * Message displayed in the top page. Can be null. Includes HTML.
     */
    @Exported
    public String getDescription() {
        return description;
    }
    
    /**
     * Gets the view properties configured for this view.
     * @since 1.406
     */
    public DescribableList<ViewProperty,ViewPropertyDescriptor> getProperties() {
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
     * Returns all the {@link LabelAtomPropertyDescriptor}s that can be potentially configured
     * on this label.
     */
    public List<ViewPropertyDescriptor> getApplicablePropertyDescriptors() {
        List<ViewPropertyDescriptor> r = new ArrayList<ViewPropertyDescriptor>();
        for (ViewPropertyDescriptor pd : ViewProperty.all()) {
            if (pd.isEnabledFor(this))
                r.add(pd);
        }
        return r;
    }

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
    @Exported(name="property",inline=true)
    public List<ViewProperty> getAllProperties() {
        return getProperties().toList();
    }

    public ViewDescriptor getDescriptor() {
        return (ViewDescriptor) Jenkins.getInstance().getDescriptorOrDie(getClass());
    }

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
     * Enables or disables automatic refreshes of the view.
     * By default, automatic refreshes are enabled.
     * @since 1.557
     */
    public boolean isAutomaticRefreshEnabled() {
        return true;
    }
    
    /**
     * If true, only show relevant executors
     */
    public boolean isFilterExecutors() {
        return filterExecutors;
    }
    
    /**
     * If true, only show relevant queue items
     */
    public boolean isFilterQueue() {
        return filterQueue;
    }

    /**
     * Gets the {@link Widget}s registered on this object.
     *
     * <p>
     * For now, this just returns the widgets registered to Hudson.
     */
    public List<Widget> getWidgets() {
        return Collections.unmodifiableList(Jenkins.getInstance().getWidgets());
    }

    /**
     * If this view uses &lt;t:projectView> for rendering, this method returns columns to be displayed.
     */
    public Iterable<? extends ListViewColumn> getColumns() {
        return ListViewColumn.createDefaultInitialColumnList();
    }

    /**
     * If this view uses &lt;t:projectView> for rendering, this method returns the indenter used
     * to indent each row.
     */
    public Indenter getIndenter() {
        return null;
    }

    /**
     * If true, this is a view that renders the top page of Hudson.
     */
    public boolean isDefault() {
        return getOwnerPrimaryView()==this;
    }
    
    public List<Computer> getComputers() {
        Computer[] computers = Jenkins.getInstance().getComputers();

        if (!isFilterExecutors()) {
            return Arrays.asList(computers);
        }

        List<Computer> result = new ArrayList<Computer>();

        HashSet<Label> labels = new HashSet<Label>();
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
        if (labels.contains(null) && node.getMode() == Mode.NORMAL) return true;

        for (Label l : labels)
            if (l != null && l.contains(node))
                return true;
        return false;
    }

    private List<Queue.Item> filterQueue(List<Queue.Item> base) {
        if (!isFilterQueue()) {
            return base;
        }

        Collection<TopLevelItem> items = getItems();
        List<Queue.Item> result = new ArrayList<Queue.Item>();
        for (Queue.Item qi : base) {
            if (items.contains(qi.task)) {
                result.add(qi);
            } else
            if (qi.task instanceof AbstractProject<?, ?>) {
                AbstractProject<?,?> project = (AbstractProject<?, ?>) qi.task;
                if (items.contains(project.getRootProject())) {
                    result.add(qi);
                }
            }
        }
        return result;
    }

    public List<Queue.Item> getQueueItems() {
        return filterQueue(Arrays.asList(Jenkins.getInstance().getQueue().getItems()));
    }

    public List<Queue.Item> getApproximateQueueItemsQuickly() {
        return filterQueue(Jenkins.getInstance().getQueue().getApproximateItemsQuickly());
    }

    /**
     * Returns the path relative to the context root.
     *
     * Doesn't start with '/' but ends with '/' (except returns
     * empty string when this is the default view).
     */
    public String getUrl() {
        return isDefault() ? (owner!=null ? owner.getUrl() : "") : getViewUrl();
    }

    /**
     * Same as {@link #getUrl()} except this returns a view/{name} path
     * even for the default view.
     */
    public String getViewUrl() {
        return (owner!=null ? owner.getUrl() : "") + "view/" + Util.rawEncode(getViewName()) + '/';
    }

    @Override public String toString() {
        return super.toString() + "[" + getViewUrl() + "]";
    }

    public String getSearchUrl() {
        return getUrl();
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
    	List<Action> result = new ArrayList<Action>();
    	result.addAll(getOwnerViewActions());
    	synchronized (this) {
    		if (transientActions == null) {
                updateTransientActions();
    		}
    		result.addAll(transientActions);
    	}
    	return result;
    }
    
    public synchronized void updateTransientActions() {
        transientActions = TransientViewActionFactory.createAllFor(this); 
    }
    
    public Object getDynamic(String token) {
        for (Action a : getActions()) {
            String url = a.getUrlName();
            if (url==null)  continue;
            if(a.getUrlName().equals(token))
                return a;
        }
        return null;
    }

    /**
     * Gets the absolute URL of this view.
     */
    @Exported(visibility=2,name="url")
    public String getAbsoluteUrl() {
        return Jenkins.getInstance().getRootUrl()+getUrl();
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
    public ACL getACL() {
        return Jenkins.getInstance().getAuthorizationStrategy().getACL(this);
    }

    public void checkPermission(Permission p) {
        getACL().checkPermission(p);
    }

    public boolean hasPermission(Permission p) {
        return getACL().hasPermission(p);
    }

    /**
     * Get view by full name.
     * @param name '/' separated list of nested view names.
     * @param context ViewGroup to start with. Use <tt>Jenkins.getInstance()</tt> for top level views.
     * @since 1.558
     */
    public static View getViewByFullName(String name, ViewGroup context) {

        View view = null;

        final StringTokenizer tok = new StringTokenizer(name, "/");
        while(tok.hasMoreTokens()) {

            String viewName = tok.nextToken();

            view = context.getView(viewName);
            if (view == null) throw new IllegalArgumentException(String.format(
                    "No view named %s inside view %s",
                    viewName, context.getDisplayName()
            ));

            view.checkPermission(View.READ);

            if (view instanceof ViewGroup) {
                context = (ViewGroup) view;
            } else if (tok.hasMoreTokens()) {
                throw new IllegalArgumentException(
                        view.getViewName() + " view can not contain views"
                );
            }
        }

        return view;
    }

    /** @deprecated Does not work properly with moved jobs. Use {@link ItemListener#onLocationChanged} instead. */
    @Deprecated
    public void onJobRenamed(Item item, String oldName, String newName) {}

    @ExportedBean(defaultVisibility=2)
    public static final class UserInfo implements Comparable<UserInfo> {
        private final User user;
        /**
         * When did this user made a last commit on any of our projects? Can be null.
         */
        private Calendar lastChange;
        /**
         * Which project did this user commit? Can be null.
         */
        private AbstractProject project;

        /** @see UserAvatarResolver */
        String avatar;

        UserInfo(User user, AbstractProject p, Calendar lastChange) {
            this.user = user;
            this.project = p;
            this.lastChange = lastChange;
        }

        @Exported
        public User getUser() {
            return user;
        }

        @Exported
        public Calendar getLastChange() {
            return lastChange;
        }

        @Exported
        public AbstractProject getProject() {
            return project;
        }

        /**
         * Returns a human-readable string representation of when this user was last active.
         */
        public String getLastChangeTimeString() {
            if(lastChange==null)    return "N/A";
            long duration = new GregorianCalendar().getTimeInMillis()- ordinal();
            return Util.getTimeSpanString(duration);
        }

        public String getTimeSortKey() {
            if(lastChange==null)    return "-";
            return Util.XS_DATETIME_FORMATTER.format(lastChange.getTime());
        }

        public int compareTo(UserInfo that) {
            long rhs = that.ordinal();
            long lhs = this.ordinal();
            if(rhs>lhs) return 1;
            if(rhs<lhs) return -1;
            return 0;
        }

        private long ordinal() {
            if(lastChange==null)    return 0;
            return lastChange.getTimeInMillis();
        }
    }

    /**
     * Does this {@link View} has any associated user information recorded?
     * @deprecated Potentially very expensive call; do not use from Jelly views.
     */
    @Deprecated
    public boolean hasPeople() {
        return People.isApplicable(getItems());
    }

    /**
     * Gets the users that show up in the changelog of this job collection.
     */
    public People getPeople() {
        return new People(this);
    }

    /**
     * @since 1.484
     */
    public AsynchPeople getAsynchPeople() {
        return new AsynchPeople(this);
    }

    @ExportedBean
    public static final class People  {
        @Exported
        public final List<UserInfo> users;

        public final ModelObject parent;

        public People(Jenkins parent) {
            this.parent = parent;
            // for Hudson, really load all users
            Map<User,UserInfo> users = getUserInfo(parent.getItems());
            User unknown = User.getUnknown();
            for (User u : User.getAll()) {
                if(u==unknown)  continue;   // skip the special 'unknown' user
                if(!users.containsKey(u))
                    users.put(u,new UserInfo(u,null,null));
            }
            this.users = toList(users);
        }

        public People(View parent) {
            this.parent = parent;
            this.users = toList(getUserInfo(parent.getItems()));
        }

        private Map<User,UserInfo> getUserInfo(Collection<? extends Item> items) {
            Map<User,UserInfo> users = new HashMap<User,UserInfo>();
            for (Item item : items) {
                for (Job job : item.getAllJobs()) {
                    if (job instanceof AbstractProject) {
                        AbstractProject<?,?> p = (AbstractProject) job;
                        for (AbstractBuild<?,?> build : p.getBuilds()) {
                            for (Entry entry : build.getChangeSet()) {
                                User user = entry.getAuthor();

                                UserInfo info = users.get(user);
                                if(info==null)
                                    users.put(user,new UserInfo(user,p,build.getTimestamp()));
                                else
                                if(info.getLastChange().before(build.getTimestamp())) {
                                    info.project = p;
                                    info.lastChange = build.getTimestamp();
                                }
                            }
                        }
                    }
                }
            }
            return users;
        }

        private List<UserInfo> toList(Map<User,UserInfo> users) {
            ArrayList<UserInfo> list = new ArrayList<UserInfo>();
            list.addAll(users.values());
            Collections.sort(list);
            return Collections.unmodifiableList(list);
        }

        public Api getApi() {
            return new Api(this);
        }

        /**
         * @deprecated Potentially very expensive call; do not use from Jelly views.
         */
        @Deprecated
        public static boolean isApplicable(Collection<? extends Item> items) {
            for (Item item : items) {
                for (Job job : item.getAllJobs()) {
                    if (job instanceof AbstractProject) {
                        AbstractProject<?,?> p = (AbstractProject) job;
                        for (AbstractBuild<?,?> build : p.getBuilds()) {
                            for (Entry entry : build.getChangeSet()) {
                                User user = entry.getAuthor();
                                if(user!=null)
                                    return true;
                            }
                        }
                    }
                }
            }
            return false;
        }
    }

    /**
     * Variant of {@link People} which can be displayed progressively, since it may be slow.
     * @since 1.484
     */
    public static final class AsynchPeople extends ProgressiveRendering { // JENKINS-15206

        private final Collection<TopLevelItem> items;
        private final User unknown;
        private final Map<User,UserInfo> users = new HashMap<User,UserInfo>();
        private final Set<User> modified = new HashSet<User>();
        private final String iconSize;
        public final ModelObject parent;

        /** @see Jenkins#getAsynchPeople */
        public AsynchPeople(Jenkins parent) {
            this.parent = parent;
            items = parent.getItems();
            unknown = User.getUnknown();
        }

        /** @see View#getAsynchPeople */
        public AsynchPeople(View parent) {
            this.parent = parent;
            items = parent.getItems();
            unknown = null;
        }

        {
            StaplerRequest req = Stapler.getCurrentRequest();
            iconSize = req != null ? Functions.validateIconSize(Functions.getCookie(req, "iconSize", "32x32")) : "32x32";
        }

        @Override protected void compute() throws Exception {
            int itemCount = 0;
            for (Item item : items) {
                for (Job<?,?> job : item.getAllJobs()) {
                    if (job instanceof AbstractProject) {
                        AbstractProject<?,?> p = (AbstractProject) job;
                        RunList<? extends AbstractBuild<?,?>> builds = p.getBuilds();
                        int buildCount = 0;
                        for (AbstractBuild<?,?> build : builds) {
                            if (canceled()) {
                                return;
                            }
                            for (ChangeLogSet.Entry entry : build.getChangeSet()) {
                                User user = entry.getAuthor();
                                UserInfo info = users.get(user);
                                if (info == null) {
                                    UserInfo userInfo = new UserInfo(user, p, build.getTimestamp());
                                    userInfo.avatar = UserAvatarResolver.resolveOrNull(user, iconSize);
                                    synchronized (this) {
                                        users.put(user, userInfo);
                                        modified.add(user);
                                    }
                                } else if (info.getLastChange().before(build.getTimestamp())) {
                                    synchronized (this) {
                                        info.project = p;
                                        info.lastChange = build.getTimestamp();
                                        modified.add(user);
                                    }
                                }
                            }
                            // TODO consider also adding the user of the UserCause when applicable
                            buildCount++;
                            // TODO this defeats lazy-loading. Should rather do a breadth-first search, as in hudson.plugins.view.dashboard.builds.LatestBuilds
                            // (though currently there is no quick implementation of RunMap.size() ~ idOnDisk.size(), which would be needed for proper progress)
                            progress((itemCount + 1.0 * buildCount / builds.size()) / (items.size() + 1));
                        }
                    }
                }
                itemCount++;
                progress(1.0 * itemCount / (items.size() + /* handling User.getAll */1));
            }
            if (unknown != null) {
                if (canceled()) {
                    return;
                }
                for (User u : User.getAll()) { // TODO nice to have a method to iterate these lazily
                    if (canceled()) {
                        return;
                    }
                    if (u == unknown) {
                        continue;
                    }
                    if (!users.containsKey(u)) {
                        UserInfo userInfo = new UserInfo(u, null, null);
                        userInfo.avatar = UserAvatarResolver.resolveOrNull(u, iconSize);
                        synchronized (this) {
                            users.put(u, userInfo);
                            modified.add(u);
                        }
                    }
                }
            }
        }

        @Override protected synchronized JSON data() {
            JSONArray r = new JSONArray();
            for (User u : modified) {
                UserInfo i = users.get(u);
                JSONObject entry = new JSONObject().
                        accumulate("id", u.getId()).
                        accumulate("fullName", u.getFullName()).
                        accumulate("url", u.getUrl()).
                        accumulate("avatar", i.avatar != null ? i.avatar : Stapler.getCurrentRequest().getContextPath() + Functions.getResourcePath() + "/images/" + iconSize + "/user.png").
                        accumulate("timeSortKey", i.getTimeSortKey()).
                        accumulate("lastChangeTimeString", i.getLastChangeTimeString());
                AbstractProject<?,?> p = i.getProject();
                if (p != null) {
                    entry.accumulate("projectUrl", p.getUrl()).accumulate("projectFullDisplayName", p.getFullDisplayName());
                }
                r.add(entry);
            }
            modified.clear();
            return r;
        }

        public Api getApi() {
            return new Api(new People());
        }

        /** JENKINS-16397 workaround */
        @Restricted(NoExternalUse.class)
        @ExportedBean
        public final class People {

            private View.People people;

            @Exported public synchronized List<UserInfo> getUsers() {
                if (people == null) {
                    people = parent instanceof Jenkins ? new View.People((Jenkins) parent) : new View.People((View) parent);
                }
                return people.users;
            }
        }

    }

    void addDisplayNamesToSearchIndex(SearchIndexBuilder sib, Collection<TopLevelItem> items) {
        for(TopLevelItem item : items) {
            
            if(LOGGER.isLoggable(Level.FINE)) {
                LOGGER.fine((String.format("Adding url=%s,displayName=%s",
                            item.getSearchUrl(), item.getDisplayName())));
            }
            sib.add(item.getSearchUrl(), item.getDisplayName());
        }        
    }
    
    @Override
    public SearchIndexBuilder makeSearchIndex() {
        SearchIndexBuilder sib = super.makeSearchIndex();
        sib.add(new CollectionSearchIndex<TopLevelItem>() {// for jobs in the view
                protected TopLevelItem get(String key) { return getItem(key); }
                protected Collection<TopLevelItem> all() { return getItems(); }                
                @Override
                protected String getName(TopLevelItem o) {
                    // return the name instead of the display for suggestion searching
                    return o.getName();
                }
            });
        
        // add the display name for each item in the search index
        addDisplayNamesToSearchIndex(sib, getItems());

        return sib;
    }

    /**
     * Accepts the new description.
     */
    @RequirePOST
    public synchronized void doSubmitDescription( StaplerRequest req, StaplerResponse rsp ) throws IOException, ServletException {
        checkPermission(CONFIGURE);

        description = req.getParameter("description");
        save();
        rsp.sendRedirect(".");  // go to the top page
    }

    /**
     * Accepts submission from the configuration page.
     *
     * Subtypes should override the {@link #submit(StaplerRequest)} method.
     */
    @RequirePOST
    public final synchronized void doConfigSubmit( StaplerRequest req, StaplerResponse rsp ) throws IOException, ServletException, FormException {
        checkPermission(CONFIGURE);

        submit(req);

        description = Util.nullify(req.getParameter("description"));
        filterExecutors = req.getParameter("filterExecutors") != null;
        filterQueue = req.getParameter("filterQueue") != null;

        rename(req.getParameter("name"));

        getProperties().rebuild(req, req.getSubmittedForm(), getApplicablePropertyDescriptors());
        updateTransientActions();  

        save();

        FormApply.success("../" + Util.rawEncode(name)).generateResponse(req,rsp,this);
    }

    /**
     * Handles the configuration submission.
     *
     * Load view-specific properties here.
     */
    protected abstract void submit(StaplerRequest req) throws IOException, ServletException, FormException;

    /**
     * Deletes this view.
     */
    @RequirePOST
    public synchronized void doDoDelete(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
        checkPermission(DELETE);

        owner.deleteView(this);

        rsp.sendRedirect2(req.getContextPath()+"/" + owner.getUrl());
    }


    /**
     * Creates a new {@link Item} in this collection.
     *
     * <p>
     * This method should call {@link ModifiableItemGroup#doCreateItem(StaplerRequest, StaplerResponse)}
     * and then add the newly created item to this view.
     * 
     * @return
     *      null if fails.
     */
    public abstract Item doCreateItem( StaplerRequest req, StaplerResponse rsp ) throws IOException, ServletException;

    public void doRssAll( StaplerRequest req, StaplerResponse rsp ) throws IOException, ServletException {
        rss(req, rsp, " all builds", getBuilds());
    }

    public void doRssFailed( StaplerRequest req, StaplerResponse rsp ) throws IOException, ServletException {
        rss(req, rsp, " failed builds", getBuilds().failureOnly());
    }
    
    public RunList getBuilds() {
        return new RunList(this);
    }
    
    public BuildTimelineWidget getTimeline() {
        return new BuildTimelineWidget(getBuilds());
    }

    private void rss(StaplerRequest req, StaplerResponse rsp, String suffix, RunList runs) throws IOException, ServletException {
        RSS.forwardToRss(getDisplayName()+ suffix, getUrl(),
            runs.newBuilds(), Run.FEED_ADAPTER, req, rsp );
    }

    public void doRssLatest( StaplerRequest req, StaplerResponse rsp ) throws IOException, ServletException {
        List<Run> lastBuilds = new ArrayList<Run>();
        for (TopLevelItem item : getItems()) {
            if (item instanceof Job) {
                Job job = (Job) item;
                Run lb = job.getLastBuild();
                if(lb!=null)    lastBuilds.add(lb);
            }
        }
        RSS.forwardToRss(getDisplayName()+" last builds only", getUrl(),
            lastBuilds, Run.FEED_ADAPTER_LATEST, req, rsp );
    }

    /**
     * Accepts <tt>config.xml</tt> submission, as well as serve it.
     */
    @WebMethod(name = "config.xml")
    public HttpResponse doConfigDotXml(StaplerRequest req) throws IOException {
        if (req.getMethod().equals("GET")) {
            // read
            checkPermission(READ);
            return new HttpResponse() {
                public void generateResponse(StaplerRequest req, StaplerResponse rsp, Object node) throws IOException, ServletException {
                    rsp.setContentType("application/xml");
                    View.this.writeXml(rsp.getOutputStream());
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
     *               The source should be either a <code>StreamSource</code> or <code>SAXSource</code>, other sources
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
        } catch (TransformerException e) {
            throw new IOException("Failed to persist configuration.xml", e);
        } catch (SAXException e) {
            throw new IOException("Failed to persist configuration.xml", e);
        }

        // try to reflect the changes by reloading
        InputStream in = new BufferedInputStream(new ByteArrayInputStream(out.toString().getBytes("UTF-8")));
        try {
            // Do not allow overwriting view name as it might collide with another
            // view in same ViewGroup and might not satisfy Jenkins.checkGoodName.
            String oldname = name;
            Jenkins.XSTREAM.unmarshal(new XppDriver().createReader(in), this);
            name = oldname;
        } catch (StreamException e) {
            throw new IOException("Unable to read",e);
        } catch(ConversionException e) {
            throw new IOException("Unable to read",e);
        } catch(Error e) {// mostly reflection errors
            throw new IOException("Unable to read",e);
        } finally {
            in.close();
        }
        save();
    }

    public ContextMenu doChildrenContextMenu(StaplerRequest request, StaplerResponse response) throws Exception {
        ContextMenu m = new ContextMenu();
        for (TopLevelItem i : getItems())
            m.add(i.getShortUrl(),i.getDisplayName());
        return m;
    }

    /**
     * A list of available view types.
     * @deprecated as of 1.286
     *      Use {@link #all()} for read access, and use {@link Extension} for registration.
     */
    @Deprecated
    public static final DescriptorList<View> LIST = new DescriptorList<View>(View.class);

    /**
     * Returns all the registered {@link ViewDescriptor}s.
     */
    public static DescriptorExtensionList<View,ViewDescriptor> all() {
        return Jenkins.getInstance().<View,ViewDescriptor>getDescriptorList(View.class);
    }

    public static List<ViewDescriptor> allInstantiable() {
        List<ViewDescriptor> r = new ArrayList<ViewDescriptor>();
        for (ViewDescriptor d : all())
            if(d.isInstantiable())
                r.add(d);
        return r;
    }

    public static final Comparator<View> SORTER = new Comparator<View>() {
        public int compare(View lhs, View rhs) {
            return lhs.getViewName().compareTo(rhs.getViewName());
        }
    };

    public static final PermissionGroup PERMISSIONS = new PermissionGroup(View.class,Messages._View_Permissions_Title());
    /**
     * Permission to create new views.
     */
    public static final Permission CREATE = new Permission(PERMISSIONS,"Create", Messages._View_CreatePermission_Description(), Permission.CREATE, PermissionScope.ITEM_GROUP);
    public static final Permission DELETE = new Permission(PERMISSIONS,"Delete", Messages._View_DeletePermission_Description(), Permission.DELETE, PermissionScope.ITEM_GROUP);
    public static final Permission CONFIGURE = new Permission(PERMISSIONS,"Configure", Messages._View_ConfigurePermission_Description(), Permission.CONFIGURE, PermissionScope.ITEM_GROUP);
    public static final Permission READ = new Permission(PERMISSIONS,"Read", Messages._View_ReadPermission_Description(), Permission.READ, PermissionScope.ITEM_GROUP);

    // to simplify access from Jelly
    public static Permission getItemCreatePermission() {
        return Item.CREATE;
    }
    
    public static View create(StaplerRequest req, StaplerResponse rsp, ViewGroup owner)
            throws FormException, IOException, ServletException {
        String requestContentType = req.getContentType();
        if(requestContentType==null)
            throw new Failure("No Content-Type header set");

        boolean isXmlSubmission = requestContentType.startsWith("application/xml") || requestContentType.startsWith("text/xml");

        String name = req.getParameter("name");
        checkGoodName(name);
        if(owner.getView(name)!=null)
            throw new Failure(Messages.Hudson_ViewAlreadyExists(name));

        String mode = req.getParameter("mode");
        if (mode==null || mode.length()==0) {
            if(isXmlSubmission) {
                View v = createViewFromXML(name, req.getInputStream());
                v.owner = owner;
                rsp.setStatus(HttpServletResponse.SC_OK);
                return v;
            } else
                throw new Failure(Messages.View_MissingMode());
        }

        View v;
        if (mode!=null && mode.equals("copy")) {
            v = copy(req, owner, name);
        } else {
            ViewDescriptor descriptor = all().findByName(mode);
            if (descriptor == null) {
                throw new Failure("No view type ‘" + mode + "’ is known");
            }

            // create a view
            v = descriptor.newInstance(req,req.getSubmittedForm());
        }
        v.owner = owner;

        // redirect to the config screen
        rsp.sendRedirect2(req.getContextPath()+'/'+v.getUrl()+v.getPostConstructLandingPage());

        return v;
    }

    private static View copy(StaplerRequest req, ViewGroup owner, String name) throws IOException {
        View v;
        String from = req.getParameter("from");
        View src = src = owner.getView(from);

        if(src==null) {
            if(Util.fixEmpty(from)==null)
                throw new Failure("Specify which view to copy");
            else
                throw new Failure("No such view: "+from);
        }
        String xml = Jenkins.XSTREAM.toXML(src);
        v = createViewFromXML(name, new StringInputStream(xml));
        return v;
    }

    /**
     * Instantiate View subtype from XML stream.
     *
     * @param name Alternative name to use or <tt>null</tt> to keep the one in xml.
     */
    public static View createViewFromXML(String name, InputStream xml) throws IOException {
        InputStream in = new BufferedInputStream(xml);
        try {
            View v = (View) Jenkins.XSTREAM.fromXML(in);
            if (name != null) v.name = name;
            checkGoodName(v.name);
            return v;
        } catch(StreamException e) {
            throw new IOException("Unable to read",e);
        } catch(ConversionException e) {
            throw new IOException("Unable to read",e);
        } catch(Error e) {// mostly reflection errors
            throw new IOException("Unable to read",e);
        } finally {
            in.close();
        }
    }

    public static class PropertyList extends DescribableList<ViewProperty,ViewPropertyDescriptor> {
        private PropertyList(View owner) {
            super(owner);
        }

        public PropertyList() {// needed for XStream deserialization
        }

        public View getOwner() {
            return (View)owner;
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
    public static final Message<View> NEW_PRONOUN = new Message<View>();

    private final static Logger LOGGER = Logger.getLogger(View.class.getName());
}
