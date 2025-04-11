/*
 * The MIT License
 *
 * Copyright (c) 2004-2011, Sun Microsystems, Inc., Kohsuke Kawaguchi, Yahoo! Inc.,
 * Manufacture Francaise des Pneumatiques Michelin, Romain Seguy
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

package hudson.model;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import hudson.Functions;
import hudson.Util;
import hudson.search.SearchableModelObject;
import hudson.security.AccessControlled;
import hudson.security.Permission;
import hudson.security.PermissionGroup;
import hudson.security.PermissionScope;
import hudson.util.Secret;
import java.io.IOException;
import java.util.Collection;
import jenkins.model.Jenkins;
import jenkins.search.SearchGroup;
import jenkins.util.SystemProperties;
import jenkins.util.io.OnMaster;
import org.kohsuke.stapler.StaplerRequest2;

/**
 * Basic configuration unit in Hudson.
 *
 * <p>
 * Every {@link Item} is hosted in an {@link ItemGroup} called "parent",
 * and some {@link Item}s are {@link ItemGroup}s. This form a tree
 * structure, which is rooted at {@link jenkins.model.Jenkins}.
 *
 * <p>
 * Unlike file systems, where a file can be moved from one directory
 * to another, {@link Item} inherently belongs to a single {@link ItemGroup}
 * and that relationship will not change.
 * Think of
 * <a href="http://images.google.com/images?q=Windows%20device%20manager">Windows device manager</a>
 * &mdash; an HDD always show up under 'Disk drives' and it can never be moved to another parent.
 *
 * Similarly, {@link ItemGroup} is not a generic container. Each subclass
 * of {@link ItemGroup} can usually only host a certain limited kinds of
 * {@link Item}s.
 *
 * <p>
 * {@link Item}s have unique {@link #getName() name}s that distinguish themselves
 * among their siblings uniquely. The names can be combined by '/' to form an
 * item full name, which uniquely identifies an {@link Item} inside the whole {@link jenkins.model.Jenkins}.
 *
 * @author Kohsuke Kawaguchi
 * @see Items
 * @see ItemVisitor
 */
public interface Item extends PersistenceRoot, SearchableModelObject, AccessControlled, OnMaster {
    /**
     * Gets the parent that contains this item.
     */
    ItemGroup<? extends Item> getParent();

    /**
     * Gets all the jobs that this {@link Item} contains as descendants.
     */
    Collection<? extends Job> getAllJobs();

    /**
     * Gets the name of the item.
     *
     * <p>
     * The name must be unique among other {@link Item}s that belong
     * to the same parent.
     *
     * <p>
     * This name is also used for directory name, so it cannot contain
     * any character that's not allowed on the file system.
     *
     * @see #getFullName()
     */
    String getName();

    /**
     * Gets the full name of this item, like "abc/def/ghi".
     *
     * <p>
     * Full name consists of {@link #getName() name}s of {@link Item}s
     * that lead from the root {@link jenkins.model.Jenkins} to this {@link Item},
     * separated by '/'. This is the unique name that identifies this
     * {@link Item} inside the whole {@link jenkins.model.Jenkins}.
     *
     * @see jenkins.model.Jenkins#getItemByFullName(String,Class)
     */
    String getFullName();

    /**
     * Gets the human readable short name of this item.
     *
     * <p>
     * This method should try to return a short concise human
     * readable string that describes this item.
     * The string need not be unique.
     *
     * <p>
     * The returned string should not include the display names
     * of {@link #getParent() ancestor items}.
     */
    @Override
    String getDisplayName();

    /**
     * Works like {@link #getDisplayName()} but return
     * the full path that includes all the display names
     * of the ancestors.
     */
    String getFullDisplayName();

    /**
     * Gets the relative name to this item from the specified group.
     *
     * @param g
     *      The {@link ItemGroup} instance used as context to evaluate the relative name of this item
     * @return
     *      The name of the current item, relative to {@code g}, or {@code null} if one of the
     *      item's parents is not an {@link Item}. Nested {@link ItemGroup}s are separated by a
     *      {@code /} character (e.g., {@code ../foo/bar}).
     * @since 1.419
     */
    @Nullable
    default String getRelativeNameFrom(@CheckForNull ItemGroup g) {
        return Functions.getRelativeNameFrom(this, g);
    }

    /**
     * Short for {@code getRelativeNameFrom(item.getParent())}
     *
     * @return String like "../foo/bar".
     *      {@code null} if one of item parents is not an {@link Item}.
     * @since 1.419
     */
    @Nullable
    default String getRelativeNameFrom(@NonNull Item item)  {
        return getRelativeNameFrom(item.getParent());

    }

    /**
     * Returns the URL of this item relative to the context root of the application.
     *
     * @see AbstractItem#getUrl() for how to implement this.
     *
     * @return
     *      URL that ends with '/'.
     */
    String getUrl();

    /**
     * Returns the URL of this item relative to the parent {@link ItemGroup}.
     * @see AbstractItem#getShortUrl() for how to implement this.
     *
     * @return
     *      URL that ends with '/'.
     */
    String getShortUrl();

    /**
     * Returns the absolute URL of this item. This relies on the current
     * {@link StaplerRequest2} to figure out what the host name is,
     * so can be used only during processing client requests.
     *
     * @return
     *      absolute URL.
     * @throws IllegalStateException
     *      if the method is invoked outside the HTTP request processing.
     *
     * @deprecated
     *      This method shall <b>NEVER</b> be used during HTML page rendering, as it won't work with
     *      network set up like Apache reverse proxy.
     *      This method is only intended for the remote API clients who cannot resolve relative references
     *      (even this won't work for the same reason, which should be fixed.)
     */
    @Deprecated
    default String getAbsoluteUrl() {
        String r = Jenkins.get().getRootUrl();
        if (r == null)
            throw new IllegalStateException("Root URL isn't configured yet. Cannot compute absolute URL.");
        return Util.encode(r + getUrl());
    }

    /**
     * Called right after when a {@link Item} is loaded from disk.
     * This is an opportunity to do a post load processing.
     *
     * @param name
     *      Name of the directory (not a path --- just the name portion) from
     *      which the configuration was loaded. This usually becomes the
     *      {@link #getName() name} of this item.
     */
    void onLoad(ItemGroup<? extends Item> parent, String name) throws IOException;

    /**
     * When a {@link Item} is copied from existing one,
     * the files are first copied on the file system,
     * then it will be loaded, then this method will be invoked
     * to perform any implementation-specific work.
     */
    void onCopiedFrom(Item src);

    /**
     * When an item is created from scratch (instead of copied),
     * this method will be invoked. Used as the post-construction initialization.
     *
     * @since 1.374
      */
    default void onCreatedFromScratch() {
        // do nothing by default
    }

    /**
     * Save the settings to a file.
     *
     * Use {@link Items#getConfigFile(Item)}
     * or {@link AbstractItem#getConfigFile()} to obtain the file
     * to save the data.
     */
    @Override
    void save() throws IOException;

    /**
     * Deletes this item.
     */
    void delete() throws IOException, InterruptedException;

    @Override
    default SearchGroup getSearchGroup() {
        return SearchGroup.get(SearchGroup.ItemSearchGroup.class);
    }

    PermissionGroup PERMISSIONS = new PermissionGroup(Item.class, Messages._Item_Permissions_Title());
    Permission CREATE =
            new Permission(
                    PERMISSIONS,
                    "Create",
                    Messages._Item_CREATE_description(),
                    Permission.CREATE,
                    PermissionScope.ITEM_GROUP);
    Permission DELETE =
            new Permission(
                    PERMISSIONS,
                    "Delete",
                    Messages._Item_DELETE_description(),
                    Permission.DELETE,
                    PermissionScope.ITEM);
    Permission CONFIGURE =
            new Permission(
                    PERMISSIONS,
                    "Configure",
                    Messages._Item_CONFIGURE_description(),
                    Permission.CONFIGURE,
                    PermissionScope.ITEM);
    Permission READ =
            new Permission(
                    PERMISSIONS,
                    "Read",
                    Messages._Item_READ_description(),
                    Permission.READ,
                    PermissionScope.ITEM);
    Permission DISCOVER =
            new Permission(
                    PERMISSIONS,
                    "Discover",
                    Messages._AbstractProject_DiscoverPermission_Description(),
                    READ,
                    PermissionScope.ITEM);
    /**
     * Ability to view configuration details.
     * If the user lacks {@link #CONFIGURE} then any {@link Secret}s must be masked out, even in encrypted form.
     * @see Secret#ENCRYPTED_VALUE_PATTERN
     */
    Permission EXTENDED_READ =
            new Permission(
                    PERMISSIONS,
                    "ExtendedRead",
                    Messages._AbstractProject_ExtendedReadPermission_Description(),
                    CONFIGURE,
                    SystemProperties.getBoolean("hudson.security.ExtendedReadPermission"),
                    new PermissionScope[] {PermissionScope.ITEM});
    // TODO the following really belong in Job, not Item, but too late to move since the owner.name is encoded in the ID:
    Permission BUILD =
            new Permission(
                    PERMISSIONS,
                    "Build",
                    Messages._AbstractProject_BuildPermission_Description(),
                    Permission.UPDATE,
                    PermissionScope.ITEM);
    Permission WORKSPACE =
            new Permission(
                    PERMISSIONS,
                    "Workspace",
                    Messages._AbstractProject_WorkspacePermission_Description(),
                    Permission.READ,
                    PermissionScope.ITEM);
    Permission WIPEOUT =
            new Permission(
                    PERMISSIONS,
                    "WipeOut",
                    Messages._AbstractProject_WipeOutPermission_Description(),
                    null,
                    SystemProperties.getBoolean("hudson.security.WipeOutPermission"),
                    new PermissionScope[] {PermissionScope.ITEM});
    Permission CANCEL =
            new Permission(
                    PERMISSIONS,
                    "Cancel",
                    Messages._AbstractProject_CancelPermission_Description(),
                    Permission.UPDATE,
                    PermissionScope.ITEM);
}
