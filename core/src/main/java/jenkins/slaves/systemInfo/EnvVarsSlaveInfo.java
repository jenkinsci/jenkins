package jenkins.slaves.systemInfo;

import hudson.Extension;
import hudson.model.Computer;
import hudson.security.Permission;
import org.jenkinsci.Symbol;

/**
 * @author Kohsuke Kawaguchi
 */
@Extension(ordinal=2) @Symbol("envVars")
public class EnvVarsSlaveInfo extends SlaveSystemInfo {
    @Override
    public String getDisplayName() {
        return Messages.EnvVarsSlaveInfo_DisplayName();
    }

    @Override
    public Permission getRequiredPermission() {
        return Computer.EXTENDED_READ;
    }
}
