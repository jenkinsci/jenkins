package jenkins.model.view;

import hudson.Extension;
import hudson.model.Action;
import hudson.model.ListView;
import java.util.Collection;
import java.util.Set;
import jenkins.model.TransientActionFactory;
import jenkins.model.menu.Group;
import jenkins.model.menu.event.LinkAction;

public class ConfigureViewMenuItem implements Action {

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
    public static class TransientActionFactoryImpl extends TransientActionFactory<ListView> {

        @Override
        public Class<ListView> type() {
            return ListView.class;
        }

        @Override
        public Collection<? extends Action> createFor(ListView target) {
            if (!target.hasPermission(ListView.CONFIGURE) || !target.isEditable()) {
                return Set.of();
            }

            return Set.of(new ConfigureViewMenuItem());
        }
    }
}
