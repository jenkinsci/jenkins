/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi
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
package hudson.security;

import hudson.model.Hudson;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;
import javax.annotation.CheckForNull;
import org.jvnet.localizer.Localizable;

/**
 * Group of {@link Permission}s that share the same {@link Permission#owner owner}.
 *
 * Sortable by the owner class name.
 */
public final class PermissionGroup implements Iterable<Permission>, Comparable<PermissionGroup> {
    private final SortedSet<Permission> permissions = new TreeSet<Permission>(Permission.ID_COMPARATOR);
    public final Class owner;

    /**
     * Human readable title of this permission group.
     * This should be short.
     */
    public final Localizable title;

    /**
     * Both creates a registers a new permission group.
     * @param owner sets {@link #owner}
     * @param title sets {@link #title}
     * @throws IllegalStateException if this group was already registered
     */
    public PermissionGroup(Class owner, Localizable title) throws IllegalStateException {
        this.owner = owner;
        this.title = title;
        register(this);
    }

    private String id() {
        return owner.getName();
    }

    public Iterator<Permission> iterator() {
        return getPermissions().iterator();
    }

    /*package*/ synchronized void add(Permission p) {
        if (!permissions.add(p)) {
            throw new IllegalStateException("attempt to register a second Permission for " + p.getId());
        }
    }

    /**
     * Lists up all the permissions in this group.
     */
    public synchronized List<Permission> getPermissions() {
        return new ArrayList<Permission>(permissions);
    }

    public synchronized boolean hasPermissionContainedBy(PermissionScope scope) {
        for (Permission p : permissions)
            if (p.isContainedBy(scope))
                return true;
        return false;
    }

    /**
     * Finds a permission that has the given name.
     */
    public synchronized Permission find(String name) {
        for (Permission p : permissions) {
            if(p.name.equals(name))
                return p;
        }
        return null;
    }

    public int compareTo(PermissionGroup that) {
        // first, sort by the 'compare order' number. This is so that
        // we can put Hudson.PERMISSIONS first.
        int r= this.compareOrder()-that.compareOrder();
        if(r!=0)    return r;

        // among the permissions of the same group, just sort by their names
        // so that the sort order is consistent regardless of classloading order.
        return id().compareTo(that.id());
    }

    private int compareOrder() {
        if(owner==Hudson.class)    return 0;
        return 1;
    }

    @Override public boolean equals(Object o) {
        return o instanceof PermissionGroup && id().equals(((PermissionGroup) o).id());
    }

    @Override public int hashCode() {
        return id().hashCode();
    }

    public synchronized int size() {
        return permissions.size();
    }

    @Override public String toString() {
        return "PermissionGroup[" + id() + "]";
    }

    private static synchronized void register(PermissionGroup g) {
        if (!PERMISSIONS.add(g)) {
            throw new IllegalStateException("attempt to register a second PermissionGroup for " + g.id());
        }
    }

    /**
     * Returns all the {@link PermissionGroup}s available in the system.
     * @return
     *      always non-null. Read-only.
     */
    public static synchronized List<PermissionGroup> getAll() {
        return new ArrayList<PermissionGroup>(PERMISSIONS);
    }

    /**
     * Gets the {@link PermissionGroup} whose {@link PermissionGroup#owner} is the given class.
     *
     * @return  null if not found.
     */
    public static synchronized @CheckForNull PermissionGroup get(Class owner) {
        for (PermissionGroup g : PERMISSIONS) {
            if (g.owner == owner) {
                return g;
            }
        }
        return null;
    }

    /**
     * All the permissions in the system, keyed by their owners.
     */
    private static final SortedSet<PermissionGroup> PERMISSIONS = new TreeSet<PermissionGroup>();
}
