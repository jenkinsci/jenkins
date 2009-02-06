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

import hudson.model.*;
import net.sf.json.util.JSONUtils;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.jvnet.localizer.Localizable;

/**
 * Permission, which represents activity that requires a security privilege.
 *
 * <p>
 * Each permission is represented by a specific instance of {@link Permission}.
 *
 * @author Kohsuke Kawaguchi
 * @see http://hudson.gotdns.com/wiki/display/HUDSON/Making+your+plugin+behave+in+secured+Hudson
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
     * Human-readable description of this permission.
     * Used as a tooltip to explain this permission, so this message
     * should be a couple of sentences long.
     *
     * <p>
     * If null, there will be no description text.
     */
    public final Localizable description;

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

    public Permission(PermissionGroup group, String name, Localizable description, Permission impliedBy) {
        if(!JSONUtils.isJavaIdentifier(name))
            throw new IllegalArgumentException(name+" is not a Java identifier");
        this.owner = group.owner;
        this.group = group;
        this.name = name;
        this.description = description;
        this.impliedBy = impliedBy;

        group.add(this);
        ALL.add(this);
    }

    /**
     * @deprecated since 1.257.
     *      Use {@link #Permission(PermissionGroup, String, Localizable, Permission)} 
     */
    public Permission(PermissionGroup group, String name, Permission impliedBy) {
        this(group,name,null,impliedBy);
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
// Because of the initialization order issue, these two fields need to be defined here,
// even though they logically belong to Hudson.
//

    /**
     * {@link PermissionGroup} for {@link Hudson}.
     *
     * @deprecated
     *      Access {@link Hudson#PERMISSIONS} instead.
     */
    public static final PermissionGroup HUDSON_PERMISSIONS = new PermissionGroup(Hudson.class, hudson.model.Messages._Hudson_Permissions_Title());
    /**
     * {@link Permission} that represents the God-like access. Equivalent of Unix root.
     *
     * <p>
     * All permissions are eventually {@linkplain Permission#impliedBy implied by} this permission.
     *
     * @deprecated
     *      Access {@link Hudson#ADMINISTER} instead.
     */
    public static final Permission HUDSON_ADMINISTER = new Permission(HUDSON_PERMISSIONS,"Administer", hudson.model.Messages._Hudson_AdministerPermission_Description(),null);

//
//
// Root Permissions.
//
// These permisisons are meant to be used as the 'impliedBy' permission for other more specific permissions.
// The intention is to allow a simplified AuthorizationStrategy implementation agnostic to
// specific permissions.

    public static final PermissionGroup GROUP = new PermissionGroup(Permission.class,Messages._Permission_Permissions_Title());

    /**
     * Historically this was separate from {@link #HUDSON_ADMINISTER} but such a distinction doesn't make sense
     * any more, so deprecated.
     *
     * @deprecated
     *      Use {@link Hudson#ADMINISTER}.
     */
    public static final Permission FULL_CONTROL = new Permission(GROUP,"FullControl",HUDSON_ADMINISTER);

    /**
     * Generic read access.
     */
    public static final Permission READ = new Permission(GROUP,"GenericRead",HUDSON_ADMINISTER);

    /**
     * Generic write access.
     */
    public static final Permission WRITE = new Permission(GROUP,"GenericWrite",HUDSON_ADMINISTER);

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
