package jenkins.slaves.systemInfo;

import hudson.Extension;
import jenkins.slaves.systemInfo.Messages;
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
}
