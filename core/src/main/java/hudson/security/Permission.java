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

import com.google.common.collect.ImmutableSet;
import hudson.model.Hudson;
import jenkins.util.SystemProperties;
import net.sf.json.util.JSONUtils;
import org.jvnet.localizer.Localizable;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Permission, which represents activity that requires a security privilege.
 *
 * <p>
 * Each permission is represented by a specific instance of {@link Permission}.
 *
 * @author Kohsuke Kawaguchi
 * @see <a href="https://wiki.jenkins-ci.org/display/JENKINS/Making+your+plugin+behave+in+secured+Jenkins">Plugins in secured Jenkins</a>
 */
public final class Permission implements Comparable<Permission> {

    public final @Nonnull Class owner;

    public final @Nonnull PermissionGroup group;

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
    public final @Nonnull String name;

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
    public boolean enabled;

    /**
     * Scopes that this permission is directly contained by.
     */
    private final @Nonnull Set<PermissionScope> scopes;

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
    public Permission(@Nonnull PermissionGroup group, @Nonnull String name, 
            @CheckForNull Localizable description, @CheckForNull Permission impliedBy, boolean enable, 
            @Nonnull PermissionScope[] scopes) throws IllegalStateException {
        if(!JSONUtils.isJavaIdentifier(name))
            throw new IllegalArgumentException(name+" is not a Java identifier");
        this.owner = group.owner;
        this.group = group;
        this.name = name;
        this.description = description;
        this.impliedBy = impliedBy;
        this.enabled = enable;
        this.scopes = ImmutableSet.copyOf(scopes);
        this.id = owner.getName() + '.' + name;

        group.add(this);
    }

    public Permission(@Nonnull PermissionGroup group, @Nonnull String name, 
            @CheckForNull Localizable description, @CheckForNull Permission impliedBy, @Nonnull PermissionScope scope) {
        this(group,name,description,impliedBy,true,new PermissionScope[]{scope});
        assert scope!=null;
    }

    /**
     * @deprecated as of 1.421
     *      Use {@link #Permission(PermissionGroup, String, Localizable, Permission, boolean, PermissionScope[])}
     */
    @Deprecated
    public Permission(@Nonnull PermissionGroup group, @Nonnull String name, @CheckForNull Localizable description, @CheckForNull Permission impliedBy, boolean enable) {
        this(group,name,description,impliedBy,enable,new PermissionScope[]{PermissionScope.JENKINS});
    }

    /**
     * @deprecated as of 1.421
     *      Use {@link #Permission(PermissionGroup, String, Localizable, Permission, PermissionScope)}
     */
    @Deprecated
    public Permission(@Nonnull PermissionGroup group, @Nonnull String name, @CheckForNull Localizable description, @CheckForNull Permission impliedBy) {
        this(group, name, description, impliedBy, PermissionScope.JENKINS);
    }

    /**
     * @deprecated since 1.257.
     *      Use {@link #Permission(PermissionGroup, String, Localizable, Permission)}
     */
    @Deprecated
    public Permission(@Nonnull PermissionGroup group, @Nonnull String name, @CheckForNull Permission impliedBy) {
        this(group,name,null,impliedBy);
    }

    /**
     * Checks if this permission is contained in the specified scope, (either directly or indirectly.)
     */
    public boolean isContainedBy(@Nonnull PermissionScope s) {
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
    public @Nonnull String getId() {
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

    @Override
    public String toString() {
        return "Permission["+owner+','+name+']';
    }

    public void setEnabled(boolean enable) {
        enabled = enable;
    }

    public boolean getEnabled() {
        return enabled;
    }

    @Override public int compareTo(@Nonnull Permission o) {
        return getId().compareTo(o.getId());
    }

    private static final PermissionLoader<GlobalPermission, Permission> LOADER =
            new PermissionLoader<>(GlobalPermission.class, Permission.class);

    /**
     * Comparator that orders {@link Permission} objects based on their ID.
     * @deprecated use the natural ordering of Permissions or use {@link Comparator#naturalOrder()}
     */
    @Deprecated
    public static final Comparator<Permission> ID_COMPARATOR = Comparator.naturalOrder();

    /**
     * Convert the ID representation into {@link Permission} object.
     *
     * @return
     *      null if the conversion failed.
     * @see #getId()
     */
    public static @CheckForNull Permission fromId(@Nonnull String id) {
        return LOADER.all().filter(permission -> permission.getId().equals(id)).findFirst().orElse(null);
    }

    /**
     * Returns all the {@link Permission}s available in the system.
     * @return
     *      always non-null. Read-only.
     */
    public static @Nonnull List<Permission> getAll() {
        return LOADER.all().sorted().collect(Collectors.toList());
    }

    /**
     * System property to enable or disable the {@code ADMINISTER} implies {@code RUN_SCRIPTS} feature flag.
     * By default, this is enabled to match legacy behavior.
     * @since TODO
     */
    @Restricted(NoExternalUse.class)
    public static final String ADMINISTER_IMPLIES_RUN_SCRIPTS = Permission.class.getName() + ".administerImpliesRunScripts";

    /**
     * Indicates whether or not the {@code ADMINISTER} permission implies the {@code RUN_SCRIPTS} permission.
     * @return true when ADMINISTER implies RUN_SCRIPTS, false when RUN_SCRIPTS implies ADMINISTER
     */
    private static boolean administerImpliesRunScripts() {
        return SystemProperties.getBoolean(ADMINISTER_IMPLIES_RUN_SCRIPTS, true);
    }

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
     * Logical root level permission to execute arbitrary scripts in the script console.
     * When {@link #ADMINISTER_IMPLIES_RUN_SCRIPTS} is enabled, all permissions are implied by this.
     * When {@link #ADMINISTER_IMPLIES_RUN_SCRIPTS} is disabled, this permission is instead implied by
     * {@link jenkins.model.Jenkins#ADMINISTER}.
     * Access this instance via {@link jenkins.model.Jenkins#RUN_SCRIPTS} instead.
     */
    @Restricted(NoExternalUse.class)
    public static final Permission RUN_SCRIPTS;

    /**
     * Legacy root level permission to access all parts of Jenkins. Unless {@link #ADMINISTER_IMPLIES_RUN_SCRIPTS} is
     * enabled, all permissions are implied by this one. When {@link #ADMINISTER_IMPLIES_RUN_SCRIPTS} is enabled,
     * all permissions besides {@link #RUN_SCRIPTS}, {@link hudson.PluginManager#CONFIGURE_UPDATECENTER}, and
     * {@link hudson.PluginManager#UPLOAD_PLUGINS} are implied by this.
     *
     * @deprecated since 2009-01-23.
     *      Access {@link jenkins.model.Jenkins#ADMINISTER} instead.
     */
    @Deprecated
    public static final Permission HUDSON_ADMINISTER;

    /**
     * Root level permission of the permission hierarchy. This refers to whichever permission is the logical
     * root permission regardless of the state of the {@link #ADMINISTER_IMPLIES_RUN_SCRIPTS} feature flag.
     * Access this instance via {@link jenkins.model.Jenkins#ROOT}.
     */
    @Restricted(NoExternalUse.class)
    public static final Permission ROOT;

    static {
        if (administerImpliesRunScripts()) {
            ROOT = HUDSON_ADMINISTER  = new Permission(HUDSON_PERMISSIONS, "Administer", hudson.model.Messages._Hudson_AdministerPermission_Description(), null, PermissionScope.JENKINS);
            RUN_SCRIPTS = new Permission(HUDSON_PERMISSIONS, "RunScripts", hudson.model.Messages._Hudson_RunScriptsPermission_Description(), ROOT, PermissionScope.JENKINS);
        } else {
            ROOT = RUN_SCRIPTS = new Permission(HUDSON_PERMISSIONS, "RunScripts", hudson.model.Messages._Hudson_RunScriptsPermission_Description(), null, PermissionScope.JENKINS);
            HUDSON_ADMINISTER = new Permission(HUDSON_PERMISSIONS, "Administer", hudson.model.Messages._Hudson_AdministerPermission_Description(), ROOT, PermissionScope.JENKINS);
        }
    }

//
//
// Root Permissions.
//
// These permissions are meant to be used as the 'impliedBy' permission for other more specific permissions.
// The intention is to allow a simplified AuthorizationStrategy implementation agnostic to
// specific permissions.

    public static final PermissionGroup GROUP = new PermissionGroup(Permission.class,Messages._Permission_Permissions_Title());

    /**
     * Historically this was separate from {@link #HUDSON_ADMINISTER} but such a distinction doesn't make sense
     * any more, so deprecated.
     *
     * @deprecated since 2009-01-23.
     *      Use {@link jenkins.model.Jenkins#ROOT}.
     */
    @Deprecated
    public static final Permission FULL_CONTROL = new Permission(GROUP, "FullControl",null, ROOT);

    /**
     * Generic read access.
     */
    @GlobalPermission
    public static final Permission READ = new Permission(GROUP,"GenericRead",null,ROOT);

    /**
     * Generic write access.
     */
    @GlobalPermission
    public static final Permission WRITE = new Permission(GROUP,"GenericWrite",null,ROOT);

    /**
     * Generic create access.
     */
    @GlobalPermission
    public static final Permission CREATE = new Permission(GROUP,"GenericCreate",null,WRITE);

    /**
     * Generic update access.
     */
    @GlobalPermission
    public static final Permission UPDATE = new Permission(GROUP,"GenericUpdate",null,WRITE);

    /**
     * Generic delete access.
     */
    @GlobalPermission
    public static final Permission DELETE = new Permission(GROUP,"GenericDelete",null,WRITE);

    /**
     * Generic configuration access.
     */
    @GlobalPermission
    public static final Permission CONFIGURE = new Permission(GROUP,"GenericConfigure",null,UPDATE);
}
