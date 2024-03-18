package jenkins.model.view;

import hudson.Extension;
import hudson.model.AbstractBuild;
import hudson.model.Action;
import java.util.Collection;
import java.util.Set;
import jenkins.model.TransientActionFactory;
import jenkins.model.menu.Group;
import jenkins.model.menu.Semantic;
import jenkins.model.menu.event.LinkAction;

public class DeleteBuildMenuItem implements Action {

    @Override
    public String getDisplayName() {
        return "Delete build";
    }

    @Override
    public String getIconFileName() {
        return "symbol-trash";
    }

    @Override
    public Group getGroup() {
        return Group.LAST_IN_MENU;
    }

    @Override
    public jenkins.model.menu.event.Action getAction() {
        return LinkAction.of("delete");
    }

    @Override
    public Semantic getSemantic() {
        return Semantic.DESTRUCTIVE;
    }

    @Extension
    public static class TransientActionFactoryImpl extends TransientActionFactory<AbstractBuild> {

        @Override
        public Class<AbstractBuild> type() {
            return AbstractBuild.class;
        }

        @Override
        public Collection<? extends Action> createFor(AbstractBuild target) {
            if (!target.hasPermission(AbstractBuild.DELETE)) {
                return Set.of();
            }

            return Set.of(new DeleteBuildMenuItem());
        }
    }
}
