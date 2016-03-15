package jenkins.model.item_category;

import hudson.Extension;
import hudson.ExtensionPoint;
import hudson.model.ModelObject;
import jenkins.model.Messages;

/**
 * A category for {@link hudson.model.Item}s.
 *
 * @since TODO
 */
public abstract class ItemCategory implements ModelObject, ExtensionPoint {

    public static int MIN_WEIGHT = Integer.MIN_VALUE;

    public static int MIN_TOSHOW = 1;

    /**
     * Identifier, e.g. "category-id-basicprojects", etc.
     *
     * @return the identifier
     */
    public abstract String getId();

    /**
     * The description in plain text
     *
     * @return the description
     */
    public abstract String getDescription();

    /**
     * Helpful to set the order.
     *
     * @return the weight
     */
    public abstract int getWeight();

    /**
     * Minimum number required to show the category.
     *
     * @return the minimum items required
     */
    public abstract int getMinToShow();

    /**
     * The default {@link ItemCategory}, if an item doesn't belong anywhere else, this is where it goes by default.
     */
    @Extension
    public static final class UncategorizedCategory extends ItemCategory {

        @Override
        public String getId() {
            return "category-id-uncategorized";
        }

        @Override
        public String getDescription() {
            return Messages.ItemCategory_Uncategorized_Description();
        }

        @Override
        public String getDisplayName() {
            return Messages.ItemCategory_Uncategorized_DisplayName();
        }

        @Override
        public int getWeight() {
            return Integer.MIN_VALUE;
        }

        @Override
        public int getMinToShow() {
            return 1;
        }

    }

}
