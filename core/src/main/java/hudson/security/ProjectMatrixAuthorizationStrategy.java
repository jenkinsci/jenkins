package hudson.security;

import hudson.model.AbstractProject;
import hudson.model.Descriptor;
import hudson.model.Jobs;

/**
 * {@link GlobalMatrixAuthorizationStrategy} plus per-project ACL.
 *
 * <p>
 * Per-project ACL is stored in {@link AuthorizationMatrixProperty}.
 *
 * @author Kohsuke Kawaguchi
 */
public class ProjectMatrixAuthorizationStrategy extends GlobalMatrixAuthorizationStrategy {
    @Override
    public ACL getACL(AbstractProject<?,?> project) {
        AuthorizationMatrixProperty amp = project.getProperty(AuthorizationMatrixProperty.class);
        if (amp != null && amp.isUseProjectSecurity()) {
            return amp.getACL();
        } else {
            return getRootACL();
        }
    }

    public Descriptor<AuthorizationStrategy> getDescriptor() {
        return DESCRIPTOR;
    }

    public static final Descriptor<AuthorizationStrategy> DESCRIPTOR = new DescriptorImpl(ProjectMatrixAuthorizationStrategy.class) {
        @Override
        protected GlobalMatrixAuthorizationStrategy create() {
            return new ProjectMatrixAuthorizationStrategy();
        }

        @Override
        public String getDisplayName() {
            return Messages.ProjectMatrixAuthorizationStrategy_DisplayName();
        }
    };

    static {
        LIST.add(DESCRIPTOR);
        Jobs.PROPERTIES.add(AuthorizationMatrixProperty.DESCRIPTOR);
    }
}

