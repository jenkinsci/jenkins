package jenkins.model.project;

import hudson.Extension;
import hudson.model.Action;
import hudson.model.Job;
import java.util.Collection;
import java.util.Set;
import jenkins.model.TransientActionFactory;
import jenkins.model.menu.Group;
import jenkins.model.menu.Semantic;
import jenkins.model.menu.event.LinkAction;

public class DeleteProjectMenuItem implements Action {

    @Override
    public String getDisplayName() {
        return "Delete project";
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
    public static class TransientActionFactoryImpl extends TransientActionFactory<Job> {

        @Override
        public Class<Job> type() {
            return Job.class;
        }

        @Override
        public Collection<? extends Action> createFor(Job target) {
            if (!target.hasPermission(Job.DELETE)) {
                return Set.of();
            }

            return Set.of(new DeleteProjectMenuItem());
        }
    }
}
