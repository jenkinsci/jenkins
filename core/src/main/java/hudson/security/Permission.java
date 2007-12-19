package hudson.security;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;

/**
 * Permission, which represents activity that requires a security privilege.
 *
 * <p>
 * Each permission is represented by a specific instance of {@link Permission}.
 *
 * @author Kohsuke Kawaguchi
 */
public class Permission {
    public final Class owner;

    /**
     * Human readable name of the permission.
     *
     * <p>
     * This name should allow humans to uniquely identify a permission.
     * The expected naming convention is something like "Browse Workspace".
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
        this.owner = owner;
        this.name = name;
        this.impliedBy = impliedBy;

        synchronized (PERMISSIONS) {
            List<Permission> ps = PERMISSIONS.get(owner);
            if(ps==null) {
                ps = new ArrayList<Permission>();
                PERMISSIONS.put(owner,ps);
            }
            ps.add(this);
        }
        ALL.add(this);
    }

    private Permission(Class owner, String name) {
        this(owner,name,null);
    }

    public String toString() {
        return "Permission["+owner+','+name+']';
    }

    public static List<Permission> getAll() {
        return ALL;
    }

    /**
     * All the permissions in the system, keyed by their owners.
     */
    private static final Map<Class,List<Permission>> PERMISSIONS = new HashMap<Class,List<Permission>>();

    /**
     * The same as {@link #PERMISSIONS} but in a single list.
     */
    private static final List<Permission> ALL = new ArrayList<Permission>();

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
    public static final Permission FULL_CONTROL = new Permission(Permission.class,"Full Control");

    /**
     * Generic read access.
     */
    public static final Permission READ = new Permission(Permission.class,"Generic Read",FULL_CONTROL);

    /**
     * Generic write access.
     */
    public static final Permission WRITE = new Permission(Permission.class,"Generic Write",FULL_CONTROL);

    /**
     * Generic create access.
     */
    public static final Permission CREATE = new Permission(Permission.class,"Generic Create",WRITE);

    /**
     * Generic update access.
     */
    public static final Permission UPDATE = new Permission(Permission.class,"Generic Update",WRITE);

    /**
     * Generic delete access.
     */
    public static final Permission DELETE = new Permission(Permission.class,"Generic Delete",WRITE);

    /**
     * Generic configuration access.
     */
    public static final Permission CONFIGURE = new Permission(Permission.class,"Generic Configure",UPDATE);
}
