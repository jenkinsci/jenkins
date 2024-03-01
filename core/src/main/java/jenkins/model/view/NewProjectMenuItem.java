package jenkins.model.view;

import hudson.Extension;
import hudson.model.Action;
import hudson.model.AllView;
import hudson.model.ListView;
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
    public static class TransientActionFactoryListViewImpl extends TransientActionFactory<ListView> {

        @Override
        public Class<ListView> type() {
            return ListView.class;
        }

        @Override
        public Collection<? extends Action> createFor(ListView target) {
            if (!target.hasPermission(ListView.CREATE)) {
                return Set.of();
            }

            return Set.of(new NewProjectMenuItem());
        }
    }

    @Extension
    public static class TransientActionFactoryViewImpl extends TransientActionFactory<AllView> {

        @Override
        public Class<AllView> type() {
            return AllView.class;
        }

        @Override
        public Collection<? extends Action> createFor(AllView target) {
            if (!target.hasPermission(AllView.CREATE)) {
                return Set.of();
            }

            return Set.of(new NewProjectMenuItem());
        }
    }
}
