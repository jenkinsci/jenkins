package jenkins.model.item_category;

import hudson.Extension;
import hudson.ExtensionList;
import hudson.ExtensionPoint;
import hudson.model.TopLevelItemDescriptor;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import java.util.Collection;

/**
 * A mapper of {@link ItemCategory}s to {@link hudson.model.Item}s.
 *
 * @since TODO
 */
public abstract class ItemCategoryConfigurator implements ExtensionPoint {

    /**
     * Provides the category for the requested item or null if this mapper doesn't have one.
     *
     * @param descriptor the item it is asking about.
     *
     * @return A {@link ItemCategory} or null
     */
    @CheckForNull
    protected abstract ItemCategory getCategoryFor(@Nonnull TopLevelItemDescriptor descriptor);

    /**
     * Finds the category specified by the first configurator.
     * If none can be found {@link ItemCategory.UncategorizedCategory} is returned.
     *
     * @param descriptor the item it is asking about.
     *
     * @return A {@link ItemCategory}
     */
    @Nonnull
    public static ItemCategory getCategory(@Nonnull TopLevelItemDescriptor descriptor) {
        for (ItemCategoryConfigurator m : all()) {
            ItemCategory category = m.getCategoryFor(descriptor);
            if (category != null) {
                return category;
            }
        }
        throw new IllegalStateException("At least, must exist the category: " + ItemCategory.UncategorizedCategory.class);
    }

    public static Collection<ItemCategoryConfigurator> all() {
        return ExtensionList.lookup(ItemCategoryConfigurator.class);
    }

    /**
     * Default configurator with the lowest ordinal.
     */
    @Extension(ordinal = Integer.MIN_VALUE)
    public static final class DefaultConfigurator extends ItemCategoryConfigurator {

        @Nonnull
        @Override
        public ItemCategory getCategoryFor(@Nonnull TopLevelItemDescriptor descriptor) {
            for (ItemCategory c : ExtensionList.lookup(ItemCategory.class)) {
                if (c.getId().equals(descriptor.getCategoryId())) {
                    return c;
                }
            }
            return ExtensionList.lookup(ItemCategory.UncategorizedCategory.class).iterator().next();
        }

    }
}
