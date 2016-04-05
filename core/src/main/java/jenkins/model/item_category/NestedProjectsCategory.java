package jenkins.model.item_category;

import hudson.Extension;
import jenkins.model.Messages;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

/**
 * A generic {@link ItemCategory}.
 *
 * This category should be moved to cloudbees-folder-plugin short-term.
 */
@Restricted(NoExternalUse.class)
@Extension(ordinal = -100)
public class NestedProjectsCategory extends ItemCategory {

    public static final String ID = "nestedprojects";

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public String getDescription() {
        return Messages.ItemCategory_NestedProjects_Description();
    }

    @Override
    public String getDisplayName() {
        return Messages.ItemCategory_NestedProjects_DisplayName();
    }

    @Override
    public int getMinToShow() {
        return ItemCategory.MIN_TOSHOW;
    }
}
