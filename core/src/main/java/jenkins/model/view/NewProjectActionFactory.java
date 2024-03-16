package jenkins.model.view;

import hudson.Extension;
import hudson.model.Action;
import hudson.model.View;
import java.util.Collection;
import java.util.Set;
import jenkins.model.TransientActionFactory;
import jenkins.model.menu.Group;
import jenkins.model.menu.event.LinkAction;

@Extension
public class NewProjectActionFactory extends TransientActionFactory<View> {

    @Override
    public Class<View> type() {
        return View.class;
    }

    @Override
    public Collection<? extends Action> createFor(View target) {
        if (!target.hasPermission(View.CREATE)) {
            return Set.of();
        }

        return Set.of(new Action() {
                @Override
                public String getDisplayName() {
                    return "New " + target.getNewPronoun();
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
                public jenkins.model.menu.event.Action getAction() {
                    return LinkAction.of("newJob");
                }
        });
    }
}
