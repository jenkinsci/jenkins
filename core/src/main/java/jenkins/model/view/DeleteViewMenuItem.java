package jenkins.model.view;

import hudson.Extension;
import hudson.model.Action;
import hudson.model.ListView;
import java.util.Collection;
import java.util.Set;
import jenkins.model.TransientActionFactory;
import jenkins.model.menu.Group;
import jenkins.model.menu.Semantic;
import jenkins.model.menu.event.LinkAction;

public class DeleteViewMenuItem implements Action {

    @Override
    public String getDisplayName() {
        return "Delete view";
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
    public static class TransientActionFactoryImpl extends TransientActionFactory<ListView> {

        @Override
        public Class<ListView> type() {
            return ListView.class;
        }

        @Override
        public Collection<? extends Action> createFor(ListView target) {
            if (!target.hasPermission(ListView.DELETE) || !target.getOwner().canDelete(target)) {
                return Set.of();
            }

            return Set.of(new DeleteViewMenuItem());
        }
    }
}
