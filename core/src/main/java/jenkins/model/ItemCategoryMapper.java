package jenkins.model;

import hudson.Extension;
import hudson.ExtensionList;
import hudson.ExtensionPoint;
import hudson.model.Item;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import java.util.Collection;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A mapper of {@link ItemCategory}s to {@link hudson.model.Item}s.
 * TODO maybe find a better name
 */
public abstract class ItemCategoryMapper implements ExtensionPoint {

    public static final Logger LOGGER = Logger.getLogger(ItemCategoryMapper.class.getName());

    /**
     * Provides the category for the requested item or null if this mapper doesn't have one.
     *
     * @param item the item to categorise
     *
     * @return the category or null
     */
    @CheckForNull
    public abstract ItemCategory getCategoryFor(@Nonnull Item item);

    /**
     * Finds the category specified by the first mapper.
     * If none can be found {@link jenkins.model.ItemCategory.Default} is returned.
     *
     * @param item the item to categorise.
     *
     * @return the category
     */
    @Nonnull
    public static ItemCategory getCategory(@Nonnull Item item) {
        for (ItemCategoryMapper m : all()) {
            try {
                ItemCategory category = m.getCategoryFor(item);
                if (category != null) {
                    return category;
                }
            } catch (Exception ignored) {
                LOGGER.log(Level.WARNING, ignored.getMessage(), ignored);
            }
        }
        return ExtensionList.lookup(ItemCategory.Default.class).iterator().next();
    }

    public static Collection<ItemCategoryMapper> all() {
        return ExtensionList.lookup(ItemCategoryMapper.class);
    }

    /**
     * Provides some sensible defaults for at least the 2.0 recommended plugins.
     * Any plugin that wants to override this list for their own item should extend {@link ItemCategoryMapper}
     * with an {@link Extension#ordinal()} higher than {@code -10000}.
     */
    @Extension(ordinal = -10000)
    public static final class SensibleDefaultMapper extends ItemCategoryMapper {

        @Override
        public ItemCategory getCategoryFor(@Nonnull Item item) {
            //TODO load from some file provided by Gus or just if then else hardcoding
            return null;
        }
    }

    /**
     * Mapper implementation with the lowest ordinal that simply returns {@link jenkins.model.ItemCategory.Default}.
     */
    @Extension(ordinal = Integer.MIN_VALUE)
    public static final class DefaultMapper extends ItemCategoryMapper {

        @Override
        public ItemCategory getCategoryFor(@Nonnull Item item) {
            return ExtensionList.lookup(ItemCategory.Default.class).iterator().next();
        }
    }
}
