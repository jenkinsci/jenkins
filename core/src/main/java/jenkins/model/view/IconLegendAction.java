package jenkins.model.view;

import hudson.Extension;
import hudson.model.Action;
import hudson.model.View;
import java.util.Collection;
import java.util.Set;
import jenkins.model.TransientActionFactory;
import jenkins.model.experimentalflags.NewDashboardPageUserExperimentalFlag;
import jenkins.model.menu.Group;
import jenkins.model.menu.event.DialogEvent;
import jenkins.model.menu.event.Event;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.Beta;

/**
 * App bar action that opens the icon legend dialog for a {@link View}.
 *
 * @since TODO
 */
@Restricted(Beta.class)
public final class IconLegendAction implements Action {

    @Override
    public String getDisplayName() {
        return Messages.IconLegendAction_DisplayName();
    }

    @Override
    public String getIconFileName() {
        return "symbol-information-circle";
    }

    @Override
    public Group getGroup() {
        return Group.of(Integer.MAX_VALUE - 1);
    }

    @Override
    public String getUrlName() {
        return null;
    }

    @Override
    public Event getEvent() {
        return DialogEvent.of("legend");
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

            return Set.of(new IconLegendAction());
        }
    }
}
