package jenkins.model.view;

import hudson.Extension;
import hudson.model.Action;
import hudson.model.View;
import java.util.Collection;
import java.util.Set;
import jenkins.model.TransientActionFactory;
import jenkins.model.menu.Group;
import jenkins.model.menu.event.LinkAction;

public class ConfigureMenuItem implements Action {

    @Override
    public String getDisplayName() {
        return "Configure";
    }

    @Override
    public String getIconFileName() {
        return "symbol-settings";
    }

    @Override
    public Group getGroup() {
        return Group.IN_APP_BAR;
    }

    @Override
    public jenkins.model.menu.event.Action getAction() {
        return LinkAction.of("configure");
    }

    @Extension
    public static class TransientActionFactoryImpl extends TransientActionFactory<View> {

        @Override
        public Class<View> type() {
            return View.class;
        }

        @Override
        public Collection<? extends Action> createFor(View target) {
            return Set.of(new ConfigureMenuItem());
        }
    }
}
