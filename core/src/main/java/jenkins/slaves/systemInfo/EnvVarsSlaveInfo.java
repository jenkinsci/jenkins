package jenkins.slaves.systemInfo;

import hudson.Extension;

/**
 * @author Kohsuke Kawaguchi
 */
@Extension
public class EnvVarsSlaveInfo extends SlaveSystemInfo {
    @Override
    public String getDisplayName() {
        return Messages.EnvVarsSlaveInfo_DisplayName();
    }
}
