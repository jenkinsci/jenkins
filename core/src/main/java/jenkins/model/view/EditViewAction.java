package jenkins.model.view;

import hudson.Extension;
import hudson.model.Action;
import hudson.model.View;
import java.util.Collection;
import java.util.Set;
import jenkins.model.TransientActionFactory;
import jenkins.model.experimentalflags.NewDashboardPageUserExperimentalFlag;
import jenkins.model.menu.Group;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.Beta;

/**
 * App bar action that links to the configuration page for an editable {@link View}.
 *
 * @since TODO
 */
@Restricted(Beta.class)
public final class EditViewAction implements Action {

    @Override
    public String getDisplayName() {
        return Messages.EditViewAction_DisplayName();
    }

    @Override
    public String getIconFileName() {
        return "symbol-edit";
    }

    @Override
    public Group getGroup() {
        return Group.FIRST_IN_MENU;
    }

    @Override
    public String getUrlName() {
        return "configure";
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

            if (!(target.isEditable() && target.hasPermission(View.CONFIGURE))) {
                return Set.of();
            }

            return Set.of(new EditViewAction());
        }
    }
}
