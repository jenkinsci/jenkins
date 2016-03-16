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

    /**
     * Provides the weight for the requested item. Helpful to order a list.
     *
     * @param descriptor the item it is asking about.
     *
     * @return the weight or null
     */
    @CheckForNull
    protected abstract Integer getWeightFor(@Nonnull TopLevelItemDescriptor descriptor);

    /**
     * Finds the weight specified by the first configurator.
     * If none can be found {@link Integer#MIN_VALUE} is returned. {@see DefaultConfigurator#getWeightFor}.
     *
     * @param descriptor the item it is asking about.
     *
     * @return the weight
     */
    @Nonnull
    public static Integer getWeight(@Nonnull TopLevelItemDescriptor descriptor) {
        for (ItemCategoryConfigurator m : all()) {
            Integer weight = m.getWeightFor(descriptor);
            if (weight != null) {
                return weight;
            }
        }
        throw new IllegalStateException("At least, a default value must exist for weight field");
    }

    /**
     * Provides the description for the requested item or null if this configurator doesn't have one.
     *
     * @param descriptor the item it is asking about.
     *
     * @return A {@link ItemCategory} or null
     */
    @CheckForNull
    protected abstract String getDescriptionFor(@Nonnull TopLevelItemDescriptor descriptor);

    /**
     * Finds the weight specified by the first configurator.
     * If none can be found a empty string is returned. {@see DefaultConfigurator#getDescriptionFor}.
     *
     * @param descriptor the item it is asking about.
     *
     * @return A {@link ItemCategory}
     */
    @Nonnull
    public static String getDescription(@Nonnull TopLevelItemDescriptor descriptor) {
        for (ItemCategoryConfigurator m : all()) {
            String description = m.getDescriptionFor(descriptor);
            if (description != null) {
                return description;
            }
        }
        throw new IllegalStateException("At least, a default value must exist for description field");
    }

    /**
     * Provides the effective clazz for the requested item or null if this configurator doesn't have one.
     *
     * @param descriptor the item it is asking about.
     *
     * @return A string or null
     */
    @CheckForNull
    protected abstract String getEffectiveClazzFor(@Nonnull TopLevelItemDescriptor descriptor);

    /**
     * Finds the effective clazz specified by the first configurator.
     * If none can be found a empty string with {@code descriptor.clazz.getName();} is returned. {@see DefaultConfigurator#getEffectiveClazzFor}.
     *
     * @param descriptor the item it is asking about.
     *
     * @return A string
     */
    @Nonnull
    public static String getEffectiveClazz(@Nonnull TopLevelItemDescriptor descriptor) {
        for (ItemCategoryConfigurator m : all()) {
            String clazz = m.getEffectiveClazzFor(descriptor);
            if (clazz != null) {
                return clazz;
            }
        }
        throw new IllegalStateException("At least, a default value must exist for clazz field");
    }

    /**
     * Provides the icon path pattern for the requested item or null if this configurator doesn't have one.
     * For example: /plugin/shortnme-of-myplugin/icons/item/:size:/myitem.pngm where :size should be replaced by the consumer using
     * the standard sizes in Jenkins: 16x16, 24x24, etc...
     *
     * @param descriptor the item it is asking about.
     *
     * @return A string or null
     */
    @CheckForNull
    protected abstract String getIconFilePathPatternFor(@Nonnull TopLevelItemDescriptor descriptor);

    /**
     * Finds the icon path pattern specified by the first configurator.
     * If none can be found a empty string is returned. {@see DefaultConfigurator#getIconFilePathFor}.
     *
     * @param descriptor the item it is asking about.
     *
     * @return A string
     */
    @Nonnull
    public static String getIconFilePathPattern(@Nonnull TopLevelItemDescriptor descriptor) {
        for (ItemCategoryConfigurator m : all()) {
            String path = m.getIconFilePathPatternFor(descriptor);
            if (path != null) {
                return path;
            }
        }
        throw new IllegalStateException("At least, a default value must exist for icon path pattern");
    }

    public static Collection<ItemCategoryConfigurator> all() {
        return ExtensionList.lookup(ItemCategoryConfigurator.class);
    }

    /**
     * Default configurator with the lowest ordinal that simply returns default values.
     */
    @Extension(ordinal = Integer.MIN_VALUE)
    public static final class DefaultConfigurator extends ItemCategoryConfigurator {

        @Nonnull
        @Override
        public ItemCategory getCategoryFor(@Nonnull TopLevelItemDescriptor descriptor) {
            return ExtensionList.lookup(ItemCategory.UncategorizedCategory.class).iterator().next();
        }

        @Nonnull
        @Override
        public Integer getWeightFor(@Nonnull TopLevelItemDescriptor descriptor) {
            return Integer.MIN_VALUE;
        }

        @Nonnull
        @Override
        public String getDescriptionFor(@Nonnull TopLevelItemDescriptor descriptor) {
            return "";
        }

        @Nonnull
        @Override
        public String getEffectiveClazzFor(@Nonnull TopLevelItemDescriptor descriptor) {
            return descriptor.clazz.getName();
        }

        @Nonnull
        @Override
        public String getIconFilePathPatternFor(@Nonnull TopLevelItemDescriptor descriptor) {
            return "";
        }

    }
}
