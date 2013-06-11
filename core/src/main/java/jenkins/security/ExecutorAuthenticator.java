package jenkins.security;

import hudson.ExtensionPoint;
import hudson.model.AbstractBuild;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Executor;
import hudson.model.Queue;
import org.acegisecurity.Authentication;

/**
 * Extension point to run builds under a specific identity for better access control.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.520
 */
public abstract class ExecutorAuthenticator extends AbstractDescribableImpl<ExecutorAuthenticator> implements ExtensionPoint {
    /**
     * Determines the identity in which the build will run as.
     *
     * @param executor
     *      The executor that's going to run the executable. Never null.
     *      Provided as an input to the decision making.
     * @param executable
     *      The build that's going to run. {@link AbstractBuild} is a common subtype of
     *      this interface.
     *      TODO: check that actions submitted to the queue are visible on this object.
     *
     * @return
     *      returning non-null will determine the identity. If null is returned, the next
     *      configured {@link ExecutorAuthenticator} will be given a chance to authenticate
     *      the executor.
     */
    public abstract Authentication authenticate(Executor executor, Queue.Executable executable);

    @Override
    public ExecutorAuthenticatorDescriptor getDescriptor() {
        return (ExecutorAuthenticatorDescriptor)super.getDescriptor();
    }
}
