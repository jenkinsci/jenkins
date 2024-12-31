package jenkins.agents.systemInfo;

import hudson.Extension;
import hudson.model.Computer;
import hudson.security.Permission;
import org.jenkinsci.Symbol;

/**
 * @author Kohsuke Kawaguchi
 */
@Extension(ordinal = 2) @Symbol("envVars")
public class EnvVarsAgentInfo extends AgentSystemInfo {
    @Override
    public String getDisplayName() {
        return Messages.EnvVarsAgentInfo_DisplayName();
    }

    @Override
    public Permission getRequiredPermission() {
        return Computer.EXTENDED_READ;
    }
}
