package jenkins.slaves.systemInfo;

import hudson.Extension;
import jenkins.slaves.systemInfo.Messages;
import jenkins.slaves.systemInfo.Messages;
import org.jenkinsci.Symbol;

/**
 * @author Kohsuke Kawaguchi
 */
@Extension(ordinal=0) @Symbol("classLoaderStatistics")
public class ClassLoaderStatisticsSlaveInfo extends SlaveSystemInfo {
    @Override
    public String getDisplayName() {
        return Messages.ClassLoaderStatisticsSlaveInfo_DisplayName();
    }
}
