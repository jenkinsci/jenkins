package jenkins.slaves.systemInfo;

import hudson.Extension;
import jenkins.slaves.systemInfo.Messages;
import jenkins.slaves.systemInfo.Messages;
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
}
