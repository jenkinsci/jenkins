package jenkins.model.project;

import hudson.Extension;
import hudson.model.Action;
import hudson.model.Job;
import java.util.Collection;
import java.util.Set;
import jenkins.model.TransientActionFactory;
import jenkins.model.menu.Group;
import jenkins.model.menu.event.LinkAction;

@Extension
public class ConfigureProjectAction extends TransientActionFactory<Job> {

    @Override
    public Class<Job> type() {
        return Job.class;
    }

    @Override
    public Collection<? extends Action> createFor(Job target) {
        if (!target.hasPermission(Job.CONFIGURE) && !(target.hasPermission(Job.EXTENDED_READ))) {
            return Set.of();
        }

        return Set.of(new Action() {
            @Override
            public String getDisplayName() {
                return target.hasPermission(Job.CONFIGURE) ? Messages.ConfigureProjectFactory_Configure() : Messages.ConfigureProjectFactory_View();
            }

            @Override
            public String getIconFileName() {
                return "symbol-settings";
            }

            @Override
            public Group getGroup() {
                return Group.IN_APP_BAR;
            }

            @Override
            public jenkins.model.menu.event.Action getAction() {
                // TODO - deprecated method - dont use this!
                return LinkAction.of(target.getAbsoluteUrl() + "configure");
            }
        });
    }
}
