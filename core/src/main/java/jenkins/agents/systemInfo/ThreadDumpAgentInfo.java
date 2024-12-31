package jenkins.agents.systemInfo;

import hudson.Extension;
import org.jenkinsci.Symbol;

/**
 * @author Kohsuke Kawaguchi
 */
@Extension(ordinal = 1) @Symbol("threadDump")
public class ThreadDumpAgentInfo extends AgentSystemInfo {
    @Override
    public String getDisplayName() {
        return Messages.ThreadDumpAgentInfo_DisplayName();
    }
}
