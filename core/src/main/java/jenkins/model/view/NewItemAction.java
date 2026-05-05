package jenkins.model.view;

import hudson.Extension;
import hudson.model.Action;
import hudson.model.Item;
import hudson.model.View;
import java.util.Collection;
import java.util.Set;
import jenkins.model.TransientActionFactory;
import jenkins.model.experimentalflags.NewDashboardPageUserExperimentalFlag;
import jenkins.model.menu.Group;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.Beta;

/**
 * App bar action that links to the New Item page for a {@link View}.
 *
 * <p>The display name is adjusted using the view's pronoun for items.
 *
 * @since TODO
 */
@Restricted(Beta.class)
public final class NewItemAction implements Action {

    private final View target;

    NewItemAction(View target) {
        this.target = target;
    }

    @Override
    public String getDisplayName() {
        return Messages.NewItemAction_DisplayName(target.getNewPronoun());
    }

    @Override
    public String getIconFileName() {
        return "symbol-add";
    }

    @Override
    public Group getGroup() {
        return Group.FIRST_IN_APP_BAR;
    }

    @Override
    public String getUrlName() {
        return "newJob";
    }

    @Extension
    @Restricted(Beta.class)
    public static final class Factory extends TransientActionFactory<View> {

        @Override
        public Class<View> type() {
            return View.class;
        }

        @Override
        public Collection<? extends Action> createFor(View target) {
            Boolean newDashboardPageEnabled = new NewDashboardPageUserExperimentalFlag().getFlagValue();

            // This condition can be removed when the flag has been removed
            if (!newDashboardPageEnabled) {
                return Set.of();
            }

            if (!target.hasPermission(Item.CREATE)) {
                return Set.of();
            }

            return Set.of(new NewItemAction(target));
        }
    }
}
