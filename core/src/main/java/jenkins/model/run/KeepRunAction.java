package jenkins.model.run;

import hudson.Extension;
import hudson.model.Action;
import hudson.model.Run;
import hudson.security.Permission;
import java.util.Collection;
import java.util.Set;
import jenkins.model.TransientActionFactory;
import jenkins.model.menu.Group;
import jenkins.model.menu.event.Event;
import jenkins.model.menu.event.LinkEvent;

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
                    return Group.FIRST_IN_MENU;
                }

                @Override
                public Event getEvent() {
                    return LinkEvent.of("toggleLogKeep", LinkEvent.LinkEventType.POST);
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
                    return "symbol-lock-open";
                }

                @Override
                public Group getGroup() {
                    return Group.FIRST_IN_MENU;
                }

                @Override
                public Event getEvent() {
                    return LinkEvent.of("toggleLogKeep", LinkEvent.LinkEventType.POST);
                }
            });
        }

        return Set.of();
    }
}
