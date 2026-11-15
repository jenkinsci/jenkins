package jenkins.model.agent;

import hudson.Extension;
import hudson.model.Action;
import hudson.model.Computer;
import java.util.Collection;
import java.util.Set;
import jenkins.model.TransientActionFactory;
import jenkins.model.menu.Group;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.Beta;

@Restricted(Beta.class) // for now, until we have a better story for agent configuration
public class ConfigureAgentAction implements Action {
    private final Computer agent;

    ConfigureAgentAction(Computer agent) {
        this.agent = agent;
    }

    @Override
    public String getIconFileName() {
        return "symbol-settings";
    }

    @Override
    public String getDisplayName() {
        if (agent.hasPermission(Computer.CONFIGURE)) {
            return Messages.ConfigureAgentAction_Title();
        }
        return Messages.ConfigureAgentAction_ViewConfiguration();
    }

    @Override
    public Group getGroup() {
        return Group.IN_APP_BAR;
    }

    @Override
    public String getUrlName() {
        return "configure";
    }

    @Extension
    @Restricted(Beta.class)
    public static final class Factory extends TransientActionFactory<Computer> {

        @Override
        public Class<Computer> type() {
            return Computer.class;
        }

        @Override
        public Collection<? extends Action> createFor(Computer target) {
            if (target.hasAnyPermission(Computer.CONFIGURE, Computer.EXTENDED_READ)) {
                return Set.of(new ConfigureAgentAction(target));
            }
            return Set.of();
        }
    }
}
