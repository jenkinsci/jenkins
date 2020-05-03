package jenkins.slaves.systemInfo;

import hudson.Extension;
import hudson.model.Computer;
import hudson.security.Permission;
import org.jenkinsci.Symbol;

/**
 * @author Kohsuke Kawaguchi
 */
@Extension(ordinal=3) @Symbol("systemProperties")
public class SystemPropertySlaveInfo extends SlaveSystemInfo {
    @Override
    public String getDisplayName() {
        return Messages.SystemPropertySlaveInfo_DisplayName();
    }

    @Override
    public Permission getRequiredPermission() {
        return Computer.EXTENDED_READ;
    }
}
