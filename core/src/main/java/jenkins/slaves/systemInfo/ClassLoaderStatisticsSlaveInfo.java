package jenkins.slaves.systemInfo;

import hudson.Extension;

/**
 * @author Kohsuke Kawaguchi
 */
@Extension(ordinal=0)
public class ClassLoaderStatisticsSlaveInfo extends SlaveSystemInfo {
    @Override
    public String getDisplayName() {
        return Messages.ClassLoaderStatisticsSlaveInfo_DisplayName();
    }
}
