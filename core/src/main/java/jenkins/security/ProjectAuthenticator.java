package jenkins.security;

import hudson.ExtensionPoint;
import hudson.model.AbstractBuild;
import hudson.model.AbstractDescribableImpl;
import hudson.model.AbstractProject;
import hudson.security.ACL;
import org.acegisecurity.Authentication;

import javax.annotation.CheckForNull;

/**
 * Extension point to run {@link AbstractBuild}s under a specific identity for better access control.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.520
 * @see ProjectAuthenticatorConfiguration
 * @see AbstractProject#getIdentity()
 */
public abstract class ProjectAuthenticator extends AbstractDescribableImpl<ProjectAuthenticator> implements ExtensionPoint {
    /**
     * Determines the identity in which the build will run as.
     *
     * @param project
     *      The project to be built.
     *
     * @return
     *      returning non-null will determine the identity. If null is returned, the next
     *      configured {@link ProjectAuthenticator} will be given a chance to authenticate
     *      the executor. If everything fails, fall back to the historical behaviour of
     *      {@link ACL#SYSTEM}.
     */
    public abstract @CheckForNull Authentication authenticate(AbstractProject<?,?> project);

    @Override
    public ProjectAuthenticatorDescriptor getDescriptor() {
        return (ProjectAuthenticatorDescriptor)super.getDescriptor();
    }
}
