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
import org.jvnet.localizer.Localizable;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.stream.Collectors;

/**
 * Group of {@link Permission}s that share the same {@link Permission#owner owner}.
 *
 * Sortable by the owner class name.
 * @see DeclarePermissionGroup
 */
public final class PermissionGroup implements Iterable<Permission>, Comparable<PermissionGroup> {
    private final SortedSet<Permission> permissions = new ConcurrentSkipListSet<>();

    @Nonnull
    public final Class owner;

    /**
     * Human readable title of this permission group.
     * This should be short.
     */
    public final Localizable title;

    private final String id;

    /**
     * Both creates a registers a new permission group.
     * @param owner sets {@link #owner}
     * @param title sets {@link #title}
     * @throws IllegalStateException if this group was already registered
     */
    public PermissionGroup(@Nonnull Class owner, Localizable title) throws IllegalStateException {
        this(title.toString(Locale.ENGLISH), owner, title);
    }

    /**
     * Both creates a registers a new permission group.
     * @param owner sets {@link #owner}
     * @param title sets {@link #title}
     * @throws IllegalStateException if this group was already registered
     * @since 2.127
     */
    public PermissionGroup(String id, @Nonnull Class owner, Localizable title) throws IllegalStateException {
        this.owner = owner;
        this.title = title;
        this.id = id;
    }

    /**
     * Gets ID of the permission group.
     * @return Non-localizable ID of the permission group.
     */
    public String getId() {
        return id;
    }

    public String getOwnerClassName() {
        return owner.getName();
    }

    public Iterator<Permission> iterator() {
        return getPermissions().iterator();
    }

    /*package*/ void add(Permission p) {
        if (!permissions.add(p)) {
            throw new IllegalStateException("attempt to register a second Permission for " + p.getId());
        }
    }

    /**
     * Lists up all the permissions in this group.
     */
    public List<Permission> getPermissions() {
        return new ArrayList<>(permissions);
    }

    public boolean hasPermissionContainedBy(PermissionScope scope) {
        return permissions.stream().anyMatch(p -> p.isContainedBy(scope));
    }

    /**
     * Finds a permission that has the given name.
     */
    public Permission find(String name) {
        return permissions.stream().filter(p -> p.name.equals(name)).findFirst().orElse(null);
    }

    @Override
    public int compareTo(PermissionGroup that) {
        // first, sort by the 'compare order' number. This is so that
        // we can put Hudson.PERMISSIONS first.
        int r= this.compareOrder()-that.compareOrder();
        if(r!=0)    return r;

        // among the permissions of the same group, just sort by their names
        // so that the sort order is consistent regardless of classloading order.
        return getOwnerClassName().compareTo(that.getOwnerClassName());
    }

    private int compareOrder() {
        if(owner==Hudson.class)    return 0;
        return 1;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof PermissionGroup && getOwnerClassName().equals(((PermissionGroup) o).getOwnerClassName());
    }

    @Override
    public int hashCode() {
        return getOwnerClassName().hashCode();
    }

    public int size() {
        return permissions.size();
    }

    @Override
    public String toString() {
        return "PermissionGroup[" + getOwnerClassName() + "]";
    }

    private Object writeReplace() {
        return new Proxy(permissions, owner, title, id);
    }

    private static class Proxy {
        private final SortedSet<Permission> permissions;
        private final Class<?> owner;
        private final Localizable title;
        private final String id;

        private Proxy(SortedSet<Permission> permissions, Class<?> owner, Localizable title, String id) {
            this.permissions = new TreeSet<>(permissions);
            this.owner = owner;
            this.title = title;
            this.id = id;
        }

        private Object readResolve() {
            PermissionGroup group = new PermissionGroup(id, owner, title);
            group.permissions.addAll(permissions);
            return group;
        }
    }

    private static final PermissionLoader<DeclarePermissionGroup, PermissionGroup> LOADER =
            new PermissionLoader<>(DeclarePermissionGroup.class, PermissionGroup.class);

    /**
     * Returns all the {@link PermissionGroup}s available in the system.
     * @return
     *      always non-null. Read-only.
     */
    public static List<PermissionGroup> getAll() {
        return LOADER.all().sorted().collect(Collectors.toList());
    }

    /**
     * Gets the {@link PermissionGroup} whose {@link PermissionGroup#owner} is the given class.
     *
     * @return  null if not found.
     */
    public static synchronized @CheckForNull PermissionGroup get(Class owner) {
        return LOADER.all().filter(group -> group.owner.equals(owner)).findFirst().orElse(null);
    }

}
