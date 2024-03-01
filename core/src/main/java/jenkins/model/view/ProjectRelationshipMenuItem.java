package jenkins.model.view;

import hudson.Extension;
import hudson.model.Action;
import hudson.model.ListView;
import java.util.Collection;
import java.util.Set;
import jenkins.model.TransientActionFactory;
import jenkins.model.menu.Group;
import jenkins.model.menu.event.LinkAction;

public class ProjectRelationshipMenuItem implements Action {

    @Override
    public String getDisplayName() {
        return "Project relationship";
    }

    @Override
    public String getIconFileName() {
        return "symbol-project-relationship";
    }

    @Override
    public Group getGroup() {
        return Group.FIRST_IN_MENU;
    }

    @Override
    public jenkins.model.menu.event.Action getAction() {
        return LinkAction.of("projectRelationship");
    }

    @Extension
    public static class TransientActionFactoryImpl extends TransientActionFactory<ListView> {

        @Override
        public Class<ListView> type() {
            return ListView.class;
        }

        @Override
        public Collection<? extends Action> createFor(ListView target) {
            return Set.of(new ProjectRelationshipMenuItem());
        }
    }
}
