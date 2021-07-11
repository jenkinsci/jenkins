package jenkins.agents;

import hudson.Extension;
import hudson.model.UnprotectedRootAction;
import jenkins.model.Jenkins;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.StaplerProxy;

/**
 * Provides the {@code /tcp-agent-listener} URL.
 *
 * @since TODO
 */
@Restricted(NoExternalUse.class)
@Extension
public class TcpAgentListenerRootAction implements UnprotectedRootAction, StaplerProxy {
    @Override
    public String getIconFileName() {
        return null;
    }

    @Override
    public String getDisplayName() {
        return null;
    }

    @Override
    public String getUrlName() {
        return "tcp-agent-listener";
    }

    @Override
    public Object getTarget() {
        return Jenkins.get().getTcpSlaveAgentListener();
    }
}
