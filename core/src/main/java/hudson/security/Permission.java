package hudson.security;

import hudson.model.Hudson;
import hudson.CopyOnWrite;
import net.sf.json.util.JSONUtils;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Iterator;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Permission, which represents activity that requires a security privilege.
 *
 * <p>
 * Each permission is represented by a specific instance of {@link Permission}.
 *
 * @author Kohsuke Kawaguchi
 */
public final class Permission {
    public final Class owner;

    /**
     * Human readable ID of the permission.
     *
     * <p>
     * This name should uniquely determine a permission among
     * its owner class. The name must be a valid Java identifier.
     * <p>
     * The expected naming convention is something like "BrowseWorkspace".
     */
    public final String name;

    /**
     * Bundled {@link Permission} that also implies this permission.
     *
     * <p>
     * This allows us to organize permissions in a hierarchy, so that
     * for example we can say "view workspace" permission is implied by
     * the (broader) "read" permission.
     *
     * <p>
     * The idea here is that for most people, access control based on
     * such broad permission bundle is good enough, and those few
     * that need finer control can do so.
     */
    public final Permission impliedBy;

    public Permission(Class owner, String name, Permission impliedBy) {
        if(!JSONUtils.isJavaIdentifier(name))
            throw new IllegalArgumentException(name+" is not a Java identifier");
        this.owner = owner;
        this.name = name;
        this.impliedBy = impliedBy;

        synchronized (PERMISSIONS) {
            Group g = PERMISSIONS.get(owner);
            if(g==null)
                PERMISSIONS.put(owner,g = new Group(owner));
            g.add(this);
        }
        ALL.add(this);
    }

    private Permission(Class owner, String name) {
        this(owner,name,null);
    }

    /**
     * Returns the string representation of this {@link Permission},
     * which can be converted back to {@link Permission} via the
     * {@link #fromId(String)} method.
     *
     * <p>
     * This string representation is suitable for persistence.
     *
     * @see #fromId(String)
     */
    public String getId() {
        return owner.getName()+'.'+name;
    }

    /**
     * Convert the ID representation into {@link Permission} object.
     *
     * @return
     *      null if the conversion failed.
     * @see #getId()
     */
    public static Permission fromId(String id) {
        int idx = id.lastIndexOf('.');
        if(idx<0)   return null;

        try {
            // force the initialization so that it will put all its permissions into the list.
            Class cl = Class.forName(id.substring(0,idx),true,Hudson.getInstance().getPluginManager().uberClassLoader);
            Group g = PERMISSIONS.get(cl);
            if(g ==null)  return null;
            return g.find(id.substring(idx+1));
        } catch (ClassNotFoundException e) {
            return null;
        }
    }

    public String toString() {
        return "Permission["+owner+','+name+']';
    }

    /**
     * Returns all the {@link Permission}s available in the system.
     * @return
     *      always non-null. Read-only.
     */
    public static List<Permission> getAll() {
        return ALL_VIEW;
    }

    /**
     * Returns all the {@link Group}s available in the system.
     * @return
     *      always non-null. Read-only.
     */
    public static List<Group> getAllGroups() {
        return ALL_GROUPS;
    }

    /**
     * All the permissions in the system, keyed by their owners.
     */
    private static final Map<Class,Group> PERMISSIONS = new ConcurrentHashMap<Class,Group>();

    /**
     * The same as {@link #PERMISSIONS} but in a single list.
     */
    private static final List<Permission> ALL = new CopyOnWriteArrayList<Permission>();

    private static final List<Permission> ALL_VIEW = Collections.unmodifiableList(ALL);

    /**
     * All groups. Sorted.
     */
    @CopyOnWrite
    private static List<Group> ALL_GROUPS = Collections.emptyList();

    /**
     * Group of {@link Permission}s that share the same {@link Permission#owner owner}.
     *
     * Sortable by the owner class name.
     */
    public static final class Group implements Iterable<Permission>, Comparable<Group> {
        private final List<Permission> permisisons = new CopyOnWriteArrayList<Permission>();
        private final List<Permission> permisisonsView = Collections.unmodifiableList(permisisons);
        public final Class owner;

        protected Group(Class owner) {
            this.owner = owner;

            synchronized(Group.class) {
                List<Group> allGroups = new ArrayList<Group>(ALL_GROUPS);
                allGroups.add(this);
                Collections.sort(allGroups);
                ALL_GROUPS = Collections.unmodifiableList(allGroups);
            }
        }

        public Iterator<Permission> iterator() {
            return permisisons.iterator();
        }

        protected void add(Permission p) {
            permisisons.add(p);
        }

        /**
         * Lists up all the permissions in this group.
         */
        public List<Permission> getPermissions() {
            return permisisonsView;
        }

        /**
         * Finds a permission that has the given name.
         */
        public Permission find(String name) {
            for (Permission p : permisisons) {
                if(p.name.equals(name))
                    return p;
            }
            return null;
        }

        public int compareTo(Group that) {
            return this.owner.getName().compareTo(that.owner.getName());
        }
    }

//
//
// Root Permissions.
//
// These permisisons are meant to be used as the 'impliedBy' permission for other more specific permissions.
// The intention is to allow a simplified AuthorizationStrategy implementation agnostic to
// specific permissions.

    /**
     * Root of all permissions
     */
    public static final Permission FULL_CONTROL = new Permission(Permission.class,"FullControl");

    /**
     * Generic read access.
     */
    public static final Permission READ = new Permission(Permission.class,"GenericRead",FULL_CONTROL);

    /**
     * Generic write access.
     */
    public static final Permission WRITE = new Permission(Permission.class,"GenericWrite",FULL_CONTROL);

    /**
     * Generic create access.
     */
    public static final Permission CREATE = new Permission(Permission.class,"GenericCreate",WRITE);

    /**
     * Generic update access.
     */
    public static final Permission UPDATE = new Permission(Permission.class,"GenericUpdate",WRITE);

    /**
     * Generic delete access.
     */
    public static final Permission DELETE = new Permission(Permission.class,"GenericDelete",WRITE);

    /**
     * Generic configuration access.
     */
    public static final Permission CONFIGURE = new Permission(Permission.class,"GenericConfigure",UPDATE);
}
