package jenkins.slaves.systemInfo;

import hudson.Extension;
import org.jenkinsci.Symbol;

/**
 * @author Kohsuke Kawaguchi
 */
@Extension(ordinal=1) @Symbol("threadDump")
public class ThreadDumpSlaveInfo extends SlaveSystemInfo {
    @Override
    public String getDisplayName() {
        return Messages.ThreadDumpSlaveInfo_DisplayName();
    }
}
