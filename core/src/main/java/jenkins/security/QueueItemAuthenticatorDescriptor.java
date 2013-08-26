package jenkins.security;

import hudson.DescriptorExtensionList;
import hudson.model.Descriptor;
import jenkins.model.Jenkins;

/**
 * {@link Descriptor} for {@link QueueItemAuthenticator}.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.520
 */
public abstract class QueueItemAuthenticatorDescriptor extends Descriptor<QueueItemAuthenticator> {
    // nothing defined here yet

    public static DescriptorExtensionList<QueueItemAuthenticator,QueueItemAuthenticatorDescriptor> all() {
        return Jenkins.getInstance().getDescriptorList(QueueItemAuthenticator.class);
    }
}
