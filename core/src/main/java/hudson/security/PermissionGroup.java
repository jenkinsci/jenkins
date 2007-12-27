package hudson.security;

import hudson.CopyOnWrite;

import java.util.List;
import java.util.Collections;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Group of {@link Permission}s that share the same {@link Permission#owner owner}.
 *
 * Sortable by the owner class name.
 */
public final class PermissionGroup implements Iterable<Permission>, Comparable<PermissionGroup> {
    private final List<Permission> permisisons = new CopyOnWriteArrayList<Permission>();
    private final List<Permission> permisisonsView = Collections.unmodifiableList(permisisons);
    public final Class owner;

    public PermissionGroup(Class owner) {
        this.owner = owner;

        synchronized(PermissionGroup.class) {
            List<PermissionGroup> allGroups = new ArrayList<PermissionGroup>(ALL);
            allGroups.add(this);
            Collections.sort(allGroups);
            ALL = Collections.unmodifiableList(allGroups);
        }
    }

    public Iterator<Permission> iterator() {
        return permisisons.iterator();
    }

    /*package*/ void add(Permission p) {
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

    public int compareTo(PermissionGroup that) {
        return this.owner.getName().compareTo(that.owner.getName());
    }

    public int size() {
        return permisisons.size();
    }

    /**
     * All groups. Sorted.
     */
    @CopyOnWrite
    private static List<PermissionGroup> ALL = Collections.emptyList();

    /**
     * Returns all the {@link PermissionGroup}s available in the system.
     * @return
     *      always non-null. Read-only.
     */
    public static List<PermissionGroup> getAll() {
        return ALL;
    }
}
