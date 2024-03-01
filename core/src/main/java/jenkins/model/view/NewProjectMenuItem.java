package jenkins.model.view;

import hudson.Extension;
import hudson.model.Action;
import hudson.model.View;
import java.util.Collection;
import java.util.Set;
import jenkins.model.TransientActionFactory;
import jenkins.model.menu.Group;
import jenkins.model.menu.event.LinkAction;

public class NewProjectMenuItem implements Action {

    @Override
    public String getDisplayName() {
        return "New project";
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

    @Extension
    public static class TransientActionFactoryImpl extends TransientActionFactory<View> {

        @Override
        public Class<View> type() {
            return View.class;
        }

        @Override
        public Collection<? extends Action> createFor(View target) {
            return Set.of(new NewProjectMenuItem());
        }
    }
}
