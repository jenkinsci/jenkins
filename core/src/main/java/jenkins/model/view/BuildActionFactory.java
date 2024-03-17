package jenkins.model.view;

import hudson.Extension;
import hudson.model.Action;
import hudson.model.Job;
import hudson.model.View;
import java.util.Collection;
import java.util.Set;
import jenkins.model.TransientActionFactory;
import jenkins.model.menu.Group;
import jenkins.model.menu.Semantic;
import jenkins.model.menu.event.LinkAction;

@Extension
public class BuildActionFactory extends TransientActionFactory<Job> {

    @Override
    public Class<Job> type() {
        return Job.class;
    }

    @Override
    public Collection<? extends Action> createFor(Job target) {
        if (!target.hasPermission(View.CREATE)) {
            return Set.of();
        }

        return Set.of(new Action() {
                @Override
                public String getDisplayName() {
                    return "Build";
                }

                @Override
                public String getIconFileName() {
                    return "symbol-play";
                }

            @Override
            public Group getGroup() {
                return Group.FIRST_IN_APP_BAR;
            }

            @Override
            public Semantic getSemantic() {
                return Semantic.BUILD;
            }

                @Override
                public jenkins.model.menu.event.Action getAction() {
                    return LinkAction.of("build");
                }
        });
    }
}
