package hudson.security;

import hudson.model.Hudson;
import net.sf.json.util.JSONUtils;

import java.util.Collections;
import java.util.List;
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

    public final PermissionGroup group;

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

    public Permission(PermissionGroup group, String name, Permission impliedBy) {
        if(!JSONUtils.isJavaIdentifier(name))
            throw new IllegalArgumentException(name+" is not a Java identifier");
        this.owner = group.owner;
        this.group = group;
        this.name = name;
        this.impliedBy = impliedBy;

        group.add(this);
        ALL.add(this);
    }

    private Permission(PermissionGroup group, String name) {
        this(group,name,null);
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
            PermissionGroup g = PermissionGroup.get(cl);
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
     * All permissions in the system but in a single list.
     */
    private static final List<Permission> ALL = new CopyOnWriteArrayList<Permission>();

    private static final List<Permission> ALL_VIEW = Collections.unmodifiableList(ALL);

//
//
// Root Permissions.
//
// These permisisons are meant to be used as the 'impliedBy' permission for other more specific permissions.
// The intention is to allow a simplified AuthorizationStrategy implementation agnostic to
// specific permissions.

    public static final PermissionGroup GROUP = new PermissionGroup(Permission.class,Messages._Permission_Permissions_Title());

    /**
     * Root of all permissions
     */
    public static final Permission FULL_CONTROL = new Permission(GROUP,"FullControl");

    /**
     * Generic read access.
     */
    public static final Permission READ = new Permission(GROUP,"GenericRead",FULL_CONTROL);

    /**
     * Generic write access.
     */
    public static final Permission WRITE = new Permission(GROUP,"GenericWrite",FULL_CONTROL);

    /**
     * Generic create access.
     */
    public static final Permission CREATE = new Permission(GROUP,"GenericCreate",WRITE);

    /**
     * Generic update access.
     */
    public static final Permission UPDATE = new Permission(GROUP,"GenericUpdate",WRITE);

    /**
     * Generic delete access.
     */
    public static final Permission DELETE = new Permission(GROUP,"GenericDelete",WRITE);

    /**
     * Generic configuration access.
     */
    public static final Permission CONFIGURE = new Permission(GROUP,"GenericConfigure",UPDATE);
}
