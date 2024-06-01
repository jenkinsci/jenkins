package jenkins.model.project;

import hudson.Extension;
import hudson.model.Action;
import hudson.model.Run;
import java.util.Collection;
import java.util.Set;
import jenkins.model.TransientActionFactory;
import jenkins.model.menu.Group;
import jenkins.model.menu.event.Event;
import jenkins.model.menu.event.LinkEvent;

@Extension
public class ChangesAction extends TransientActionFactory<Run> {

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
                return "Changes";
            }

            @Override
            public String getIconFileName() {
                return "symbol-changes";
            }

            @Override
            public Group getGroup() {
                return Group.FIRST_IN_APP_BAR;
            }

            @Override
            public Event getEvent() {
                return LinkEvent.of("changes");
            }
        });
    }
}
