package jenkins.security;

import hudson.Extension;
import hudson.model.AbstractProject;
import hudson.security.ACL;
import org.acegisecurity.Authentication;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * @author Kohsuke Kawaguchi
 */
public class DefaultProjectAuthenticator extends ProjectAuthenticator {
    @DataBoundConstructor
    public DefaultProjectAuthenticator() {
    }

    @Override
    public Authentication authenticate(AbstractProject<?, ?> project) {
        return ACL.SYSTEM;
    }

    @Extension
    public static class DescriptorImpl extends ProjectAuthenticatorDescriptor {
        @Override
        public String getDisplayName() {
            return "Default"; // TODO: fix
        }
    }
}
