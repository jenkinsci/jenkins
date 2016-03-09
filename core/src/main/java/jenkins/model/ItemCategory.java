package jenkins.model;

import hudson.Extension;
import hudson.ExtensionPoint;
import hudson.model.ModelObject;

/**
 * A category for {@link hudson.model.Item}s.
 */
public abstract class ItemCategory implements ModelObject, ExtensionPoint {

    /**
     * The icon class specification e.g. 'icon-category-folder', 'icon-help' etc.
     * The size specification should not be provided as that is determined by the
     * context of where the category is displayed.
     *
     * @return the icon class specification
     */
    public abstract String getIconClassName();

    /**
     * The default category, if an item doesn't belong anywhere else, this is where it goes by default.
     */
    @Extension
    public static final class Default extends ItemCategory {

        @Override
        public String getIconClassName() {
            return "icon-category-default"; //TODO whatever Gus decides
        }

        @Override
        public String getDisplayName() {
            return Messages.ItemCategory_Default_DisplayName();
        }
    }

    /**
     * A category suitable for folder and container (not docker) like items.
     */
    @Extension
    public static final class Folders extends ItemCategory {

        @Override
        public String getIconClassName() {
            return "icon-category-folders"; //TODO whatever Gus decides
        }

        @Override
        public String getDisplayName() {
            return Messages.ItemCategory_Folders_DisplayName();
        }
    }
}
