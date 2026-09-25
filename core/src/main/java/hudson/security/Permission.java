/*
 * The MIT License
 *
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi, Yahoo! Inc.
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

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.model.Hudson;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import jenkins.model.Jenkins;
import net.sf.json.util.JSONUtils;
import org.jvnet.localizer.Localizable;

/**
 * Permission, which represents activity that requires a security privilege.
 *
 * <p>
 * Each permission is represented by a specific instance of {@link Permission}.
 *
 * @author Kohsuke Kawaguchi
 * @see <a href="https://www.jenkins.io/doc/developer/security/">Security</a>
 */
public final class Permission {

    /**
     * Comparator that orders {@link Permission} objects based on their ID.
     */
    public static final Comparator<Permission> ID_COMPARATOR = Comparator.comparing(Permission::getId);

    public final @NonNull Class owner;

    public final @NonNull PermissionGroup group;

    // if some plugin serialized old version of this class using XStream, `id` can be null
    private final @CheckForNull String id;

    /**
     * Human readable ID of the permission.
     *
     * <p>
     * This name should uniquely determine a permission among
     * its owner class. The name must be a valid Java identifier.
     * <p>
     * The expected naming convention is something like "BrowseWorkspace".
     */
    public final @NonNull String name;

    /**
     * Human-readable description of this permission.
     * Used as a tooltip to explain this permission, so this message
     * should be a couple of sentences long.
     *
     * <p>
     * If null, there will be no description text.
     */
    public final @CheckForNull Localizable description;

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
    public final @CheckForNull Permission impliedBy;

    /**
     * Whether this permission is available for use.
     *
     * <p>
     * This allows us to dynamically enable or disable the visibility of
     * permissions, so administrators can control the complexity of their
     * permission matrix.
     *
     * @since 1.325
     */
    @SuppressFBWarnings(value = "PA_PUBLIC_PRIMITIVE_ATTRIBUTE", justification = "Preserve API compatibility")
    public boolean enabled;

    /**
     * Scopes that this permission is directly contained by.
     */
    private final @NonNull Set<PermissionScope> scopes;

    /**
     * Defines a new permission.
     *
     * @param group
     *      Permissions are grouped per classes that own them. Specify the permission group
     *      created for that class. The idiom is:
     *
     * <pre>
     * class Foo {
     *     private static final PermissionGroup PERMISSIONS = new PermissionGroup(Foo.class,...);
     *     public static final Permission ABC = new Permission(PERMISSION,...) ;
     * }
     * </pre>
     *
     *      Because of the classloading problems and the difficulty for Hudson to enumerate them,
     *      the permission constants really need to be static field of the owner class.
     *
     * @param name
     *      See {@link #name}.
     * @param description
     *      See {@link #description}.
     * @param impliedBy
     *      See {@link #impliedBy}.
     * @throws IllegalStateException if this permission was already defined
     */
    public Permission(@NonNull PermissionGroup group, @NonNull String name,
            @CheckForNull Localizable description, @CheckForNull Permission impliedBy, boolean enable,
            @NonNull PermissionScope[] scopes) throws IllegalStateException {
        if (!JSONUtils.isJavaIdentifier(name))
            throw new IllegalArgumentException(name + " is not a Java identifier");
        this.owner = group.owner;
        this.group = group;
        this.name = name;
        this.description = description;
        this.impliedBy = impliedBy;
        this.enabled = enable;
        this.scopes = Set.of(scopes);
        this.id = owner.getName() + '.' + name;

        group.add(this);
        ALL.add(this);
    }

    public Permission(@NonNull PermissionGroup group, @NonNull String name,
            @CheckForNull Localizable description, @CheckForNull Permission impliedBy, @NonNull PermissionScope scope) {
        this(group, name, description, impliedBy, true, new PermissionScope[]{scope});
        assert scope != null;
    }

    /**
     * @deprecated as of 1.421
     *      Use {@link #Permission(PermissionGroup, String, Localizable, Permission, boolean, PermissionScope[])}
     */
    @Deprecated
    public Permission(@NonNull PermissionGroup group, @NonNull String name, @CheckForNull Localizable description, @CheckForNull Permission impliedBy, boolean enable) {
        this(group, name, description, impliedBy, enable, new PermissionScope[]{PermissionScope.JENKINS});
    }

    /**
     * @deprecated as of 1.421
     *      Use {@link #Permission(PermissionGroup, String, Localizable, Permission, PermissionScope)}
     */
    @Deprecated
    public Permission(@NonNull PermissionGroup group, @NonNull String name, @CheckForNull Localizable description, @CheckForNull Permission impliedBy) {
        this(group, name, description, impliedBy, PermissionScope.JENKINS);
    }

    /**
     * @deprecated since 1.257.
     *      Use {@link #Permission(PermissionGroup, String, Localizable, Permission)}
     */
    @Deprecated
    public Permission(@NonNull PermissionGroup group, @NonNull String name, @CheckForNull Permission impliedBy) {
        this(group, name, null, impliedBy);
    }

    private Permission(@NonNull PermissionGroup group, @NonNull String name) {
        this(group, name, null, null);
    }

    /**
     * Checks if this permission is contained in the specified scope, (either directly or indirectly.)
     */
    public boolean isContainedBy(@NonNull PermissionScope s) {
        for (PermissionScope c : scopes) {
            if (c.isContainedBy(s))
                return true;
        }
        return false;
    }

    /**
     * Returns the string representation of this {@link Permission},
     * which can be converted back to {@link Permission} via the
     * {@link #fromId(String)} method.
     *
     * <p>
     * This string representation is suitable for persistence.
     * @return ID with the following format: <i>permissionClass.permissionName</i>
     * @see #fromId(String)
     */
    public @NonNull String getId() {
        if (id == null) {
            return owner.getName() + '.' + name;
        }
        return id;
    }

    @Override public boolean equals(Object o) {
        return o instanceof Permission && getId().equals(((Permission) o).getId());
    }

    @Override public int hashCode() {
        return getId().hashCode();
    }

    /**
     * Convert the ID representation into {@link Permission} object.
     *
     * @return
     *      null if the conversion failed.
     * @see #getId()
     */
    public static @CheckForNull Permission fromId(@NonNull String id) {
        int idx = id.lastIndexOf('.');
        if (idx < 0)   return null;

        try {
            // force the initialization so that it will put all its permissions into the list.
            Class cl = Class.forName(id.substring(0, idx), true, Jenkins.get().getPluginManager().uberClassLoader);
            PermissionGroup g = PermissionGroup.get(cl);
            if (g == null)  return null;
            return g.find(id.substring(idx + 1));
        } catch (ClassNotFoundException e) {
            return null;
        }
    }

    @Override
    public String toString() {
        return "Permission[" + owner + ',' + name + ']';
    }

    public void setEnabled(boolean enable) {
        enabled = enable;
    }

    public boolean getEnabled() {
        return enabled;
    }

    /**
     * Returns all the {@link Permission}s available in the system.
     * @return
     *      always non-null. Read-only.
     */
    public static @NonNull List<Permission> getAll() {
        return ALL_VIEW;
    }

    /**
     * All permissions in the system but in a single list.
     */
    private static final List<Permission> ALL = new CopyOnWriteArrayList<>();

    private static final List<Permission> ALL_VIEW = Collections.unmodifiableList(ALL);

//
//
// Because of the initialization order issue, these two fields need to be defined here,
// even though they logically belong to Jenkins.
//

    /**
     * {@link PermissionGroup} for {@link jenkins.model.Jenkins}.
     *
     * @deprecated since 2009-01-23.
     *      Access {@link jenkins.model.Jenkins#PERMISSIONS} instead.
     */
    @Deprecated
    public static final PermissionGroup HUDSON_PERMISSIONS = new PermissionGroup(Hudson.class, hudson.model.Messages._Hudson_Permissions_Title());
    /**
     * {@link Permission} that represents the God-like access. Equivalent of Unix root.
     *
     * <p>
     * All permissions are eventually {@linkplain Permission#impliedBy implied by} this permission.
     *
     * @deprecated since 2009-01-23.
     *      Access {@link jenkins.model.Jenkins#ADMINISTER} instead.
     */
    @Deprecated
    public static final Permission HUDSON_ADMINISTER = new Permission(HUDSON_PERMISSIONS, "Administer", hudson.model.Messages._Hudson_AdministerPermission_Description(), null);

//
//
// Root Permissions.
//
// These permissions are meant to be used as the 'impliedBy' permission for other more specific permissions.
// The intention is to allow a simplified AuthorizationStrategy implementation agnostic to
// specific permissions.

    public static final PermissionGroup GROUP = new PermissionGroup(Permission.class, Messages._Permission_Permissions_Title());

    /**
     * Historically this was separate from {@link #HUDSON_ADMINISTER} but such a distinction doesn't make sense
     * any more, so deprecated.
     *
     * @deprecated since 2009-01-23.
     *      Use {@link jenkins.model.Jenkins#ADMINISTER}.
     */
    @Deprecated
    public static final Permission FULL_CONTROL = new Permission(GROUP, "FullControl", null, HUDSON_ADMINISTER);

    /**
     * Generic read access.
     */
    public static final Permission READ = new Permission(GROUP, "GenericRead", null, HUDSON_ADMINISTER);

    /**
     * Generic write access.
     */
    public static final Permission WRITE = new Permission(GROUP, "GenericWrite", null, HUDSON_ADMINISTER);

    /**
     * Generic create access.
     */
    public static final Permission CREATE = new Permission(GROUP, "GenericCreate", null, WRITE);

    /**
     * Generic update access.
     */
    public static final Permission UPDATE = new Permission(GROUP, "GenericUpdate", null, WRITE);

    /**
     * Generic delete access.
     */
    public static final Permission DELETE = new Permission(GROUP, "GenericDelete", null, WRITE);

    /**
     * Generic configuration access.
     */
    public static final Permission CONFIGURE = new Permission(GROUP, "GenericConfigure", null, UPDATE);
}
