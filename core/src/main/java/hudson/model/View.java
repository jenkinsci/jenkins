/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi, Tom Huybrechts
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

import static hudson.model.Hudson.checkGoodName;
import hudson.DescriptorExtensionList;
import hudson.Extension;
import hudson.ExtensionPoint;
import hudson.Util;
import hudson.model.Descriptor.FormException;
import hudson.model.Node.Mode;
import hudson.scm.ChangeLogSet.Entry;
import hudson.search.CollectionSearchIndex;
import hudson.search.SearchIndexBuilder;
import hudson.security.ACL;
import hudson.security.AccessControlled;
import hudson.security.Permission;
import hudson.security.PermissionGroup;
import hudson.util.DescriptorList;
import hudson.util.RunList;
import hudson.widgets.Widget;

import java.io.IOException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import javax.servlet.ServletException;

import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;

/**
 * Encapsulates the rendering of the list of {@link TopLevelItem}s
 * that {@link Hudson} owns.
 *
 * <p>
 * This is an extension point in Hudson, allowing different kind of
 * rendering to be added as plugins.
 *
 * <h2>Note for implementors</h2>
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
public abstract class View extends AbstractModelObject implements AccessControlled, Describable<View>, ExtensionPoint {
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
     * Gets the {@link TopLevelItem} of the given name.
     */
    public TopLevelItem getItem(String name) {
        return Hudson.getInstance().getItem(name);
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
    public void rename(String newName) throws ParseException, FormException {
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
     * Message displayed in the top page. Can be null. Includes HTML.
     */
    @Exported
    public String getDescription() {
        return description;
    }

    public ViewDescriptor getDescriptor() {
        return (ViewDescriptor)Hudson.getInstance().getDescriptorOrDie(getClass());
    }

    public String getDisplayName() {
        return getViewName();
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
        return Collections.unmodifiableList(Hudson.getInstance().getWidgets());
    }

    /**
     * If true, this is a view that renders the top page of Hudson.
     */
    public boolean isDefault() {
        return Hudson.getInstance().getPrimaryView()==this;
    }
    
    public List<Computer> getComputers() {
    	Computer[] computers = Hudson.getInstance().getComputers();
    	
    	if (!isFilterExecutors()) {
    		return Arrays.asList(computers);
    	}
    	
    	List<Computer> result = new ArrayList<Computer>();
    	
    	boolean roam = false;
    	HashSet<Label> labels = new HashSet<Label>();
    	for (Item item: getItems()) {
    		if (item instanceof AbstractProject<?,?>) {
    			AbstractProject<?,?> p = (AbstractProject<?, ?>) item;
    			Label l = p.getAssignedLabel();
    			if (l != null) {
    				labels.add(l);
    			} else {
    				roam = true;
    			}
    		}
    	}
    	
    	for (Computer c: computers) {
    		Node n = c.getNode();
    		if (c != null) {
    			if (roam && n.getMode() == Mode.NORMAL || !Collections.disjoint(n.getAssignedLabels(), labels)) {
    				result.add(c);
    			}
    		}
    	}
    	
    	return result;
    }
    
    public List<Queue.Item> getQueueItems() {
    	if (!isFilterQueue()) {
    		return Arrays.asList(Hudson.getInstance().getQueue().getItems());
    	}
    	
    	Collection<TopLevelItem> items = getItems(); 
    	List<Queue.Item> result = new ArrayList<Queue.Item>();
    	for (Queue.Item qi: Hudson.getInstance().getQueue().getItems()) {
    		if (items.contains(qi.task)) {
    			result.add(qi);
    		}
    	}
    	return result;
    }

    /**
     * Returns the path relative to the context root.
     *
     * Doesn't start with '/' but ends with '/' (except returns
     * empty string when this is the default view).
     */
    public String getUrl() {
        return isDefault() ? "" : getViewUrl();
    }

    /**
     * Same as {@link #getUrl()} except this returns a view/{name} path
     * even for the default view.
     */
    public String getViewUrl() {
        return (owner!=null ? owner.getUrl() : "") + "view/" + Util.rawEncode(getViewName()) + '/';
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
     * @see Hudson#getActions()
     */
    public List<Action> getActions() {
        return Hudson.getInstance().getActions();
    }

    /**
     * Gets the absolute URL of this view.
     */
    @Exported(visibility=2,name="url")
    public String getAbsoluteUrl() {
        return Hudson.getInstance().getRootUrl()+getUrl();
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
        return Hudson.getInstance().getAuthorizationStrategy().getACL(this);
    }

    public void checkPermission(Permission p) {
        getACL().checkPermission(p);
    }

    public boolean hasPermission(Permission p) {
        return getACL().hasPermission(p);
    }

    /**
     * Called when a job name is changed or deleted.
     *
     * <p>
     * If this view contains this job, it should update the view membership so that
     * the renamed job will remain in the view, and the deleted job is removed.
     *
     * @param item
     *      The item whose name is being changed.
     * @param oldName
     *      Old name of the item. Always non-null.
     * @param newName
     *      New name of the item, if the item is renamed. Or null, if the item is removed.
     */
    public abstract void onJobRenamed(Item item, String oldName, String newName);

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
     */
    public final boolean hasPeople() {
        return People.isApplicable(getItems());
    }

    /**
     * Gets the users that show up in the changelog of this job collection.
     */
    public final People getPeople() {
        return new People(this);
    }

    @ExportedBean
    public static final class People  {
        @Exported
        public final List<UserInfo> users;

        public final Object parent;

        public People(Hudson parent) {
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


    @Override
    public SearchIndexBuilder makeSearchIndex() {
        return super.makeSearchIndex()
            .add(new CollectionSearchIndex() {// for jobs in the view
                protected TopLevelItem get(String key) { return getItem(key); }
                protected Collection<TopLevelItem> all() { return getItems(); }
            });
    }

    /**
     * Accepts the new description.
     */
    public synchronized void doSubmitDescription( StaplerRequest req, StaplerResponse rsp ) throws IOException, ServletException {
        checkPermission(CONFIGURE);

        req.setCharacterEncoding("UTF-8");
        description = req.getParameter("description");
        owner.save();
        rsp.sendRedirect(".");  // go to the top page
    }

    /**
     * Accepts submission from the configuration page.
     *
     * Subtypes should override the {@link #submit(StaplerRequest)} method.
     */
    public final synchronized void doConfigSubmit( StaplerRequest req, StaplerResponse rsp ) throws IOException, ServletException, FormException {
        checkPermission(CONFIGURE);

        req.setCharacterEncoding("UTF-8");

        submit(req);

        description = Util.nullify(req.getParameter("description"));
        filterExecutors = req.getParameter("filterExecutors") != null;
        filterQueue = req.getParameter("filterQueue") != null;

        try {
            rename(req.getParameter("name"));
        } catch (ParseException e) {
            sendError(e, req, rsp);
            return;
        }

        owner.save();

        rsp.sendRedirect2("../"+name);
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
    public synchronized void doDoDelete(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
        requirePOST();
        checkPermission(DELETE);

        owner.deleteView(this);

        rsp.sendRedirect2(req.getContextPath()+"/" + owner.getUrl());
    }


    /**
     * Creates a new {@link Item} in this collection.
     *
     * <p>
     * This method should call {@link Hudson#doCreateItem(StaplerRequest, StaplerResponse)}
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
     * A list of available view types.
     * @deprecated as of 1.286
     *      Use {@link #all()} for read access, and use {@link Extension} for registration.
     */
    public static final DescriptorList<View> LIST = new DescriptorList<View>(View.class);

    /**
     * Returns all the registered {@link ViewDescriptor}s.
     */
    public static DescriptorExtensionList<View,ViewDescriptor> all() {
        return Hudson.getInstance().<View,ViewDescriptor>getDescriptorList(View.class);
    }

    public static final Comparator<View> SORTER = new Comparator<View>() {
        public int compare(View lhs, View rhs) {
            return lhs.getViewName().compareTo(rhs.getViewName());
        }
    };

    public static final PermissionGroup PERMISSIONS = new PermissionGroup(View.class,Messages._View_Permissions_Title());
    /**
     * Permission to create new jobs.
     */
    public static final Permission CREATE = new Permission(PERMISSIONS,"Create", Messages._View_CreatePermission_Description(), Permission.CREATE);
    public static final Permission DELETE = new Permission(PERMISSIONS,"Delete", Messages._View_DeletePermission_Description(), Permission.DELETE);
    public static final Permission CONFIGURE = new Permission(PERMISSIONS,"Configure", Messages._View_ConfigurePermission_Description(), Permission.CONFIGURE);

    // to simplify access from Jelly
    public static Permission getItemCreatePermission() {
        return Item.CREATE;
    }
    
    public static View create(StaplerRequest req, StaplerResponse rsp, ViewGroup owner)
            throws ParseException, FormException, IOException, ServletException {
        req.setCharacterEncoding("UTF-8");

        String name = req.getParameter("name");
        checkGoodName(name);
        if(owner.getView(name)!=null)
            throw new FormException(Messages.Hudson_ViewAlreadyExists(name),"name");

        String mode = req.getParameter("mode");
        if (mode==null || mode.length()==0)
            throw new FormException(Messages.View_MissingMode(),"mode");

        // create a view
        View v = all().findByName(mode).newInstance(req,req.getSubmittedForm());
        v.owner = owner;

        // redirect to the config screen
        rsp.sendRedirect2(req.getContextPath()+'/'+v.getUrl()+v.getPostConstructLandingPage());

        return v;
    }
}
