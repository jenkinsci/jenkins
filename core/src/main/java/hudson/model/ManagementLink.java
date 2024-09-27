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

package hudson.model;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.ExtensionList;
import hudson.ExtensionListView;
import hudson.ExtensionPoint;
import hudson.security.Permission;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.management.Badge;
import jenkins.model.Jenkins;
import org.jvnet.localizer.Localizable;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.interceptor.RequirePOST;

/**
 * Extension point to add icon to {@code http://server/hudson/manage} page.
 *
 * <p>
 * This is a place for exposing features that are only meant for system admins
 * (whereas features that are meant for Hudson users at large should probably
 * be added to {@link Jenkins#getActions()}.)
 *
 * <p>
 * To register a new instance, put {@link Extension} on your implementation class.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.194
 */
public abstract class ManagementLink implements ExtensionPoint, Action {

    /**
     * Mostly works like {@link Action#getIconFileName()}, except that
     * the expected icon format is SVG. So if you give just a file name, "/images/svgs" will be assumed.
     *
     * @return
     *      As a special case, return null to exclude this object from the management link.
     *      This is useful for defining {@link ManagementLink} that only shows up under
     *      certain circumstances.
     */
    @Override
    public abstract @CheckForNull String getIconFileName();

    /**
     * Returns a short description of what this link does. This text
     * is the one that's displayed in grey. This can include HTML,
     * although the use of block tags is highly discouraged.
     *
     * Optional.
     */
    public String getDescription() {
        return "";
    }

    /**
     * {@inheritDoc}
     *
     * <p>
     * In case of {@link ManagementLink}, this value is put straight into the href attribute,
     * so relative paths are interpreted against the root {@link Jenkins} object.
     */
    @Override
    public abstract @CheckForNull String getUrlName();

    /**
     * Allows implementations to request that this link show a confirmation dialog, and use POST if confirmed.
     * Suitable for links which perform an action rather than simply displaying a page.
     * @return true if this link takes an action
     * @see RequirePOST
     * @since 1.512
     */
    public boolean getRequiresConfirmation() {
        return false;
    }

    /**
     * All registered instances.
     * @deprecated as of 1.286
     *      Use {@link #all()} for read access and put {@link Extension} for registration.
     */
    @Deprecated
    public static final List<ManagementLink> LIST = ExtensionListView.createList(ManagementLink.class);

    /**
     * All registered instances.
     */
    public static @NonNull ExtensionList<ManagementLink> all() {
        return ExtensionList.lookup(ManagementLink.class);
    }

    /**
     * Returns the permission required for user to see this management link on the "Manage Jenkins" page ({@link ManageJenkinsAction}).
     *
     * Historically, this returned null, which amounted to the same behavior, as {@link Jenkins#ADMINISTER} was required to access the page.
     *
     * @return the permission required for the link to be shown on "Manage Jenkins".
     */
    public @NonNull Permission getRequiredPermission() {
        return Jenkins.ADMINISTER;
    }

    /**
     * Define if the rendered link will use the default GET method or POST.
     * @return true if POST must be used
     * @see RequirePOST
     * @since 1.584
     */
    public boolean getRequiresPOST() {
        return false;
    }

    /**
     * Name of the category for this management link. Exists so that plugins with core dependency pre-dating the version
     * when this was introduced can define a category. Plugins with newer core dependency override {@link #getCategory()} instead.
     *
     * @return name of the desired category, one of the enum values of {@link Category}, e.g. {@code STATUS}.
     * @since 2.226
     */
    @Restricted(NoExternalUse.class) // TODO I don't think this works
    protected @NonNull String getCategoryName() {
        return "UNCATEGORIZED";
    }

    /**
     * Category for management link, uses {@code String} so it can be done with core dependency pre-dating the version this feature was added.
     *
     * @return An enum value of {@link Category}.
     * @since 2.226
     */
    public @NonNull Category getCategory() {
        try {
            return Category.valueOf(getCategoryName());
        } catch (RuntimeException e) {
            LOGGER.log(Level.WARNING, "invalid category {0} for class {1}", new Object[]{getCategoryName(), this.getClass().getName()});
            return Category.UNCATEGORIZED;
        }
    }

    /**
     * Categories supported by this version of core.
     *
     * @since 2.226
     */
    public enum Category {
        /**
         * Configuration pages that don't fit into a more specific section.
         */
        CONFIGURATION(Messages._ManagementLink_Category_CONFIGURATION()),
        /**
         * Security related options. Useful for plugins providing security related {@code ManagementLink}s (e.g. security realms).
         * Use {@link Category#STATUS} instead if the feature is informational.
         */
        SECURITY(Messages._ManagementLink_Category_SECURITY()),
        /**
         * Status information about the Jenkins instance, such as log messages, load statistics, or general information.
         */
        STATUS(Messages._ManagementLink_Category_STATUS()),
        /**
         * Troubleshooting utilities. This overlaps some with status information, but the difference is that status
         * always applies, while troubleshooting only matters when things go wrong.
         */
        TROUBLESHOOTING(Messages._ManagementLink_Category_TROUBLESHOOTING()),
        /**
         * Tools are specifically tools for administrators,
         * such as the Jenkins CLI and Script Console,
         * as well as specific stand-alone administrative features ({@link jenkins.management.ShutdownLink}, {@link jenkins.management.ReloadLink}).
         * This has nothing to do with build tools or tool installers.
         */
        TOOLS(Messages._ManagementLink_Category_TOOLS()),
        /**
         * Anything that doesn't fit into any of the other categories. Expected to be necessary only very rarely.
         */
        MISC(Messages._ManagementLink_Category_MISC()),
        /**
         * The default category for uncategorized items. Do not explicitly specify this category for your {@code ManagementLink}.
         */
        UNCATEGORIZED(Messages._ManagementLink_Category_UNCATEGORIZED());

        private final Localizable label;

        Category(Localizable label) {
            this.label = label;
        }

        public @NonNull String getLabel() {
            return label.toString();
        }
    }

    /**
     * A {@link Badge} shown as overlay over the icon on "Manage Jenkins".
     *
     * @return badge or {@code null} if no badge should be shown.
     * @since 2.385
     */
    public @CheckForNull Badge getBadge() {
        return null;
    }

    private static final Logger LOGGER = Logger.getLogger(ManagementLink.class.getName());
}
