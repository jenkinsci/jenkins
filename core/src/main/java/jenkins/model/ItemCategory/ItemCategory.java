package jenkins.model.ItemCategory;

import hudson.Extension;
import hudson.ExtensionPoint;
import hudson.model.ModelObject;
import jenkins.model.Messages;

/**
 * A category for {@link hudson.model.Item}s.
 */
public abstract class ItemCategory implements ModelObject, ExtensionPoint {

    /**
     * Identifier, e.g. "category-id-default", etc.
     *
     * @return the identifier
     */
    public abstract String getId();

    /**
     * The icon class specification e.g. 'category-icon-folder', 'category-icon-default', etc.
     *
     * @return the icon class specification
     */
    public abstract String getIconClassName();

    /**
     * The description in plain text
     *
     * @return the description
     */
    public abstract String getDescription();

    /**
     * The default category, if an item doesn't belong anywhere else, this is where it goes by default.
     */
    @Extension
    public static final class Default extends ItemCategory {

        @Override
        public String getId() {
            return "category-id-default";
        }

        @Override
        public String getIconClassName() {
            return "category-icon-default";
        }

        @Override
        public String getDescription() {
            return Messages.ItemCategory_Default_Description();
        }

        @Override
        public String getDisplayName() {
            return Messages.ItemCategory_Default_DisplayName();
        }
    }

}
