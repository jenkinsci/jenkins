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

import com.google.common.base.Predicate;
import com.infradna.tool.bridge_method_injector.WithBridgeMethods;
import hudson.*;
import hudson.model.Descriptor.FormException;
import hudson.model.listeners.SaveableListener;
import hudson.security.ACL;
import hudson.security.AccessControlled;
import hudson.security.Permission;
import hudson.security.SecurityRealm;
import hudson.security.UserMayOrMayNotExistException;
import hudson.util.FormApply;
import hudson.util.FormValidation;
import hudson.util.RunList;
import hudson.util.XStream2;
import jenkins.model.IdStrategy;
import jenkins.model.Jenkins;
import jenkins.model.ModelObjectWithContextMenu;
import jenkins.security.ImpersonatingUserDetailsService;
import jenkins.security.LastGrantedAuthoritiesProperty;
import net.sf.json.JSONObject;

import org.acegisecurity.Authentication;
import org.acegisecurity.GrantedAuthority;
import org.acegisecurity.providers.UsernamePasswordAuthenticationToken;
import org.acegisecurity.providers.anonymous.AnonymousAuthenticationToken;
import org.acegisecurity.userdetails.UserDetails;
import org.acegisecurity.userdetails.UsernameNotFoundException;
import org.springframework.dao.DataAccessException;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;
import org.apache.commons.io.filefilter.DirectoryFileFilter;
import org.kohsuke.stapler.interceptor.RequirePOST;

import javax.annotation.concurrent.GuardedBy;
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
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

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

    /**
     * The username of the 'unknown' user used to avoid null user references.
     */
    private static final String UKNOWN_USERNAME = "unknown";

    /**
     * These usernames should not be used by real users logging into Jenkins. Therefore, we prevent
     * users with these names from being saved.
     */
    private static final String[] ILLEGAL_PERSISTED_USERNAMES = new String[]{ACL.ANONYMOUS_USERNAME,
            ACL.SYSTEM_USERNAME, UKNOWN_USERNAME};
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

    /**
     * Returns the {@link jenkins.model.IdStrategy} for use with {@link User} instances. See
     * {@link hudson.security.SecurityRealm#getUserIdStrategy()}
     *
     * @return the {@link jenkins.model.IdStrategy} for use with {@link User} instances.
     * @since 1.566
     */
    @Nonnull
    public static IdStrategy idStrategy() {
        Jenkins j = Jenkins.getInstance();
        if (j == null) {
            return IdStrategy.CASE_INSENSITIVE;
        }
        SecurityRealm realm = j.getSecurityRealm();
        if (realm == null) {
            return IdStrategy.CASE_INSENSITIVE;
        }
        return realm.getUserIdStrategy();
    }

    public int compareTo(User that) {
        return idStrategy().compare(this.id, that.id);
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

    public @Nonnull String getUrl() {
        return "user/"+Util.rawEncode(idStrategy().keyFor(id));
    }

    public @Nonnull String getSearchUrl() {
        return "/user/"+Util.rawEncode(idStrategy().keyFor(id));
    }

    /**
     * The URL of the user page.
     */
    @Exported(visibility=999)
    public @Nonnull String getAbsoluteUrl() {
        return Jenkins.getInstance().getRootUrl()+getUrl();
    }

    /**
     * Gets the human readable name of this user.
     * This is configurable by the user.
     */
    @Exported(visibility=999)
    public @Nonnull String getFullName() {
        return fullName;
    }

    /**
     * Sets the human readable name of the user.
     * If the input parameter is empty, the user's ID will be set.
     */
    public void setFullName(String name) {
        if(Util.fixEmptyAndTrim(name)==null)    name=id;
        this.fullName = name;
    }

    @Exported
    public @CheckForNull String getDescription() {
        return description;
    }


    /**
     * Sets the description of the user.
     * @since 1.609
     */
    public void setDescription(String description) {
        this.description = description;
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
    public synchronized void addProperty(@Nonnull UserProperty p) throws IOException {
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
     * This method checks with {@link SecurityRealm} if the user is a valid user that can login to the security realm.
     * If {@link SecurityRealm} is a kind that does not support querying information about other users, this will
     * use {@link LastGrantedAuthoritiesProperty} to pick up the granted authorities as of the last time the user has
     * logged in.
     *
     * @throws UsernameNotFoundException
     *      If this user is not a valid user in the backend {@link SecurityRealm}.
     * @since 1.419
     */
    public @Nonnull Authentication impersonate() throws UsernameNotFoundException {
        try {
            UserDetails u = new ImpersonatingUserDetailsService(
                    Jenkins.getInstance().getSecurityRealm().getSecurityComponents().userDetails).loadUserByUsername(id);
            return new UsernamePasswordAuthenticationToken(u.getUsername(), "", u.getAuthorities());
        } catch (UserMayOrMayNotExistException e) {
            // backend can't load information about other users. so use the stored information if available
        } catch (UsernameNotFoundException e) {
            // if the user no longer exists in the backend, we need to refuse impersonating this user
            if (!ALLOW_NON_EXISTENT_USER_TO_LOGIN)
                throw e;
        } catch (DataAccessException e) {
            // seems like it's in the same boat as UserMayOrMayNotExistException
        }

        // seems like a legitimate user we have no idea about. proceed with minimum access
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
        return get(UKNOWN_USERNAME);
    }

    /**
     * Gets the {@link User} object by its id or full name.
     *
     * @param create
     *      If true, this method will never return null for valid input
     *      (by creating a new {@link User} object if none exists.)
     *      If false, this method will return null if {@link User} object
     *      with the given name doesn't exist.
     * @return Requested user. May be {@code null} if a user does not exist and
     *      {@code create} is false.
     * @deprecated use {@link User#get(String, boolean, java.util.Map)}
     */
    public static @Nullable User get(String idOrFullName, boolean create) {
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
     * 
     * @return
     *      An existing or created user. May be {@code null} if a user does not exist and
     *      {@code create} is false.
     */
    public static @Nullable User get(String idOrFullName, boolean create, Map context) {

        if(idOrFullName==null)
            return null;

        // sort resolvers by priority
        List<CanonicalIdResolver> resolvers = new ArrayList<CanonicalIdResolver>(ExtensionList.lookup(CanonicalIdResolver.class));
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
        if (id == null) {
            throw new IllegalStateException("The user id should be always non-null thanks to DefaultUserCanonicalIdResolver");
        }
        return getOrCreate(id, idOrFullName, create);
    }

    /**
     * Retrieve a user by its ID, and create a new one if requested.
     * @return
     *      An existing or created user. May be {@code null} if a user does not exist and
     *      {@code create} is false.
     */
    private static @Nullable User getOrCreate(@Nonnull String id, @Nonnull String fullName, boolean create) {
        String idkey = idStrategy().keyFor(id);

        byNameLock.readLock().lock();
        User u;
        try {
            u = byName.get(idkey);
        } finally {
            byNameLock.readLock().unlock();
        }
        final File configFile = getConfigFileFor(id);
        if (!configFile.isFile() && !configFile.getParentFile().isDirectory()) {
            // check for legacy users and migrate if safe to do so.
            File[] legacy = getLegacyConfigFilesFor(id);
            if (legacy != null && legacy.length > 0) {
                for (File legacyUserDir : legacy) {
                    final XmlFile legacyXml = new XmlFile(XSTREAM, new File(legacyUserDir, "config.xml"));
                    try {
                        Object o = legacyXml.read();
                        if (o instanceof User) {
                            if (idStrategy().equals(id, legacyUserDir.getName()) && !idStrategy().filenameOf(legacyUserDir.getName())
                                    .equals(legacyUserDir.getName())) {
                                if (!legacyUserDir.renameTo(configFile.getParentFile())) {
                                    LOGGER.log(Level.WARNING, "Failed to migrate user record from {0} to {1}",
                                            new Object[]{legacyUserDir, configFile.getParentFile()});
                                }
                                break;
                            }
                        } else {
                            LOGGER.log(Level.FINE, "Unexpected object loaded from {0}: {1}",
                                    new Object[]{ legacyUserDir, o });
                        }
                    } catch (IOException e) {
                        LOGGER.log(Level.FINE, String.format("Exception trying to load user from {0}: {1}",
                                new Object[]{ legacyUserDir, e.getMessage() }), e);
                    }
                }
            }
        }
        if (u==null && (create || configFile.exists())) {
            User tmp = new User(id, fullName);
            User prev;
            byNameLock.readLock().lock();
            try {
                prev = byName.putIfAbsent(idkey, u = tmp);
            } finally {
                byNameLock.readLock().unlock();
            }
            if (prev != null) {
                u = prev; // if some has already put a value in the map, use it
                if (LOGGER.isLoggable(Level.FINE) && !fullName.equals(prev.getFullName())) {
                    LOGGER.log(Level.FINE, "mismatch on fullName (‘" + fullName + "’ vs. ‘" + prev.getFullName() + "’) for ‘" + id + "’", new Throwable());
                }
            } else if (!id.equals(fullName) && !configFile.exists()) {
                // JENKINS-16332: since the fullName may not be recoverable from the id, and various code may store the id only, we must save the fullName
                try {
                    u.save();
                } catch (IOException x) {
                    LOGGER.log(Level.WARNING, null, x);
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
        return get(Jenkins.getAuthentication());
    }

    /**
     * Gets the {@link User} object representing the supplied {@link Authentication} or
     * {@code null} if the supplied {@link Authentication} is either anonymous or {@code null}
     * @param a the supplied {@link Authentication} .
     * @return a {@link User} object for the supplied {@link Authentication} or {@code null}
     * @since 1.609
     */
    public static @CheckForNull User get(@CheckForNull Authentication a) {
        if(a == null || a instanceof AnonymousAuthenticationToken)
            return null;

        // Since we already know this is a name, we can just call getOrCreate with the name directly.
        String id = a.getName();
        return getOrCreate(id, id, true);
    }

    private static volatile long lastScanned;

    /**
     * Gets all the users.
     */
    public static @Nonnull Collection<User> getAll() {
        final IdStrategy strategy = idStrategy();
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
                    String name = strategy.idFromFilename(subdir.getName());
                    User.getOrCreate(name, name, true);
                }

            lastScanned = System.currentTimeMillis();
        }

        byNameLock.readLock().lock();
        ArrayList<User> r;
        try {
            r = new ArrayList<User>(byName.values());
        } finally {
            byNameLock.readLock().unlock();
        }
        Collections.sort(r,new Comparator<User>() {

            public int compare(User o1, User o2) {
                return strategy.compare(o1.getId(), o2.getId());
            }
        });
        return r;
    }

    /**
     * Reloads the configuration from disk.
     */
    public static void reload() {
        byNameLock.readLock().lock();
        try {
            for (User u : byName.values()) {
                u.load();
            }
        } finally {
            byNameLock.readLock().unlock();
        }
    }

    /**
     * Stop gap hack. Don't use it. To be removed in the trunk.
     */
    public static void clear() {
        byNameLock.writeLock().lock();
        try {
            byName.clear();
        } finally {
            byNameLock.writeLock().unlock();
        }
    }

    /**
     * Called when changing the {@link IdStrategy}.
     * @since 1.566
     */
    public static void rekey() {
        final IdStrategy strategy = idStrategy();
        byNameLock.writeLock().lock();
        try {
            for (Map.Entry<String, User> e : byName.entrySet()) {
                String idkey = strategy.keyFor(e.getValue().id);
                if (!idkey.equals(e.getKey())) {
                    // need to remap
                    byName.remove(e.getKey());
                    byName.putIfAbsent(idkey, e.getValue());
                }
            }
        } finally {
            byNameLock.writeLock().unlock();
        }
    }

    /**
     * Returns the user name.
     */
    public @Nonnull String getDisplayName() {
        return getFullName();
    }

    /** true if {@link AbstractBuild#hasParticipant} or {@link hudson.model.Cause.UserIdCause} */
    private boolean relatedTo(@Nonnull AbstractBuild<?,?> b) {
        if (b.hasParticipant(this)) {
            return true;
        }
        for (Cause cause : b.getCauses()) {
            if (cause instanceof Cause.UserIdCause) {
                String userId = ((Cause.UserIdCause) cause).getUserId();
                if (userId != null && idStrategy().equals(userId, getId())) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Gets the list of {@link Build}s that include changes by this user,
     * by the timestamp order.
     */
    @WithBridgeMethods(List.class)
    public @Nonnull RunList getBuilds() {
    	return new RunList<Run<?,?>>(Jenkins.getInstance().getAllItems(Job.class)).filter(new Predicate<Run<?,?>>() {
            @Override public boolean apply(Run<?,?> r) {
                return r instanceof AbstractBuild && relatedTo((AbstractBuild<?,?>) r);
            }
        });
    }

    /**
     * Gets all the {@link AbstractProject}s that this user has committed to.
     * @since 1.191
     */
    public @Nonnull Set<AbstractProject<?,?>> getProjects() {
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
        return new File(getRootDir(), idStrategy().filenameOf(id) +"/config.xml");
    }

    private static final File[] getLegacyConfigFilesFor(final String id) {
        return getRootDir().listFiles(new FileFilter() {
            @Override
            public boolean accept(File pathname) {
                return pathname.isDirectory() && new File(pathname, "config.xml").isFile() && idStrategy().equals(
                        pathname.getName(), id);
            }
        });
    }

    /**
     * Gets the directory where Hudson stores user information.
     */
    private static File getRootDir() {
        return new File(Jenkins.getInstance().getRootDir(), "users");
    }

    /**
     * Is the ID allowed? Some are prohibited for security reasons. See SECURITY-166.
     * <p/>
     * Note that this is only enforced when saving. These users are often created
     * via the constructor (and even listed on /asynchPeople), but our goal is to
     * prevent anyone from logging in as these users. Therefore, we prevent
     * saving a User with one of these ids.
     *
     * @return true if the username or fullname is valid
     * @since 1.600
     */
    public static boolean isIdOrFullnameAllowed(String id) {
        for (String invalidId : ILLEGAL_PERSISTED_USERNAMES) {
            if (id.equalsIgnoreCase(invalidId))
                return false;
        }
        return true;
    }

    /**
     * Save the settings to a file.
     */
    public synchronized void save() throws IOException, FormValidation {
        if (! isIdOrFullnameAllowed(id)) {
            throw FormValidation.error(Messages.User_IllegalUsername(id));
        }
        if (! isIdOrFullnameAllowed(fullName)) {
            throw FormValidation.error(Messages.User_IllegalFullname(fullName));
        }
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
        final IdStrategy strategy = idStrategy();
        byNameLock.readLock().lock();
        try {
            byName.remove(strategy.keyFor(id));
        } finally {
            byNameLock.readLock().unlock();
        }
        Util.deleteRecursive(new File(getRootDir(), strategy.filenameOf(id)));
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

        JSONObject json = req.getSubmittedForm();

        fullName = json.getString("fullName");
        description = json.getString("description");

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
        if (idStrategy().equals(id, Jenkins.getAuthentication().getName())) {
            rsp.sendError(HttpServletResponse.SC_BAD_REQUEST, "Cannot delete self");
            return;
        }

        delete();

        rsp.sendRedirect2("../..");
    }

    public void doRssAll(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
        rss(req, rsp, " all builds", getBuilds(), Run.FEED_ADAPTER);
    }

    public void doRssFailed(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
        rss(req, rsp, " regression builds", getBuilds().regressionOnly(), Run.FEED_ADAPTER);
    }

    public void doRssLatest(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
        final List<Run> lastBuilds = new ArrayList<Run>();
        for (AbstractProject<?,?> p : Jenkins.getInstance().getAllItems(AbstractProject.class)) {
            for (AbstractBuild<?,?> b = p.getLastBuild(); b != null; b = b.getPreviousBuild()) {
                if (relatedTo(b)) {
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
     * The key needs to be generated by {@link IdStrategy#keyFor(String)}.
     */
    @GuardedBy("byNameLock")
    private static final ConcurrentMap<String,User> byName = new ConcurrentHashMap<String, User>();

    /**
     * This lock is used to guard access to the {@link #byName} map. Use
     * {@link java.util.concurrent.locks.ReadWriteLock#readLock()} for normal access and
     * {@link java.util.concurrent.locks.ReadWriteLock#writeLock()} for {@link #rekey()} or any other operation
     * that requires operating on the map as a whole.
     */
    private static final ReadWriteLock byNameLock = new ReentrantReadWriteLock();

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
                return (idStrategy().equals(a.getName(), id) && !(a instanceof AnonymousAuthenticationToken))
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
        final IdStrategy strategy = idStrategy();
        return hasPermission(Jenkins.ADMINISTER) && !strategy.equals(id, Jenkins.getAuthentication().getName())
                && new File(getRootDir(), strategy.filenameOf(id)).exists();
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
        Authentication authentication;
        try {
            authentication = impersonate();
        } catch (UsernameNotFoundException x) {
            LOGGER.log(Level.FINE, "cannot look up authorities for " + id, x);
            return Collections.emptyList();
        }
        for (GrantedAuthority a : authentication.getAuthorities()) {
            if (a.equals(SecurityRealm.AUTHENTICATED_AUTHORITY)) {
                continue;
            }
            String n = a.getAuthority();
            if (n != null && !idStrategy().equals(n, id)) {
                r.add(n);
            }
        }
        Collections.sort(r, String.CASE_INSENSITIVE_ORDER);
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


    /**
     * Resolve user ID from id.
     * @since TODO
     */
    @Extension
    @Restricted(NoExternalUse.class)
    public static class UserIDCanonicalIdResolver extends User.CanonicalIdResolver {

        @Override
        public String resolveCanonicalId(String idOrFullName, Map<String, ?> context) {
            // use the same as DefaultUserCanonicalIdResolver
            String _id = idOrFullName.replace('\\', '_').replace('/', '_').replace('<','_')
                                            .replace('>', '_');  // 4 replace() still faster than regex
            Jenkins j = Jenkins.getInstance();
            if (j == null) {
                return null;
            }
            IdStrategy userIdStrategy = j.getSecurityRealm().getUserIdStrategy();
            Collection<User> users = User.getAll();
            for (User u : users) {
                if (userIdStrategy.equals(u.getId(), _id)) {
                    return u.getId();
                }
            }

            // XXX I have seen files created in AD that are in the form DOMAIN\User so not sure why they get through but lets also try those...
            for (User u : users) {
                if (userIdStrategy.equals(u.getId(), idOrFullName)) {
                    return u.getId();
                }
            }
            return null;
        }

        @Override
        public int getPriority() {
            // should always come first so that ID that are ids get mapped correctly
            return Integer.MAX_VALUE;
        }

    }

    /**
     * Jenkins now refuses to let the user login if he/she doesn't exist in {@link SecurityRealm},
     * which was necessary to make sure users removed from the backend will get removed from the frontend.
     * <p>
     * Unfortunately this infringed some legitimate use cases of creating Jenkins-local users for
     * automation purposes. This escape hatch switch can be enabled to resurrect that behaviour.
     *
     * JENKINS-22346.
     */
    public static boolean ALLOW_NON_EXISTENT_USER_TO_LOGIN = Boolean.getBoolean(User.class.getName()+".allowNonExistentUserToLogin");
}

