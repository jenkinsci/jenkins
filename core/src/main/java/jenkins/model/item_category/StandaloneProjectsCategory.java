package jenkins.model.item_category;

import hudson.Extension;
import jenkins.model.Messages;

/**
 * A generic {@link ItemCategory}
 */
@Extension(ordinal = -100)
public class StandaloneProjectsCategory extends ItemCategory {

    public static final String ID = "standaloneprojects";

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public String getDescription() {
        return Messages.ItemCategory_StandaloneProjects_Description();
    }

    @Override
    public String getDisplayName() {
        return Messages.ItemCategory_StandaloneProjects_DisplayName();
    }

    @Override
    public int getMinToShow() {
        return ItemCategory.MIN_TOSHOW;
    }
}
