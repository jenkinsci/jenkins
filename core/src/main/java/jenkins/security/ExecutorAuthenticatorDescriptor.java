package jenkins.security;

import hudson.DescriptorExtensionList;
import hudson.model.Descriptor;
import jenkins.model.Jenkins;

/**
 * {@link Descriptor} for {@link ExecutorAuthenticator}.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.520
 */
public abstract class ExecutorAuthenticatorDescriptor extends Descriptor<ExecutorAuthenticator> {
    // nothing defined here yet

    public static DescriptorExtensionList<ExecutorAuthenticator,ExecutorAuthenticatorDescriptor> all() {
        return Jenkins.getInstance().getDescriptorList(ExecutorAuthenticator.class);
    }
}
