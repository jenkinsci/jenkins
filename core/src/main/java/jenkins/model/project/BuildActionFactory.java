package jenkins.model.project;

import hudson.Extension;
import hudson.model.AbstractProject;
import hudson.model.Action;
import hudson.model.View;
import java.util.Collection;
import java.util.Set;
import jenkins.model.TransientActionFactory;
import jenkins.model.menu.Group;
import jenkins.model.menu.Semantic;
import jenkins.model.menu.event.DoNothingAction;
import jenkins.model.menu.event.LinkAction;

@Extension
public class BuildActionFactory extends TransientActionFactory<AbstractProject> {

    @Override
    public Class<AbstractProject> type() {
        return AbstractProject.class;
    }

    @Override
    public Collection<? extends Action> createFor(AbstractProject target) {
        if (!target.hasPermission(View.CREATE)) {
            return Set.of();
        }

        return Set.of(new Action() {
                @Override
                public String getDisplayName() {
                    return target.getBuildNowText();
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
            public String getId() {
                return "button-build";
            }

                @Override
                public jenkins.model.menu.event.Action getAction() {
                    if (target.isParameterized()) {
                        return LinkAction.of("build");
                    } else {
                     return new DoNothingAction();
                    }
                }
        });
    }
}
