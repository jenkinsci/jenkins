package jenkins.model.project;

import hudson.Extension;
import hudson.model.Action;
import hudson.model.View;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import jenkins.model.ParameterizedJobMixIn;
import jenkins.model.TransientActionFactory;
import jenkins.model.menu.Group;
import jenkins.model.menu.Semantic;
import jenkins.model.menu.event.Event;
import jenkins.model.menu.event.JavaScriptEvent;
import jenkins.model.menu.event.LinkEvent;

@Extension
public class BuildProjectAction extends TransientActionFactory<ParameterizedJobMixIn.ParameterizedJob> {

    @Override
    public Class<ParameterizedJobMixIn.ParameterizedJob> type() {
        return ParameterizedJobMixIn.ParameterizedJob.class;
    }

    @Override
    public Collection<? extends Action> createFor(ParameterizedJobMixIn.ParameterizedJob target) {
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
            public Event getEvent() {
                if (target.isParameterized()) {
                    // TODO - deprecated method - dont use this!
                    return LinkEvent.of(target.getAbsoluteUrl() + "build");
                } else {
                    return JavaScriptEvent.of(Map.of("button-type", "build", "project-id", target.getAbsoluteUrl(), "build-scheduled", Messages.BuildProjectFactory_BuildScheduled()), "jsbundles/pages/project/build.js");
                }
            }
        });
    }
}
