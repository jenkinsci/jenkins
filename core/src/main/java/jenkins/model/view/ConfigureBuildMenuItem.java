package jenkins.model.view;

import hudson.Extension;
import hudson.model.AbstractBuild;
import hudson.model.Action;
import java.util.Collection;
import java.util.Set;
import jenkins.model.TransientActionFactory;
import jenkins.model.menu.Group;
import jenkins.model.menu.event.LinkAction;

public class ConfigureBuildMenuItem implements Action {

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
    public static class TransientActionFactoryImpl extends TransientActionFactory<AbstractBuild> {

        @Override
        public Class<AbstractBuild> type() {
            return AbstractBuild.class;
        }

        @Override
        public Collection<? extends Action> createFor(AbstractBuild target) {
            if (!target.hasPermission(AbstractBuild.UPDATE)) {
                return Set.of();
            }

            return Set.of(new ConfigureBuildMenuItem());
        }
    }
}
