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

import hudson.CopyOnWrite;
import hudson.model.Hudson;

import java.util.List;
import java.util.Collections;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ConcurrentHashMap;

import org.jvnet.localizer.Localizable;

/**
 * Group of {@link Permission}s that share the same {@link Permission#owner owner}.
 *
 * Sortable by the owner class name.
 */
public final class PermissionGroup implements Iterable<Permission>, Comparable<PermissionGroup> {
    private final List<Permission> permisisons = new CopyOnWriteArrayList<Permission>();
    private final List<Permission> permisisonsView = Collections.unmodifiableList(permisisons);
    public final Class owner;

    /**
     * Human readable title of this permission group.
     * This should be short.
     */
    public final Localizable title;

    public PermissionGroup(Class owner, Localizable title) {
        this.owner = owner;
        this.title = title;

        synchronized(PermissionGroup.class) {
            List<PermissionGroup> allGroups = new ArrayList<PermissionGroup>(ALL);
            allGroups.add(this);
            Collections.sort(allGroups);
            ALL = Collections.unmodifiableList(allGroups);
        }

        PERMISSIONS.put(owner,this);
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
        // first, sort by the 'compare order' number. This is so that
        // we can put Hudson.PERMISSIONS first.
        int r= this.compareOrder()-that.compareOrder();
        if(r!=0)    return r;

        // among the permissions of the same group, just sort by their names
        // so that the sort order is consistent regardless of classloading order.
        return this.owner.getName().compareTo(that.owner.getName());
    }

    private int compareOrder() {
        if(owner== Hudson.class)    return 0;
        return 1;
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

    /**
     * Gets the {@link PermissionGroup} whose {@link PermissionGroup#owner} is the given class.
     *
     * @return  null if not found.
     */
    public static PermissionGroup get(Class owner) {
        return PERMISSIONS.get(owner);
    }

    /**
     * All the permissions in the system, keyed by their owners.
     */
    private static final Map<Class, PermissionGroup> PERMISSIONS = new ConcurrentHashMap<Class, PermissionGroup>();
}
