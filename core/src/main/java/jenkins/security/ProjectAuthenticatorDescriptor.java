package jenkins.security;

import hudson.DescriptorExtensionList;
import hudson.model.Descriptor;
import jenkins.model.Jenkins;

/**
 * {@link Descriptor} for {@link ProjectAuthenticator}.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.520
 */
public abstract class ProjectAuthenticatorDescriptor extends Descriptor<ProjectAuthenticator> {
    // nothing defined here yet

    public static DescriptorExtensionList<ProjectAuthenticator,ProjectAuthenticatorDescriptor> all() {
        return Jenkins.getInstance().getDescriptorList(ProjectAuthenticator.class);
    }
}
