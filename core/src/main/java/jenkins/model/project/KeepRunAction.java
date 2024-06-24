package jenkins.model.project;

import hudson.Extension;
import hudson.model.Action;
import hudson.model.Run;
import hudson.security.Permission;
import java.util.Collection;
import java.util.Set;
import jenkins.model.TransientActionFactory;
import jenkins.model.menu.Group;
import jenkins.model.menu.event.ConfirmationEvent;
import jenkins.model.menu.event.Event;

@Extension
public class KeepRunAction extends TransientActionFactory<Run> {

    @Override
    public Class<Run> type() {
        return Run.class;
    }

    @Override
    public Collection<? extends Action> createFor(Run target) {
        if (!target.canToggleLogKeep()) {
            return Set.of();
        }

        if (target.isKeepLog() && target.hasPermission(Permission.DELETE)) {
            return Set.of(new Action() {
                @Override
                public String getDisplayName() {
                    return "Don't keep this build forever";
                }

                @Override
                public String getIconFileName() {
                    return "symbol-lock-closed";
                }

                @Override
                public Group getGroup() {
                    return Group.LAST_IN_MENU;
                }

                @Override
                public Event getEvent() {
                    // TODO - Change this - Deprecated method
                    return ConfirmationEvent.of(
                            Messages.DeleteProjectFactory_DeleteDialog_Title(),
                            Messages.DeleteProjectFactory_DeleteDialog_Description(),
                            target.getAbsoluteUrl() + "doDelete");
                }
            });
        }

        if (!target.isKeepLog() && target.hasPermission(Permission.UPDATE)) {
            return Set.of(new Action() {
                @Override
                public String getDisplayName() {
                    return "Keep this build forever";
                }

                @Override
                public String getIconFileName() {
                    return "symbol-lock-closed";
                }

                @Override
                public Group getGroup() {
                    return Group.LAST_IN_MENU;
                }

                @Override
                public Event getEvent() {
                    // TODO - Change this - Deprecated method
                    return ConfirmationEvent.of(
                            Messages.DeleteProjectFactory_DeleteDialog_Title(),
                            Messages.DeleteProjectFactory_DeleteDialog_Description(),
                            target.getAbsoluteUrl() + "doDelete");
                }
            });
        }

        return Set.of();
    }
}
