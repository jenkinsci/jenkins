/*
 * The MIT License
 *
 * Copyright (c) 2004-2018, Sun Microsystems, Inc., Kohsuke Kawaguchi, Erik Ramfelt,
 * Tom Huybrechts, Vincent Latombe, CloudBees, Inc.
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
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.BulkChange;
import hudson.CopyOnWrite;
import hudson.Extension;
import hudson.ExtensionList;
import hudson.ExtensionPoint;
import hudson.Util;
import hudson.XmlFile;
import hudson.init.InitMilestone;
import hudson.init.Initializer;
import hudson.model.listeners.SaveableListener;
import hudson.security.ACL;
import hudson.security.AccessControlled;
import hudson.security.SecurityRealm;
import hudson.security.UserMayOrMayNotExistException2;
import hudson.tasks.UserAvatarResolver;
import hudson.util.FormValidation;
import hudson.util.RunList;
import hudson.util.XStream2;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.model.IdStrategy;
import jenkins.model.Jenkins;
import jenkins.model.Loadable;
import jenkins.model.ModelObjectWithContextMenu;
import jenkins.scm.RunWithSCM;
import jenkins.search.SearchGroup;
import jenkins.security.ImpersonatingUserDetailsService2;
import jenkins.security.LastGrantedAuthoritiesProperty;
import jenkins.security.UserDetailsCache;
import jenkins.util.SystemProperties;
import org.jenkinsci.Symbol;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.Beta;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.StaplerProxy;
import org.kohsuke.stapler.StaplerRequest2;
import org.kohsuke.stapler.StaplerResponse2;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;
import org.kohsuke.stapler.interceptor.RequirePOST;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

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
 * @author Kohsuke Kawaguchi
 */
@ExportedBean
public class User extends AbstractModelObject implements AccessControlled, DescriptorByNameOwner, Loadable, Saveable, Comparable<User>, ModelObjectWithContextMenu, StaplerProxy {

    public static final XStream2 XSTREAM = new XStream2();
    private static final Logger LOGGER = Logger.getLogger(User.class.getName());
    static final String CONFIG_XML = "config.xml";

    /**
     * Escape hatch for StaplerProxy-based access control
     */
    @Restricted(NoExternalUse.class)
    @SuppressFBWarnings(value = "MS_SHOULD_BE_FINAL", justification = "for script console")
    public static /* Script Console modifiable */ boolean SKIP_PERMISSION_CHECK = SystemProperties.getBoolean(User.class.getName() + ".skipPermissionCheck");

    /**
     * Jenkins now refuses to let the user login if he/she doesn't exist in {@link SecurityRealm},
     * which was necessary to make sure users removed from the backend will get removed from the frontend.
     * <p>
     * Unfortunately this infringed some legitimate use cases of creating Jenkins-local users for
     * automation purposes. This escape hatch switch can be enabled to resurrect that behaviour.
     * <p>
     * See <a href="https://issues.jenkins.io/browse/JENKINS-22346">JENKINS-22346</a>.
     */
    @SuppressFBWarnings(value = "MS_SHOULD_BE_FINAL", justification = "for script console")
    public static boolean ALLOW_NON_EXISTENT_USER_TO_LOGIN = SystemProperties.getBoolean(User.class.getName() + ".allowNonExistentUserToLogin");

    /**
     * Jenkins historically created a (usually) ephemeral user record when an user with Overall/Administer permission
     * accesses a /user/arbitraryName URL.
     * <p>
     * Unfortunately this constitutes a CSRF vulnerability, as malicious users can make admins create arbitrary numbers
     * of ephemeral user records, so the behavior was changed in Jenkins 2.44 / 2.32.2.
     * <p>
     * As some users may be relying on the previous behavior, setting this to true restores the previous behavior. This
     * is not recommended.
     * <p>
     * SECURITY-406.
     */
    @Restricted(NoExternalUse.class)
    @SuppressFBWarnings(value = "MS_SHOULD_BE_FINAL", justification = "for script console")
    public static boolean ALLOW_USER_CREATION_VIA_URL = SystemProperties.getBoolean(User.class.getName() + ".allowUserCreationViaUrl");

    /**
     * The username of the 'unknown' user used to avoid null user references.
     */
    private static final String UNKNOWN_USERNAME = "unknown";

    /**
     * These usernames should not be used by real users logging into Jenkins. Therefore, we prevent
     * users with these names from being saved.
     */
    private static final String[] ILLEGAL_PERSISTED_USERNAMES = new String[]{ACL.ANONYMOUS_USERNAME,
            ACL.SYSTEM_USERNAME, UNKNOWN_USERNAME};

    @SuppressFBWarnings(value = "SS_SHOULD_BE_STATIC", justification = "Reserved for future use")
    private final int version = 10; // Not currently used, but it may be helpful in the future to store a version.
    private String id;
    private volatile String fullName;
    private volatile String description;

    @CopyOnWrite
    private volatile List<UserProperty> properties = new ArrayList<>();

    static {
        XSTREAM.alias("user", User.class);
    }

    private User(String id, String fullName) {
        this.id = id;
        this.fullName = fullName;
        load(id);
    }

    @Override
    public void load() {
        load(id);
    }

    private void load(String userId) {
        clearExistingProperties();
        loadFromUserConfigFile(userId);
        removeNullsThatFailedToLoad();
        allocateDefaultPropertyInstancesAsNeeded();
        setUserToProperties();
    }

    private void setUserToProperties() {
        for (UserProperty p : properties) {
            p.setUser(this);
        }
    }

    private void allocateDefaultPropertyInstancesAsNeeded() {
        for (UserPropertyDescriptor d : UserProperty.all()) {
            if (getProperty(d.clazz) == null) {
                UserProperty up = d.newInstance(this);
                if (up != null)
                    properties.add(up);
            }
        }
    }

    private void removeNullsThatFailedToLoad() {
        properties.removeIf(Objects::isNull);
    }

    private void loadFromUserConfigFile(String userId) {
        XmlFile config = getConfigFile();
        try {
            if (config != null && config.exists()) {
                config.unmarshal(this);
                this.id = userId;
            }
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to load " + config, e);
        }
    }

    private void clearExistingProperties() {
        properties.clear();
    }

    private XmlFile getConfigFile() {
        File existingUserFolder = getExistingUserFolder();
        return existingUserFolder == null ? null : new XmlFile(XSTREAM, new File(existingUserFolder, CONFIG_XML));
    }

    /**
     * Returns the {@link jenkins.model.IdStrategy} for use with {@link User} instances. See
     * {@link hudson.security.SecurityRealm#getUserIdStrategy()}
     *
     * @return the {@link jenkins.model.IdStrategy} for use with {@link User} instances.
     * @since 1.566
     */
    @NonNull
    public static IdStrategy idStrategy() {
        Jenkins j = Jenkins.get();
        SecurityRealm realm = j.getSecurityRealm();
        if (realm == null) {
            return IdStrategy.CASE_INSENSITIVE;
        }
        return realm.getUserIdStrategy();
    }

    @Override
    public int compareTo(@NonNull User that) {
        return idStrategy().compare(this.id, that.id);
    }

    @Exported
    public String getId() {
        return id;
    }

    public @NonNull String getUrl() {
        return "user/" + Util.rawEncode(idStrategy().keyFor(id));
    }

    @Override
    public @NonNull String getSearchUrl() {
        return "/user/" + Util.rawEncode(idStrategy().keyFor(id));
    }

    @Override
    public String getSearchIcon() {
        return UserAvatarResolver.resolve(this, "48x48");
    }

    @Override
    public SearchGroup getSearchGroup() {
        return SearchGroup.get(SearchGroup.UserSearchGroup.class);
    }

    /**
     * The URL of the user page.
     */
    @Exported(visibility = 999)
    public @NonNull String getAbsoluteUrl() {
        return Jenkins.get().getRootUrl() + getUrl();
    }

    /**
     * Gets the human readable name of this user.
     * This is configurable by the user.
     */
    @Exported(visibility = 999)
    public @NonNull String getFullName() {
        return fullName;
    }

    /**
     * Sets the human readable name of the user.
     * If the input parameter is empty, the user's ID will be set.
     */
    public void setFullName(String name) {
        if (Util.fixEmptyAndTrim(name) == null) name = id;
        this.fullName = name;
    }

    @Exported
    public @CheckForNull String getDescription() {
        return description;
    }

    /**
     * Sets the description of the user.
     *
     * @since 1.609
     */
    public void setDescription(String description) {
        this.description = description;
    }

    /**
     * Gets the user properties configured for this user.
     */
    public Map<Descriptor<UserProperty>, UserProperty> getProperties() {
        return Descriptor.toMap(properties);
    }

    /**
     * Updates the user object by adding a property.
     */
    public synchronized void addProperty(@NonNull UserProperty p) throws IOException {
        UserProperty old = getProperty(p.getClass());
        List<UserProperty> ps = new ArrayList<>(properties);
        if (old != null)
            ps.remove(old);
        ps.add(p);
        p.setUser(this);
        properties = ps;
        save();
    }

    /**
     * Expand {@link #addProperty(UserProperty)} for multiple properties to be done at once.
     * Expected to be used by the categorized configuration pages to update part of the properties.
     * The properties not included in the list will be let untouched.
     * It will call the {@link UserProperty#setUser(User)} method and at the end, {@link #save()} once.
     *
     * @since 2.468
     */
    public synchronized void addProperties(@NonNull List<UserProperty> multipleProperties) throws IOException {
        List<UserProperty> newProperties = new ArrayList<>(this.properties);
        for (UserProperty property : multipleProperties) {
            UserProperty oldProp = getProperty(property.getClass());
            if (oldProp != null) {
                newProperties.remove(oldProp);
            }
            newProperties.add(property);
            property.setUser(this);
        }

        this.properties = newProperties;
        this.save();
    }

    /**
     * List of all {@link UserProperty}s exposed primarily for the remoting API.
     */
    @Exported(name = "property", inline = true)
    public List<UserProperty> getAllProperties() {
        if (hasPermission(Jenkins.ADMINISTER)) {
            return Collections.unmodifiableList(properties);
        }

        return Collections.emptyList();
    }

    /**
     * Gets the specific property, or null.
     */
    public <T extends UserProperty> T getProperty(Class<T> clazz) {
        for (UserProperty p : properties) {
            if (clazz.isInstance(p))
                return clazz.cast(p);
        }
        return null;
    }

    /**
     * Creates an {@link Authentication} object that represents this user.
     * <p>
     * This method checks with {@link SecurityRealm} if the user is a valid user that can login to the security realm.
     * If {@link SecurityRealm} is a kind that does not support querying information about other users, this will
     * use {@link LastGrantedAuthoritiesProperty} to pick up the granted authorities as of the last time the user has
     * logged in.
     *
     * @throws UsernameNotFoundException If this user is not a valid user in the backend {@link SecurityRealm}.
     * @since 2.266
     */
    public @NonNull Authentication impersonate2() throws UsernameNotFoundException {
        return this.impersonate(this.getUserDetailsForImpersonation2());
    }

    /**
     * @deprecated use {@link #impersonate2}
     * @since 1.419
     */
    @Deprecated
    public @NonNull org.acegisecurity.Authentication impersonate() throws org.acegisecurity.userdetails.UsernameNotFoundException {
        try {
            return org.acegisecurity.Authentication.fromSpring(impersonate2());
        } catch (AuthenticationException x) {
            throw org.acegisecurity.AuthenticationException.fromSpring(x);
        }
    }

    /**
     * This method checks with {@link SecurityRealm} if the user is a valid user that can login to the security realm.
     * If {@link SecurityRealm} is a kind that does not support querying information about other users, this will
     * use {@link LastGrantedAuthoritiesProperty} to pick up the granted authorities as of the last time the user has
     * logged in.
     *
     * @return userDetails for the user, in case he's not found but seems legitimate, we provide a userDetails with minimum access
     * @throws UsernameNotFoundException If this user is not a valid user in the backend {@link SecurityRealm}.
     * @since 2.266
     */
    public @NonNull UserDetails getUserDetailsForImpersonation2() throws UsernameNotFoundException {
        ImpersonatingUserDetailsService2 userDetailsService = new ImpersonatingUserDetailsService2(
                Jenkins.get().getSecurityRealm().getSecurityComponents().userDetails2
        );

        try {
            UserDetails userDetails = userDetailsService.loadUserByUsername(id);
            LOGGER.log(Level.FINE, "Impersonation of the user {0} was a success", id);
            return userDetails;
        } catch (UserMayOrMayNotExistException2 e) {
            LOGGER.log(Level.FINE, "The user {0} may or may not exist in the SecurityRealm, so we provide minimum access", id);
        } catch (UsernameNotFoundException e) {
            if (ALLOW_NON_EXISTENT_USER_TO_LOGIN) {
                LOGGER.log(Level.FINE, "The user {0} was not found in the SecurityRealm but we are required to let it pass, due to ALLOW_NON_EXISTENT_USER_TO_LOGIN", id);
            } else {
                LOGGER.log(Level.FINE, "The user {0} was not found in the SecurityRealm", id);
                throw e;
            }
        }

        return new LegitimateButUnknownUserDetails(id);
    }

    /**
     * @deprecated use {@link #getUserDetailsForImpersonation2}
     */
    @Deprecated
    public @NonNull org.acegisecurity.userdetails.UserDetails getUserDetailsForImpersonation() throws org.acegisecurity.userdetails.UsernameNotFoundException {
        try {
            return org.acegisecurity.userdetails.UserDetails.fromSpring(getUserDetailsForImpersonation2());
        } catch (AuthenticationException x) {
            throw org.acegisecurity.AuthenticationException.fromSpring(x);
        }
    }

    /**
     * Only used for a legitimate user we have no idea about. We give it only minimum access
     */
    private static class LegitimateButUnknownUserDetails extends org.springframework.security.core.userdetails.User {
        private LegitimateButUnknownUserDetails(String username) throws IllegalArgumentException {
            super(
                    username, "",
                    true, true, true, true,
                    Set.of(SecurityRealm.AUTHENTICATED_AUTHORITY2)
            );
        }
    }

    /**
     * Creates an {@link Authentication} object that represents this user using the given userDetails
     *
     * @param userDetails Provided by {@link #getUserDetailsForImpersonation2()}.
     * @see #getUserDetailsForImpersonation2()
     */
    @Restricted(NoExternalUse.class)
    public @NonNull Authentication impersonate(@NonNull UserDetails userDetails) {
        return new UsernamePasswordAuthenticationToken(userDetails.getUsername(), "", userDetails.getAuthorities());
    }

    /**
     * Accepts the new description.
     */
    @RequirePOST
    public void doSubmitDescription(StaplerRequest2 req, StaplerResponse2 rsp) throws IOException {
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
    public static @NonNull User getUnknown() {
        return getById(UNKNOWN_USERNAME, true);
    }

    /**
     * Gets the {@link User} object by its id or full name.
     *
     * @param create If true, this method will never return null for valid input
     *               (by creating a new {@link User} object if none exists.)
     *               If false, this method will return null if {@link User} object
     *               with the given name doesn't exist.
     * @return Requested user. May be {@code null} if a user does not exist and
     * {@code create} is false.
     * @deprecated use {@link User#get(String, boolean, java.util.Map)}
     */
    @Deprecated
    public static @Nullable User get(String idOrFullName, boolean create) {
        return get(idOrFullName, create, Collections.emptyMap());
    }

    /**
     * Gets the {@link User} object by its id or full name.
     * <p>
     * In order to resolve the user ID, the method invokes {@link CanonicalIdResolver} extension points.
     * Note that it may cause significant performance degradation.
     * If you are sure the passed value is a User ID, it is recommended to use {@link #getById(String, boolean)}.
     *
     * @param create  If true, this method will never return null for valid input
     *                (by creating a new {@link User} object if none exists.)
     *                If false, this method will return null if {@link User} object
     *                with the given name doesn't exist.
     * @param context contextual environment this user idOfFullName was retrieved from,
     *                that can help resolve the user ID
     * @return An existing or created user. May be {@code null} if a user does not exist and
     * {@code create} is false.
     */
    public static @Nullable User get(String idOrFullName, boolean create, @NonNull Map context) {
        if (idOrFullName == null) {
            return null;
        }

        User user = AllUsers.get(idOrFullName);
        if (user != null) {
            return user;
        }

        String id = CanonicalIdResolver.resolve(idOrFullName, context);
        return getOrCreateById(id, idOrFullName, create);
    }

    /**
     * Retrieve a user by its ID, and create a new one if requested.
     *
     * @return An existing or created user. May be {@code null} if a user does not exist and
     * {@code create} is false.
     */
    private static @Nullable User getOrCreateById(@NonNull String id, @NonNull String fullName, boolean create) {
        User u = AllUsers.get(id);
        if (u == null && (create || UserIdMapper.getInstance().isMapped(id))) {
            u = new User(id, fullName);
            AllUsers.put(id, u);
            if (!id.equals(fullName) && !UserIdMapper.getInstance().isMapped(id)) {
                try {
                    u.save();
                } catch (IOException x) {
                    LOGGER.log(Level.WARNING, "Failed to save user configuration for " + id, x);
                }
            }
        }
        return u;
    }

    /**
     * Gets the {@link User} object by its id or full name.
     * <p>
     * Creates a user on-demand.
     *
     * <p>
     * Use {@link #getById} when you know you have an ID.
     * In this method Jenkins will try to resolve the {@link User} by full name with help of various
     * {@link hudson.tasks.UserNameResolver}.
     * This is slow (see JENKINS-23281).
     *
     * @deprecated This method is deprecated, because it causes unexpected {@link User} creation
     * by API usage code and causes performance degradation of used to retrieve users by ID.
     * Use {@link #getById} when you know you have an ID.
     * Otherwise use {@link #getOrCreateByIdOrFullName(String)} or {@link #get(String, boolean, Map)}.
     */
    @Deprecated
    public static @NonNull User get(String idOrFullName) {
        return getOrCreateByIdOrFullName(idOrFullName);
    }

    /**
     * Get the user by ID or Full Name.
     * <p>
     * If the user does not exist, creates a new one on-demand.
     *
     * <p>
     * Use {@link #getById} when you know you have an ID.
     * In this method Jenkins will try to resolve the {@link User} by full name with help of various
     * {@link hudson.tasks.UserNameResolver}.
     * This is slow (see JENKINS-23281).
     *
     * @param idOrFullName User ID or full name
     * @return User instance. It will be created on-demand.
     * @since 2.91
     */
    public static @NonNull User getOrCreateByIdOrFullName(@NonNull String idOrFullName) {
        return get(idOrFullName, true, Collections.emptyMap());
    }


    /**
     * Gets the {@link User} object representing the currently logged-in user, or null
     * if the current user is anonymous.
     *
     * @since 1.172
     */
    public static @CheckForNull User current() {
        return get2(Jenkins.getAuthentication2());
    }

    /**
     * Gets the {@link User} object representing the supplied {@link Authentication} or
     * {@code null} if the supplied {@link Authentication} is either anonymous or {@code null}
     *
     * @param a the supplied {@link Authentication} .
     * @return a {@link User} object for the supplied {@link Authentication} or {@code null}
     * @since 2.266
     */
    public static @CheckForNull User get2(@CheckForNull Authentication a) {
        if (a == null || a instanceof AnonymousAuthenticationToken)
            return null;

        // Since we already know this is a name, we can just call getOrCreateById with the name directly.
        return getById(a.getName(), true);
    }

    /**
     * @deprecated use {@link #get2(Authentication)}
     * @since 1.609
     */
    @Deprecated
    public static @CheckForNull User get(@CheckForNull org.acegisecurity.Authentication a) {
        return get2(a != null ? a.toSpring() : null);
    }

    /**
     * Gets the {@link User} object by its {@code id}
     *
     * @param id     the id of the user to retrieve and optionally create if it does not exist.
     * @param create If {@code true}, this method will never return {@code null} for valid input (by creating a
     *               new {@link User} object if none exists.) If {@code false}, this method will return
     *               {@code null} if {@link User} object with the given id doesn't exist.
     * @return the a User whose id is {@code id}, or {@code null} if {@code create} is {@code false}
     * and the user does not exist.
     * @since 1.651.2 / 2.3
     */
    public static @Nullable User getById(String id, boolean create) {
        return getOrCreateById(id, id, create);
    }

    /**
     * Gets all the users.
     */
    public static @NonNull Collection<User> getAll() {
        final IdStrategy strategy = idStrategy();
        ArrayList<User> users = new ArrayList<>(AllUsers.values());
        users.sort((o1, o2) -> strategy.compare(o1.getId(), o2.getId()));
        return users;
    }

    /**
     * Called from {@link Jenkins#reload}.
     */
    @Restricted(Beta.class)
    public static void reload() throws IOException {
        UserIdMapper.getInstance().reload();
        AllUsers.reload();
    }

    /**
     * Called when changing the {@link IdStrategy}.
     *
     * @since 1.566
     */
    public static void rekey() {
        /* There are many and varied ways in which this could cause erratic or
            problematic behavior. Such changes should really only occur during initial
            setup and under very controlled situations. After this sort of a change
            the whole webapp should restart. It's possible that this rekeying,
            or greater issues in the realm change, could affect currently logged
            in users and even the user making the change. */
        try {
            reload();
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to perform rekey operation.", e);
        }
    }

    /**
     * Returns the user name.
     */
    @Override
    public @NonNull String getDisplayName() {
        return getFullName();
    }

    /**
     * true if {@link RunWithSCM#hasParticipant} or {@link hudson.model.Cause.UserIdCause}
     */
    private boolean relatedTo(@NonNull Run<?, ?> b) {
        if (b instanceof RunWithSCM && ((RunWithSCM) b).hasParticipant(this)) {
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
     * Searches for builds which include changes by this user or which were triggered by this user.
     */
    @SuppressWarnings("unchecked")
    @WithBridgeMethods(List.class)
    public @NonNull RunList getBuilds() {
        return RunList.fromJobs((Iterable) Jenkins.get().
                allItems(Job.class)).filter((Predicate<Run<?, ?>>) this::relatedTo);
    }

    /**
     * Gets all the {@link AbstractProject}s that this user has committed to.
     *
     * @since 1.191
     */
    public @NonNull Set<AbstractProject<?, ?>> getProjects() {
        Set<AbstractProject<?, ?>> r = new HashSet<>();
        for (AbstractProject<?, ?> p : Jenkins.get().allItems(AbstractProject.class, p -> p.hasParticipant(this)))
            r.add(p);
        return r;
    }

    @Override
    public String toString() {
        return id;
    }

    /**
     * Called by tests in the JTH. Otherwise this shouldn't be called.
     * Even in the tests this usage is questionable.
     * @deprecated removed without replacement
     */
    @Deprecated
    public static void clear() {
        if (ExtensionList.lookup(AllUsers.class).isEmpty()) {
            return;
        }
        UserIdMapper.getInstance().clear();
        AllUsers.clear();
    }

    private static File getConfigFileFor(String id) {
        return new File(getUserFolderFor(id), "config.xml");
    }

    private static File getUserFolderFor(String id) {
        return new File(getRootDir(), idStrategy().filenameOf(id));
    }
    /**
     * Returns the folder that store all the user information.
     * Useful for plugins to save a user-specific file aside the config.xml.
     * Exposes implementation details that may be subject to change.
     *
     * @return The folder containing the user configuration files or {@code null} if the user was not yet saved.
     *
     * @since 2.129
     */

    public @CheckForNull File getUserFolder() {
        return getExistingUserFolder();
    }

    private @CheckForNull File getExistingUserFolder() {
        return UserIdMapper.getInstance().getDirectory(id);
    }

    /**
     * Gets the directory where Hudson stores user information.
     */
    static File getRootDir() {
        return new File(Jenkins.get().getRootDir(), "users");
    }

    /**
     * Is the ID allowed? Some are prohibited for security reasons. See SECURITY-166.
     * <p>
     * Note that this is only enforced when saving. These users are often created
     * via the constructor (and even listed on /asynchPeople), but our goal is to
     * prevent anyone from logging in as these users. Therefore, we prevent
     * saving a User with one of these ids.
     *
     * @param id ID to be checked
     * @return {@code true} if the username or fullname is valid.
     * For {@code null} or blank IDs returns {@code false}.
     * @since 1.600
     */
    public static boolean isIdOrFullnameAllowed(@CheckForNull String id) {
        if (id == null || id.isBlank()) {
            return false;
        }
        final String trimmedId = id.trim();
        for (String invalidId : ILLEGAL_PERSISTED_USERNAMES) {
            if (trimmedId.equalsIgnoreCase(invalidId))
                return false;
        }
        return true;
    }

    /**
     * Save the user configuration.
     */
    @Override
    public synchronized void save() throws IOException {
        if (!isIdOrFullnameAllowed(id)) {
            throw FormValidation.error(Messages.User_IllegalUsername(id));
        }
        if (!isIdOrFullnameAllowed(fullName)) {
            throw FormValidation.error(Messages.User_IllegalFullname(fullName));
        }
        if (BulkChange.contains(this)) {
            return;
        }
        XmlFile xmlFile = new XmlFile(XSTREAM, constructUserConfigFile());
        xmlFile.write(this);
        SaveableListener.fireOnChange(this, xmlFile);
    }

    private File constructUserConfigFile() throws IOException {
        return new File(putUserFolderIfAbsent(), CONFIG_XML);
    }

    private File putUserFolderIfAbsent() throws IOException {
        return UserIdMapper.getInstance().putIfAbsent(id);
    }

    /**
     * Deletes the data directory and removes this user from Hudson.
     *
     * @throws IOException if we fail to delete.
     */
    public void delete() throws IOException {
        String idKey = idStrategy().keyFor(id);
        File existingUserFolder = getExistingUserFolder();
        UserIdMapper.getInstance().remove(id);
        AllUsers.remove(id);
        deleteExistingUserFolder(existingUserFolder);
        UserDetailsCache.get().invalidate(idKey);
    }

    private void deleteExistingUserFolder(File existingUserFolder) throws IOException {
        if (existingUserFolder != null && existingUserFolder.exists()) {
            Util.deleteRecursive(existingUserFolder);
        }
    }

    /**
     * Exposed remote API.
     */
    public Api getApi() {
        return new Api(this);
    }

    /**
     * Deletes this user from Hudson.
     */
    @RequirePOST
    public void doDoDelete(StaplerRequest2 req, StaplerResponse2 rsp) throws IOException {
        checkPermission(Jenkins.ADMINISTER);
        if (idStrategy().equals(id, Jenkins.getAuthentication2().getName())) {
            rsp.sendError(HttpServletResponse.SC_BAD_REQUEST, "Cannot delete self");
            return;
        }

        delete();

        rsp.sendRedirect2("../..");
    }

    public void doRssAll(StaplerRequest2 req, StaplerResponse2 rsp) throws IOException, ServletException {
        RSS.rss(req, rsp, "Jenkins:" + getDisplayName() + " (all builds)", getUrl(), getBuilds().newBuilds());
    }

    public void doRssFailed(StaplerRequest2 req, StaplerResponse2 rsp) throws IOException, ServletException {
        RSS.rss(req, rsp, "Jenkins:" + getDisplayName() + " (failed builds)", getUrl(), getBuilds().regressionOnly());
    }

    public void doRssLatest(StaplerRequest2 req, StaplerResponse2 rsp) throws IOException, ServletException {
        final List<Run> lastBuilds = new ArrayList<>();
        for (Job<?, ?> p : Jenkins.get().allItems(Job.class)) {
            for (Run<?, ?> b = p.getLastBuild(); b != null; b = b.getPreviousBuild()) {
                if (relatedTo(b)) {
                    lastBuilds.add(b);
                    break;
                }
            }
        }
        // historically these have been reported sorted by project name, we switched to the lazy iteration
        // so we only have to sort the sublist of runs rather than the full list of irrelevant projects
        lastBuilds.sort((o1, o2) -> Items.BY_FULL_NAME.compare(o1.getParent(), o2.getParent()));
        RSS.rss(req, rsp, "Jenkins:" + getDisplayName() + " (latest builds)", getUrl(), RunList.fromRuns(lastBuilds), Run.FEED_ADAPTER_LATEST);
    }

    @Override
    @NonNull
    public ACL getACL() {
        ACL base = Jenkins.get().getAuthorizationStrategy().getACL(this);
        // always allow a non-anonymous user full control of himself.
        return ACL.lambda2((a, permission) -> (idStrategy().equals(a.getName(), id) && !(a instanceof AnonymousAuthenticationToken))
                || base.hasPermission2(a, permission));
    }

    /**
     * With ADMINISTER permission, can delete users with persisted data but can't delete self.
     */
    public boolean canDelete() {
        final IdStrategy strategy = idStrategy();
        return hasPermission(Jenkins.ADMINISTER) && !strategy.equals(id, Jenkins.getAuthentication2().getName())
                && UserIdMapper.getInstance().isMapped(id);
    }

    /**
     * Checks for authorities (groups) associated with this user.
     * If the caller lacks {@link Jenkins#ADMINISTER}, or any problems arise, returns an empty list.
     * {@link SecurityRealm#AUTHENTICATED_AUTHORITY2} and the username, if present, are omitted.
     *
     * @return a possibly empty list
     * @since 1.498
     */
    public @NonNull List<String> getAuthorities() {
        if (!Jenkins.get().hasPermission(Jenkins.ADMINISTER)) {
            return Collections.emptyList();
        }
        List<String> r = new ArrayList<>();
        Authentication authentication;
        try {
            authentication = impersonate2();
        } catch (UsernameNotFoundException x) {
            LOGGER.log(Level.FINE, "cannot look up authorities for " + id, x);
            return Collections.emptyList();
        }
        for (GrantedAuthority a : authentication.getAuthorities()) {
            if (a.equals(SecurityRealm.AUTHENTICATED_AUTHORITY2)) {
                continue;
            }
            String n = a.getAuthority();
            if (n != null && !idStrategy().equals(n, id)) {
                r.add(n);
            }
        }
        r.sort(String.CASE_INSENSITIVE_ORDER);
        return r;
    }

    public Object getDynamic(String token) {
        for (Action action : getTransientActions()) {
            if (Objects.equals(action.getUrlName(), token))
                return action;
        }
        for (Action action : getPropertyActions()) {
            if (Objects.equals(action.getUrlName(), token))
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
        List<Action> actions = new ArrayList<>();
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
        List<Action> actions = new ArrayList<>();
        for (TransientUserActionFactory factory : TransientUserActionFactory.all()) {
            actions.addAll(factory.createFor(this));
        }
        return Collections.unmodifiableList(actions);
    }

    @Override
    public ContextMenu doContextMenu(StaplerRequest2 request, StaplerResponse2 response) throws Exception {
        return new ContextMenu().from(this, request, response);
    }

    @Override
    @Restricted(NoExternalUse.class)
    public Object getTarget() {
        if (!SKIP_PERMISSION_CHECK) {
            if (!Jenkins.get().hasPermission(Jenkins.READ)) {
                return null;
            }
        }
        return this;
    }

    /**
     * Gets list of Illegal usernames, for which users should not be created.
     * Always includes users from {@link #ILLEGAL_PERSISTED_USERNAMES}
     *
     * @return List of usernames
     */
    @Restricted(NoExternalUse.class)
    /*package*/ static Set<String> getIllegalPersistedUsernames() {
        return new HashSet<>(Arrays.asList(ILLEGAL_PERSISTED_USERNAMES));
    }

    private Object writeReplace() {
        return XmlFile.replaceIfNotAtTopLevel(this, () -> new Replacer(this));
    }

    private static class Replacer {
        private final String id;

        Replacer(User u) {
            id = u.getId();
        }

        private Object readResolve() {
            // Will generally only work if called after UserIdMapper.init:
            return getById(id, false);
        }
    }

    /**
     * Per-{@link Jenkins} holder of all known {@link User}s.
     */
    @Extension
    @Restricted(NoExternalUse.class)
    public static final class AllUsers {

        private final ConcurrentMap<String, User> byName = new ConcurrentHashMap<>();

        @Initializer(after = InitMilestone.JOB_CONFIG_ADAPTED)
        public static void scanAll() {
            for (String userId : UserIdMapper.getInstance().getConvertedUserIds()) {
                User user = new User(userId, userId);
                getInstance().byName.putIfAbsent(idStrategy().keyFor(userId), user);
            }
        }

        /**
         * Keyed by {@link User#id}. This map is used to ensure
         * singleton-per-id semantics of {@link User} objects.
         * <p>
         * The key needs to be generated by {@link IdStrategy#keyFor(String)}.
         */
        private static AllUsers getInstance() {
            return ExtensionList.lookupSingleton(AllUsers.class);
        }

        private static void reload() {
            getInstance().byName.clear();
            UserDetailsCache.get().invalidateAll();
            scanAll();
        }

        private static void clear() {
            getInstance().byName.clear();
        }

        private static void remove(String id) {
            getInstance().byName.remove(idStrategy().keyFor(id));
        }

        private static User get(String id) {
            return getInstance().byName.get(idStrategy().keyFor(id));
        }

        private static void put(String id, User user) {
            getInstance().byName.putIfAbsent(idStrategy().keyFor(id), user);
        }

        private static Collection<User> values() {
            return getInstance().byName.values();
        }
    }

    /**
     * Resolves User IDs by ID, full names or other strings.
     * <p>
     * This extension point may be useful to map SCM user names to Jenkins {@link User} IDs.
     * Currently the extension point is used in {@link User#get(String, boolean, Map)}.
     *
     * @see jenkins.model.DefaultUserCanonicalIdResolver
     * @see FullNameIdResolver
     * @since 1.479
     */
    public abstract static class CanonicalIdResolver implements Describable<CanonicalIdResolver>, ExtensionPoint, Comparable<CanonicalIdResolver> {

        /**
         * context key for realm (domain) where idOrFullName has been retrieved from.
         * Can be used (for example) to distinguish ambiguous committer ID using the SCM URL.
         * Associated Value is a {@link String}
         */
        public static final String REALM = "realm";

        @Override
        public int compareTo(@NonNull CanonicalIdResolver o) {
            // reverse priority order
            return Integer.compare(o.getPriority(), getPriority());
        }

        /**
         * extract user ID from idOrFullName with help from contextual infos.
         * can return {@code null} if no user ID matched the input
         */
        public abstract @CheckForNull String resolveCanonicalId(String idOrFullName, Map<String, ?> context);

        /**
         * Gets priority of the resolver.
         * Higher priority means that it will be checked earlier.
         * <p>
         * Overriding methods must not use {@link Integer#MIN_VALUE}, because it will cause collisions
         * with {@link jenkins.model.DefaultUserCanonicalIdResolver}.
         *
         * @return Priority of the resolver.
         */
        public int getPriority() {
            return 1;
        }

        //Such sorting and collection rebuild is not good for User#get(...) method performance.

        /**
         * Gets all extension points, sorted by priority.
         *
         * @return Sorted list of extension point implementations.
         * @since 2.93
         */
        public static List<CanonicalIdResolver> all() {
            List<CanonicalIdResolver> resolvers = new ArrayList<>(ExtensionList.lookup(CanonicalIdResolver.class));
            Collections.sort(resolvers);
            return resolvers;
        }

        /**
         * Resolves users using all available {@link CanonicalIdResolver}s.
         *
         * @param idOrFullName ID or full name of the user
         * @param context      Context
         * @return Resolved User ID or {@code null} if the user ID cannot be resolved.
         * @since 2.93
         */
        @CheckForNull
        public static String resolve(@NonNull String idOrFullName, @NonNull Map<String, ?> context) {
            for (CanonicalIdResolver resolver : CanonicalIdResolver.all()) {
                String id = resolver.resolveCanonicalId(idOrFullName, context);
                if (id != null) {
                    LOGGER.log(Level.FINE, "{0} mapped {1} to {2}", new Object[]{resolver, idOrFullName, id});
                    return id;
                }
            }

            // De-facto it is not going to happen OOTB, because the current DefaultUserCanonicalIdResolver
            // always returns a value. But we still need to check nulls if somebody disables the extension point
            return null;
        }
    }


    /**
     * Resolve user ID from full name
     */
    @Extension
    @Symbol("fullName")
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
     * Tries to verify if an ID is valid.
     * If so, we do not want to even consider users who might have the same full name.
     */
    @Extension
    @Restricted(NoExternalUse.class)
    public static class UserIDCanonicalIdResolver extends User.CanonicalIdResolver {

        private static /* not final */ boolean SECURITY_243_FULL_DEFENSE =
                SystemProperties.getBoolean(User.class.getName() + ".SECURITY_243_FULL_DEFENSE", true);

        private static final ThreadLocal<Boolean> resolving = ThreadLocal.withInitial(() -> false);

        @Override
        public String resolveCanonicalId(String idOrFullName, Map<String, ?> context) {
            User existing = getById(idOrFullName, false);
            if (existing != null) {
                return existing.getId();
            }
            if (SECURITY_243_FULL_DEFENSE) {
                if (!resolving.get()) {
                    resolving.set(true);
                    try {
                        UserDetails userDetails = UserDetailsCache.get().loadUserByUsername(idOrFullName);
                        return userDetails.getUsername();
                    } catch (UsernameNotFoundException x) {
                        LOGGER.log(Level.FINE, "not sure whether " + idOrFullName + " is a valid username or not", x);
                    } catch (ExecutionException x) {
                        LOGGER.log(Level.FINE, "could not look up " + idOrFullName, x);
                    } finally {
                        resolving.set(false);
                    }
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

}
