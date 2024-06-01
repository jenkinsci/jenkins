package jenkins.model.project;

import hudson.Extension;
import hudson.model.Action;
import hudson.model.Run;
import java.util.Collection;
import java.util.Set;
import jenkins.model.TransientActionFactory;
import jenkins.model.menu.Group;
import jenkins.model.menu.Semantic;
import jenkins.model.menu.event.ConfirmationEvent;
import jenkins.model.menu.event.Event;

@Extension
public class DeleteRunAction extends TransientActionFactory<Run> {

    @Override
    public Class<Run> type() {
        return Run.class;
    }

    @Override
    public Collection<? extends Action> createFor(Run target) {
        if (!target.hasPermission(Run.DELETE)) {
            return Set.of();
        }

        return Set.of(new Action() {
            @Override
            public String getDisplayName() {
                return Messages.DeleteProjectFactory_Delete();
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
            public Event getEvent() {
                // TODO - Change this - Deprecated method
                return ConfirmationEvent.of(Messages.DeleteProjectFactory_DeleteDialog_Title(), Messages.DeleteProjectFactory_DeleteDialog_Description(),  target.getAbsoluteUrl() + "doDelete");
            }

            @Override
            public Semantic getSemantic() {
                return Semantic.DESTRUCTIVE;
            }
        });
    }
}
