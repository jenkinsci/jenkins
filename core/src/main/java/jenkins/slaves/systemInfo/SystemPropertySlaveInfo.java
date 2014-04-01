package jenkins.slaves.systemInfo;

import hudson.Extension;

/**
 * @author Kohsuke Kawaguchi
 */
@Extension
public class SystemPropertySlaveInfo extends SlaveSystemInfo {
    @Override
    public String getDisplayName() {
        return Messages.SystemPropertySlaveInfo_DisplayName();
    }
}
