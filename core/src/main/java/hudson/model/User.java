/*
 * The MIT License
 * 
 * Copyright (c) 2004-2012, Sun Microsystems, Inc., Kohsuke Kawaguchi, Erik Ramfelt,
 * Tom Huybrechts, Vincent Latombe
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
import hudson.*;
import hudson.model.Descriptor.FormException;
import hudson.model.listeners.SaveableListener;
import hudson.security.ACL;
import hudson.security.AccessControlled;
import hudson.security.Permission;
import hudson.security.SecurityRealm;
import hudson.util.FormApply;
import hudson.util.RunList;
import hudson.util.XStream2;
import jenkins.model.Jenkins;
import jenkins.model.ModelObjectWithContextMenu;
import net.sf.json.JSONObject;

import org.acegisecurity.Authentication;
import org.acegisecurity.GrantedAuthority;
import org.acegisecurity.providers.UsernamePasswordAuthenticationToken;
import org.acegisecurity.providers.anonymous.AnonymousAuthenticationToken;
import org.acegisecurity.userdetails.UserDetails;
import org.acegisecurity.userdetails.UsernameNotFoundException;
import org.springframework.dao.DataAccessException;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;
import org.apache.commons.io.filefilter.DirectoryFileFilter;
import org.kohsuke.stapler.interceptor.RequirePOST;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.io.FileFilter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;

/**
 * Represents a user.
 *
 * <p>
 * In Hudson, {@link User} objects are created in on-demand basis;
 * for example, when a build is performed, its change log is computed
 * and as a result commits from users who Hudson has never seen may be discovered.
 * When this happens, new {@link User} object is created.
 *
 * <p>
 * If the persisted record for an user exists, the information is loaded at
 * that point, but if there's no such record, a fresh instance is created from
 * thin air (this is where {@link UserPropertyDescriptor#newInstance(User)} is
 * called to provide initial {@link UserProperty} objects.
 *
 * <p>
 * Such newly created {@link User} objects will be simply GC-ed without
 * ever leaving the persisted record, unless {@link User#save()} method
 * is explicitly invoked (perhaps as a result of a browser submitting a
 * configuration.)
 *
 *
 * @author Kohsuke Kawaguchi
 */
@ExportedBean
public class User extends AbstractModelObject implements AccessControlled, DescriptorByNameOwner, Saveable, Comparable<User>, ModelObjectWithContextMenu {
    
    private transient final String id;

    private volatile String fullName;

    private volatile String description;

    /**
     * List of {@link UserProperty}s configured for this project.
     */
    @CopyOnWrite
    private volatile List<UserProperty> properties = new ArrayList<UserProperty>();


    private User(String id, String fullName) {
        this.id = id;
        this.fullName = fullName;
        load();
    }

    public int compareTo(User that) {
        return this.id.compareTo(that.id);
    }

    /**
     * Loads the other data from disk if it's available.
     */
    private synchronized void load() {
        properties.clear();

        XmlFile config = getConfigFile();
        try {
            if(config.exists())
                config.unmarshal(this);
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to load "+config,e);
        }

        // remove nulls that have failed to load
        for (Iterator<UserProperty> itr = properties.iterator(); itr.hasNext();) {
            if(itr.next()==null)
                itr.remove();            
        }

        // allocate default instances if needed.
        // doing so after load makes sure that newly added user properties do get reflected
        for (UserPropertyDescriptor d : UserProperty.all()) {
            if(getProperty(d.clazz)==null) {
                UserProperty up = d.newInstance(this);
                if(up!=null)
                    properties.add(up);
            }
        }

        for (UserProperty p : properties)
            p.setUser(this);
    }

    @Exported
    public String getId() {
        return id;
    }

    public String getUrl() {
        return "user/"+Util.rawEncode(id);
    }

    public String getSearchUrl() {
        return "/user/"+Util.rawEncode(id);
    }

    /**
     * The URL of the user page.
     */
    @Exported(visibility=999)
    public String getAbsoluteUrl() {
        return Jenkins.getInstance().getRootUrl()+getUrl();
    }

    /**
     * Gets the human readable name of this user.
     * This is configurable by the user.
     *
     * @return
     *      never null.
     */
    @Exported(visibility=999)
    public String getFullName() {
        return fullName;
    }

    /**
     * Sets the human readable name of thie user.
     */
    public void setFullName(String name) {
        if(Util.fixEmptyAndTrim(name)==null)    name=id;
        this.fullName = name;
    }

    @Exported
    public String getDescription() {
        return description;
    }

    /**
     * Gets the user properties configured for this user.
     */
    public Map<Descriptor<UserProperty>,UserProperty> getProperties() {
        return Descriptor.toMap(properties);
    }

    /**
     * Updates the user object by adding a property.
     */
    public synchronized void addProperty(UserProperty p) throws IOException {
        UserProperty old = getProperty(p.getClass());
        List<UserProperty> ps = new ArrayList<UserProperty>(properties);
        if(old!=null)
            ps.remove(old);
        ps.add(p);
        p.setUser(this);
        properties = ps;
        save();
    }

    /**
     * List of all {@link UserProperty}s exposed primarily for the remoting API.
     */
    @Exported(name="property",inline=true)
    public List<UserProperty> getAllProperties() {
        return Collections.unmodifiableList(properties);
    }
    
    /**
     * Gets the specific property, or null.
     */
    public <T extends UserProperty> T getProperty(Class<T> clazz) {
        for (UserProperty p : properties) {
            if(clazz.isInstance(p))
                return clazz.cast(p);
        }
        return null;
    }

    /**
     * Creates an {@link Authentication} object that represents this user.
     * 
     * @since 1.419
     */
    public Authentication impersonate() {
        try {
            UserDetails u = Jenkins.getInstance().getSecurityRealm().loadUserByUsername(id);
            return new UsernamePasswordAuthenticationToken(u.getUsername(), "", u.getAuthorities());
        } catch (UsernameNotFoundException e) {
            // ignore
        } catch (DataAccessException e) {
            // ignore
        }
        // TODO: use the stored GrantedAuthorities
        return new UsernamePasswordAuthenticationToken(id, "",
            new GrantedAuthority[]{SecurityRealm.AUTHENTICATED_AUTHORITY});
    }

    /**
     * Accepts the new description.
     */
    public synchronized void doSubmitDescription( StaplerRequest req, StaplerResponse rsp ) throws IOException, ServletException {
        checkPermission(Jenkins.ADMINISTER);

        description = req.getParameter("description");
        save();
        
        rsp.sendRedirect(".");  // go to the top page
    }

    /**
     * Gets the fallback "unknown" user instance.
     * <p>
     * This is used to avoid null {@link User} instance.
     */
    public static @Nonnull User getUnknown() {
        return get("unknown");
    }

    /**
     * Gets the {@link User} object by its id or full name.
     *
     * @param create
     *      If true, this method will never return null for valid input
     *      (by creating a new {@link User} object if none exists.)
     *      If false, this method will return null if {@link User} object
     *      with the given name doesn't exist.
     * @deprecated use {@link User#get(String, boolean, java.util.Map)}
     */
    public static User get(String idOrFullName, boolean create) {
        return get(idOrFullName, create, Collections.emptyMap());
    }

    /**
     * Gets the {@link User} object by its id or full name.
     *
     * @param create
     *      If true, this method will never return null for valid input
     *      (by creating a new {@link User} object if none exists.)
     *      If false, this method will return null if {@link User} object
     *      with the given name doesn't exist.
     *
     * @param context
     *      contextual environment this user idOfFullName was retrieved from,
     *      that can help resolve the user ID
     */
    public static User get(String idOrFullName, boolean create, Map context) {

        if(idOrFullName==null)
            return null;

        // sort resolvers by priority
        List<CanonicalIdResolver> resolvers = new ArrayList<CanonicalIdResolver>(Jenkins.getInstance().getExtensionList(CanonicalIdResolver.class));
        Collections.sort(resolvers);

        String id = null;
        for (CanonicalIdResolver resolver : resolvers) {
            id = resolver.resolveCanonicalId(idOrFullName, context);
            if (id != null) {
                LOGGER.log(Level.FINE, "{0} mapped {1} to {2}", new Object[] {resolver, idOrFullName, id});
                break;
            }
        }
        // DefaultUserCanonicalIdResolver will always return a non-null id if all other CanonicalIdResolver failed

        return getOrCreate(id, idOrFullName, create);
    }

    /**
     * retrieve a user by its ID, and create a new one if requested
     */
    private static User getOrCreate(String id, String fullName, boolean create) {
        String idkey = id.toLowerCase(Locale.ENGLISH);

        User u = byName.get(idkey);
        if (u==null && (create || getConfigFileFor(id).exists())) {
            User tmp = new User(id, fullName);
            User prev = byName.putIfAbsent(idkey, u = tmp);
            if (prev != null) {
                u = prev; // if some has already put a value in the map, use it
                if (LOGGER.isLoggable(Level.FINE) && !fullName.equals(prev.getFullName())) {
                    LOGGER.log(Level.FINE, "mismatch on fullName (‘" + fullName + "’ vs. ‘" + prev.getFullName() + "’) for ‘" + id + "’", new Throwable());
                }
            }
        }
        return u;
    }

    /**
     * Gets the {@link User} object by its id or full name.
     */
    public static @Nonnull User get(String idOrFullName) {
        return get(idOrFullName,true);
    }

    /**
     * Gets the {@link User} object representing the currently logged-in user, or null
     * if the current user is anonymous.
     * @since 1.172
     */
    public static @CheckForNull User current() {
        Authentication a = Jenkins.getAuthentication();
        if(a instanceof AnonymousAuthenticationToken)
            return null;
        return get(a.getName());
    }

    private static volatile long lastScanned;

    /**
     * Gets all the users.
     */
    public static Collection<User> getAll() {
        if(System.currentTimeMillis() -lastScanned>10000) {
            // occasionally scan the file system to check new users
            // whether we should do this only once at start up or not is debatable.
            // set this right away to avoid another thread from doing the same thing while we do this.
            // having two threads doing the work won't cause race condition, but it's waste of time.
            lastScanned = System.currentTimeMillis();

            File[] subdirs = getRootDir().listFiles((FileFilter)DirectoryFileFilter.INSTANCE);
            if(subdirs==null)       return Collections.emptyList(); // shall never happen

            for (File subdir : subdirs)
                if(new File(subdir,"config.xml").exists()) {
                    String name = subdir.getName();
                    User.getOrCreate(name, name, true);
                }

            lastScanned = System.currentTimeMillis();
        }

        ArrayList<User> r = new ArrayList<User>(byName.values());
        Collections.sort(r,new Comparator<User>() {
            public int compare(User o1, User o2) {
                return o1.getId().compareToIgnoreCase(o2.getId());
            }
        });
        return r;
    }

    /**
     * Reloads the configuration from disk.
     */
    public static void reload() {
        for( User u : byName.values() )
            u.load();
    }

    /**
     * Stop gap hack. Don't use it. To be removed in the trunk.
     */
    public static void clear() {
        byName.clear();
    }

    /**
     * Returns the user name.
     */
    public String getDisplayName() {
        return getFullName();
    }

    /**
     * Gets the list of {@link Build}s that include changes by this user,
     * by the timestamp order.
     * 
     * TODO: do we need some index for this?
     */
    @WithBridgeMethods(List.class)
    public RunList getBuilds() {
        List<AbstractBuild> r = new ArrayList<AbstractBuild>();
        for (AbstractProject<?,?> p : Jenkins.getInstance().getAllItems(AbstractProject.class))
            for (AbstractBuild<?,?> b : p.getBuilds().newBuilds()){
                if(b.hasParticipant(this))
                    r.add(b);
                else {
                    //append builds that were run by this user
                    Cause.UserIdCause cause = b.getCause(Cause.UserIdCause.class);
                    if (cause != null) {
                        String userId = cause.getUserId();
                        if (userId != null && this.getId() != null && userId.equals(this.getId()))
                            r.add(b);
                    }
                }
            }
        return RunList.fromRuns(r);
    }

    /**
     * Gets all the {@link AbstractProject}s that this user has committed to.
     * @since 1.191
     */
    public Set<AbstractProject<?,?>> getProjects() {
        Set<AbstractProject<?,?>> r = new HashSet<AbstractProject<?,?>>();
        for (AbstractProject<?,?> p : Jenkins.getInstance().getAllItems(AbstractProject.class))
            if(p.hasParticipant(this))
                r.add(p);
        return r;
    }

    public @Override String toString() {
        return fullName;
    }

    /**
     * The file we save our configuration.
     */
    protected final XmlFile getConfigFile() {
        return new XmlFile(XSTREAM,getConfigFileFor(id));
    }

    private static final File getConfigFileFor(String id) {
        return new File(getRootDir(),id +"/config.xml");
    }

    /**
     * Gets the directory where Hudson stores user information.
     */
    private static File getRootDir() {
        return new File(Jenkins.getInstance().getRootDir(), "users");
    }

    /**
     * Save the settings to a file.
     */
    public synchronized void save() throws IOException {
        if(BulkChange.contains(this))   return;
        getConfigFile().write(this);
        SaveableListener.fireOnChange(this, getConfigFile());
    }

    /**
     * Deletes the data directory and removes this user from Hudson.
     *
     * @throws IOException
     *      if we fail to delete.
     */
    public synchronized void delete() throws IOException {
        byName.remove(id.toLowerCase(Locale.ENGLISH));
        Util.deleteRecursive(new File(getRootDir(), id));
    }

    /**
     * Exposed remote API.
     */
    public Api getApi() {
        return new Api(this);
    }

    /**
     * Accepts submission from the configuration page.
     */
    @RequirePOST
    public void doConfigSubmit( StaplerRequest req, StaplerResponse rsp ) throws IOException, ServletException, FormException {
        checkPermission(Jenkins.ADMINISTER);

        fullName = req.getParameter("fullName");
        description = req.getParameter("description");

        JSONObject json = req.getSubmittedForm();

        List<UserProperty> props = new ArrayList<UserProperty>();
        int i = 0;
        for (UserPropertyDescriptor d : UserProperty.all()) {
            UserProperty p = getProperty(d.clazz);

            JSONObject o = json.optJSONObject("userProperty" + (i++));
            if (o!=null) {
                if (p != null) {
                    p = p.reconfigure(req, o);
                } else {
                    p = d.newInstance(req, o);
                }
                p.setUser(this);
            }

            if (p!=null)
                props.add(p);
        }
        this.properties = props;

        save();

        FormApply.success(".").generateResponse(req,rsp,this);
    }

    /**
     * Deletes this user from Hudson.
     */
    @RequirePOST
    public void doDoDelete(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
        checkPermission(Jenkins.ADMINISTER);
        if (id.equals(Jenkins.getAuthentication().getName())) {
            rsp.sendError(HttpServletResponse.SC_BAD_REQUEST, "Cannot delete self");
            return;
        }

        delete();

        rsp.sendRedirect2("../..");
    }

    public void doRssAll(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
        rss(req, rsp, " all builds", RunList.fromRuns(getBuilds()), Run.FEED_ADAPTER);
    }

    public void doRssFailed(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
        rss(req, rsp, " regression builds", RunList.fromRuns(getBuilds()).regressionOnly(), Run.FEED_ADAPTER);
    }

    public void doRssLatest(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
        final List<Run> lastBuilds = new ArrayList<Run>();
        for (final TopLevelItem item : Jenkins.getInstance().getItems()) {
            if (!(item instanceof Job)) continue;
            for (Run r = ((Job) item).getLastBuild(); r != null; r = r.getPreviousBuild()) {
                if (!(r instanceof AbstractBuild)) continue;
                final AbstractBuild b = (AbstractBuild) r;
                if (b.hasParticipant(this)) {
                    lastBuilds.add(b);
                    break;
                }
            }
        }
        rss(req, rsp, " latest build", RunList.fromRuns(lastBuilds), Run.FEED_ADAPTER_LATEST);
    }

    private void rss(StaplerRequest req, StaplerResponse rsp, String suffix, RunList runs, FeedAdapter adapter)
            throws IOException, ServletException {
        RSS.forwardToRss(getDisplayName()+ suffix, getUrl(), runs.newBuilds(), adapter, req, rsp);
    }

    /**
     * Keyed by {@link User#id}. This map is used to ensure
     * singleton-per-id semantics of {@link User} objects.
     *
     * The key needs to be lower cased for case insensitivity.
     */
    private static final ConcurrentMap<String,User> byName = new ConcurrentHashMap<String, User>();

    /**
     * Used to load/save user configuration.
     */
    public static final XStream2 XSTREAM = new XStream2();

    private static final Logger LOGGER = Logger.getLogger(User.class.getName());

    static {
        XSTREAM.alias("user",User.class);
    }

    public ACL getACL() {
        final ACL base = Jenkins.getInstance().getAuthorizationStrategy().getACL(this);
        // always allow a non-anonymous user full control of himself.
        return new ACL() {
            public boolean hasPermission(Authentication a, Permission permission) {
                return (a.getName().equals(id) && !(a instanceof AnonymousAuthenticationToken))
                        || base.hasPermission(a, permission);
            }
        };
    }

    public void checkPermission(Permission permission) {
        getACL().checkPermission(permission);
    }

    public boolean hasPermission(Permission permission) {
        return getACL().hasPermission(permission);
    }

    /**
     * With ADMINISTER permission, can delete users with persisted data but can't delete self.
     */
    public boolean canDelete() {
        return hasPermission(Jenkins.ADMINISTER) && !id.equals(Jenkins.getAuthentication().getName())
                && new File(getRootDir(), id).exists();
    }

    /**
     * Checks for authorities (groups) associated with this user.
     * If the caller lacks {@link Jenkins#ADMINISTER}, or any problems arise, returns an empty list.
     * {@link SecurityRealm#AUTHENTICATED_AUTHORITY} and the username, if present, are omitted.
     * @since 1.498
     * @return a possibly empty list
     */
    public @Nonnull List<String> getAuthorities() {
        if (!Jenkins.getInstance().hasPermission(Jenkins.ADMINISTER)) {
            return Collections.emptyList();
        }
        List<String> r = new ArrayList<String>();
        for (GrantedAuthority a : impersonate().getAuthorities()) {
            if (a.equals(SecurityRealm.AUTHENTICATED_AUTHORITY)) {
                continue;
            }
            String n = a.getAuthority();
            if (n != null && !n.equals(id)) {
                r.add(n);
            }
        }
        return r;
    }

    public Descriptor getDescriptorByName(String className) {
        return Jenkins.getInstance().getDescriptorByName(className);
    }
    
    public Object getDynamic(String token) {
        for(Action action: getTransientActions()){
            if(action.getUrlName().equals(token))
                return action;
        }
        for(Action action: getPropertyActions()){
            if(action.getUrlName().equals(token))
                return action;
        }
        return null;
    }
    
    /**
     * Return all properties that are also actions.
     * 
     * @return the list can be empty but never null. read only.
     */
    public List<Action> getPropertyActions() {
        List<Action> actions = new ArrayList<Action>();
        for (UserProperty userProp : getProperties().values()) {
            if (userProp instanceof Action) {
                actions.add((Action) userProp);
            }
        }
        return Collections.unmodifiableList(actions);
    }
    
    /**
     * Return all transient actions associated with this user.
     * 
     * @return the list can be empty but never null. read only.
     */
    public List<Action> getTransientActions() {
        List<Action> actions = new ArrayList<Action>();
        for (TransientUserActionFactory factory: TransientUserActionFactory.all()) {
            actions.addAll(factory.createFor(this));
        }
        return Collections.unmodifiableList(actions);
    }

    public ContextMenu doContextMenu(StaplerRequest request, StaplerResponse response) throws Exception {
        return new ContextMenu().from(this,request,response);
    }

    public static abstract class CanonicalIdResolver extends AbstractDescribableImpl<CanonicalIdResolver> implements ExtensionPoint, Comparable<CanonicalIdResolver> {

        /**
         * context key for realm (domain) where idOrFullName has been retreived from.
         * Can be used (for example) to distinguish ambiguous committer ID using the SCM URL.
         * Associated Value is a {@link String}
         */
        public static final String REALM = "realm";

        public int compareTo(CanonicalIdResolver o) {
            // reverse priority order
            int i = getPriority();
            int j = o.getPriority();
            return i>j ? -1 : (i==j ? 0:1);
        }

        /**
         * extract user ID from idOrFullName with help from contextual infos.
         * can return <code>null</code> if no user ID matched the input
         */
        public abstract @CheckForNull String resolveCanonicalId(String idOrFullName, Map<String, ?> context);

        public int getPriority() {
            return 1;
        }

    }


    /**
     * Resolve user ID from full name
     */
    @Extension
    public static class FullNameIdResolver extends CanonicalIdResolver {

        @Override
        public String resolveCanonicalId(String idOrFullName, Map<String, ?> context) {
            for (User user : getAll()) {
                if (idOrFullName.equals(user.getFullName())) return user.getId();
            }
            return null;
        }

        @Override
        public int getPriority() {
            return -1; // lower than default
        }
    }
}

