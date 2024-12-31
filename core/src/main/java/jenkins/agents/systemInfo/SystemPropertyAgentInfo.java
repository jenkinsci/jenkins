package jenkins.agents.systemInfo;

import hudson.Extension;
import hudson.model.Computer;
import hudson.security.Permission;
import org.jenkinsci.Symbol;

/**
 * @author Kohsuke Kawaguchi
 */
@Extension(ordinal = 3) @Symbol("systemProperties")
public class SystemPropertyAgentInfo extends AgentSystemInfo {
    @Override
    public String getDisplayName() {
        return Messages.SystemPropertyAgentInfo_DisplayName();
    }

    @Override
    public Permission getRequiredPermission() {
        return Computer.EXTENDED_READ;
    }
}
