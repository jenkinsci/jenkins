package jenkins.model;

import hudson.Extension;
import hudson.ExtensionList;
import hudson.ExtensionPoint;
import hudson.model.ModelObject;

import hudson.security.Messages;

/**
 * Grouping of related {@link GlobalConfiguration}s.
 *
 * <p>
 * To facilitate the separation of the global configuration into multiple pages, tabs, and so on,
 * {@link GlobalConfiguration}s are classified into categories (such as "security", "tools", as well
 * as the catch all "unclassified".) Categories themselves are extensible &mdash; plugins may introduce
 * its own category as well, although that should only happen if you are creating a big enough subsystem.
 *
 * <p>
 * The primary purpose of this is to enable future UIs to split the global configurations to
 * smaller pieces that can be individually looked at and updated.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.494
 * @see GlobalConfiguration
 */
public abstract class GlobalConfigurationCategory implements ExtensionPoint, ModelObject {
    /**
     * One-line plain text message that explains what this category is about.
     * This can be used in the UI to help the user pick the right category.
     *
     * The text should be longer than {@link #getDisplayName()}
     */
    public abstract String getShortDescription();

    /**
     * Returns all the registered {@link GlobalConfiguration} descriptors.
     */
    public static ExtensionList<GlobalConfigurationCategory> all() {
        return ExtensionList.lookup(GlobalConfigurationCategory.class);
    }

    public static <T extends GlobalConfigurationCategory> T get(Class<T> type) {
        return all().get(type);
    }

    /**
     * This category represents the catch-all I-dont-know-what-category-it-is instance,
     * used for those {@link GlobalConfiguration}s that don't really deserve/need a separate
     * category.
     *
     * Also used for backward compatibility. All {@link GlobalConfiguration}s without
     * explicit category gets this as the category.
     *
     * In the current UI, this corresponds to the /configure link.
     */
    @Extension
    public static class Unclassified extends GlobalConfigurationCategory {
        @Override
        public String getShortDescription() {
            return jenkins.management.Messages.ConfigureLink_Description();
        }

        public String getDisplayName() {
            return jenkins.management.Messages.ConfigureLink_DisplayName();
        }
    }

    /**
     * Security related configurations.
     */
    @Extension
    public static class Security extends GlobalConfigurationCategory {
        @Override
        public String getShortDescription() {
            return Messages.GlobalSecurityConfiguration_Description();
        }

        public String getDisplayName() {
            return hudson.security.Messages.GlobalSecurityConfiguration_DisplayName();
        }
    }
}
