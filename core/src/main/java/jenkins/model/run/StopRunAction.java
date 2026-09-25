package jenkins.model.run;

import hudson.Extension;
import hudson.model.Action;
import hudson.model.Run;
import java.util.Collection;
import java.util.Set;
import jenkins.model.TransientActionFactory;
import jenkins.model.experimentalflags.NewBuildPageUserExperimentalFlag;
import jenkins.model.menu.Group;
import jenkins.model.menu.Semantic;
import jenkins.model.menu.event.ConfirmationEvent;
import jenkins.model.menu.event.Event;

@Extension
public class StopRunAction extends TransientActionFactory<Run> {

    @Override
    public Class<Run> type() {
        return Run.class;
    }

    @Override
    public Collection<? extends Action> createFor(Run target) {
        Boolean newBuildPageEnabled = new NewBuildPageUserExperimentalFlag().getFlagValue();

        // This condition can be removed when the flag has been removed
        if (!newBuildPageEnabled) {
            return Set.of();
        }

        if (!target.isBuilding()) {
            return Set.of();
        }

        return Set.of(new Action() {
            @Override
            public String getDisplayName() {
                return "Cancel";
            }

            @Override
            public String getIconFileName() {
                return "symbol-stop-circle";
            }

            @Override
            public Group getGroup() {
                return Group.FIRST_IN_APP_BAR;
            }

            @Override
            public String getUrlName() {
                return null;
            }

            @Override
            public Event getEvent() {
                return ConfirmationEvent.of(
                        Messages.StopRunAction_confirm(target.getFullDisplayName()),
                        null, "stop"
                );
            }

            @Override
            public Semantic getSemantic() {
                return Semantic.DESTRUCTIVE;
            }
        });
    }
}
