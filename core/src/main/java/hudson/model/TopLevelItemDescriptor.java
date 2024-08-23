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
import hudson.ExtensionList;
import hudson.util.FormValidation;
import java.io.StringWriter;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.model.Jenkins;
import jenkins.model.item_category.ItemCategory;
import org.apache.commons.jelly.Script;
import org.apache.commons.jelly.XMLOutput;
import org.jenkins.ui.icon.Icon;
import org.jenkins.ui.icon.IconSet;
import org.jenkins.ui.icon.IconSpec;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.MetaClass;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.WebApp;
import org.kohsuke.stapler.jelly.DefaultScriptInvoker;
import org.kohsuke.stapler.jelly.JellyClassTearOff;
import org.springframework.security.access.AccessDeniedException;

/**
 * {@link Descriptor} for {@link TopLevelItem}s.
 *
 * @author Kohsuke Kawaguchi
 */
public abstract class TopLevelItemDescriptor extends Descriptor<TopLevelItem> implements IconSpec {

    private static final Logger LOGGER = Logger.getLogger(TopLevelItemDescriptor.class.getName());

    protected TopLevelItemDescriptor(Class<? extends TopLevelItem> clazz) {
        super(clazz);
    }

    /**
     * Infers the type of the corresponding {@link TopLevelItem} from the outer class.
     * This version works when you follow the common convention, where a descriptor
     * is written as the static nested class of the describable class.
     *
     * @since 1.278
     */
    protected TopLevelItemDescriptor() {
    }

    /**
     * {@link TopLevelItemDescriptor}s often uses other descriptors to decorate itself.
     * This method allows the subtype of {@link TopLevelItemDescriptor}s to filter them out.
     *
     * <p>
     * This is useful for a workflow/company specific item type that wants to eliminate
     * options that the user would see.
     *
     * @since 1.294
     */
    public boolean isApplicable(Descriptor descriptor) {
        return true;
    }

    /**
     * {@link TopLevelItemDescriptor}s often may want to limit the scope within which they can be created.
     * This method allows the subtype of {@link TopLevelItemDescriptor}s to filter them out.
     *
     * @since 1.607
     */
    public boolean isApplicableIn(ItemGroup parent) {
        return true;
    }

    /**
     * Checks if this top level item is applicable within the specified item group.
     * <p>
     * This is just a convenience function.
     * @since 1.607
     */
    public final void checkApplicableIn(ItemGroup parent) {
        if (!isApplicableIn(parent)) {
            throw new AccessDeniedException(
                    Messages.TopLevelItemDescriptor_NotApplicableIn(getDisplayName(), parent.getFullDisplayName()));
        }
    }

    /**
     * Tests if the given instance belongs to this descriptor, in the sense
     * that this descriptor can produce items like the given one.
     *
     * <p>
     * {@link TopLevelItemDescriptor}s that act like a wizard and produces different
     * object types than {@link #clazz} can override this method to augment
     * instance-descriptor relationship.
     * @since 1.410
     */
    public boolean testInstance(TopLevelItem i) {
        return clazz.isInstance(i);
    }

    /**
     * {@inheritDoc}
     *
     * <p>
     * Used as the caption when the user chooses what item type to create.
     * The descriptor implementation also needs to have {@code newInstanceDetail.jelly}
     * script, which will be used to render the text below the caption
     * that explains the item type.
     */
    @NonNull
    @Override
    public String getDisplayName() {
        return super.getDisplayName();
    }

    /**
     * A description of this kind of item type. This description can contain HTML code but it is recommended that
     * you use plain text in order to be consistent with the rest of Jenkins.
     *
     * This method should be called from a thread where Stapler is handling an HTTP request, otherwise it will
     * return an empty string.
     *
     * @return A string, by default the value from newInstanceDetail view is taken.
     *
     * @since 2.0
     */
    @NonNull
    public String getDescription() {
        Stapler stapler = Stapler.getCurrent();
        if (stapler != null) {
            try {
                WebApp webapp = WebApp.getCurrent();
                MetaClass meta = webapp.getMetaClass(this);
                Script s = meta.loadTearOff(JellyClassTearOff.class).findScript("newInstanceDetail");
                if (s == null) {
                    return "";
                }
                DefaultScriptInvoker dsi = new DefaultScriptInvoker();
                StringWriter sw = new StringWriter();
                XMLOutput xml = dsi.createXMLOutput(sw, true);
                dsi.invokeScript(Stapler.getCurrentRequest(), Stapler.getCurrentResponse(), s, this, xml);
                return sw.toString();
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, null, e);
                return "";
            }
        } else {
            return "";
        }
    }

    /**
     * Used to categorize this kind of item type. @see {@link ItemCategory}
     *
     * @return A string with the category identifier, {@link ItemCategory.UncategorizedCategory#getId()} by default.
     *
     * @since 2.0
     */
    @NonNull
    public String getCategoryId() {
        return ItemCategory.UncategorizedCategory.ID;
    }

    /**
     * Represents a file path pattern to get the Item icon in different sizes.
     *
     * For example: plugin/plugin-shortname/images/:size/item.png, where {@code :size} represents the different
     * icon sizes used commonly in Jenkins project: 16x16, 24x24, 32x32 or 48x48
     *
     * @see FreeStyleProject.DescriptorImpl#getIconFilePathPattern()
     *
     * @return A string or null if it is not defined.
     *
     * @since 2.0
     * @deprecated prefer {@link #getIconClassName()}
     */
    @CheckForNull
    @Deprecated
    public String getIconFilePathPattern() {
        return null;
    }

    /**
     * An icon file path associated to a specific size.
     *
     * @param size A string with values that represent the common sizes: 16x16, 24x24, 32x32 or 48x48
     *
     * @return A string or null if it is not defined.
     *
     * @since 2.0
     * @deprecated prefer {@link #getIconClassName()}
     */
    @CheckForNull
    @Deprecated
    public String getIconFilePath(String size) {
        String iconFilePathPattern = getIconFilePathPattern();
        if (iconFilePathPattern != null && !iconFilePathPattern.isBlank()) {
            return iconFilePathPattern.replace(":size", size);
        }
        return null;
    }

    /**
     * Get the Item's Icon class specification e.g. 'icon-notepad'.
     * <p>
     * Note: do <strong>NOT</strong> include icon size specifications (such as 'icon-sm').
     *
     * @return The Icon class specification e.g. 'icon-notepad'.
     */
    @Override
    public String getIconClassName() {
        // Oh the fun of somebody adding a legacy way of referencing images into 2.0 code
        String pattern = getIconFilePathPattern();
        if (pattern != null) {
            // here we go with the dance of the IconSet's
            String path = pattern.replace(":size", "24x24"); // we'll strip the icon-md to get the class name
            if (path.indexOf('/') == -1) {
                // this one is easy... too easy... also will never happen
                return IconSet.toNormalizedIconNameClass(path);
            }
            if (!Jenkins.RESOURCE_PATH.isEmpty() && path.startsWith(Jenkins.RESOURCE_PATH)) {
                // will to live falling
                path = path.substring(Jenkins.RESOURCE_PATH.length());
            }
            Icon icon = IconSet.icons.getIconByUrl(path);
            if (icon != null) {
                return icon.getClassSpec().replaceAll("\\s*icon-md\\s*", " ").replaceAll("\\s+", " ");
            }
        }
        return null;
    }

    /**
     * @deprecated since 2007-01-19.
     *      This is not a valid operation for {@link Item}s.
     */
    @Deprecated
    @Override
    public TopLevelItem newInstance(StaplerRequest req) throws FormException {
        throw new UnsupportedOperationException();
    }

    /**
     * Creates a new {@link TopLevelItem}.
     *
     * @deprecated as of 1.390
     *      Use {@link #newInstance(ItemGroup, String)}
     */
    @Deprecated
    public TopLevelItem newInstance(String name) {
        return newInstance(Jenkins.get(), name);
    }

    /**
     * Creates a new {@link TopLevelItem} for the specified parent.
     *
     * @since 1.390
     */
    public abstract TopLevelItem newInstance(ItemGroup parent, String name);

    /**
     * Returns all the registered {@link TopLevelItem} descriptors.
     */
    public static ExtensionList<TopLevelItemDescriptor> all() {
        return Items.all();
    }

    @Restricted(NoExternalUse.class)
    public FormValidation doCheckDisplayNameOrNull(@AncestorInPath TopLevelItem item, @QueryParameter String value) {
        return Jenkins.get().doCheckDisplayName(value, item.getName());
    }
}
