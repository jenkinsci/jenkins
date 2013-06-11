package jenkins.security;

import hudson.Extension;
import hudson.model.Executor;
import hudson.model.Queue.Executable;
import hudson.security.ACL;
import org.acegisecurity.Authentication;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * @author Kohsuke Kawaguchi
 */
public class DefaultExecutorAuthenticator extends ExecutorAuthenticator {
    @DataBoundConstructor
    public DefaultExecutorAuthenticator() {
    }

    @Override
    public Authentication authenticate(Executor executor, Executable executable) {
        return ACL.SYSTEM;
    }

    @Extension
    public static class DescriptorImpl extends ExecutorAuthenticatorDescriptor {
        @Override
        public String getDisplayName() {
            return "Default"; // TODO: fix
        }
    }
}
