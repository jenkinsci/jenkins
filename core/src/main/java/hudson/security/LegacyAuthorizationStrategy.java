package hudson.security;

import hudson.model.Descriptor;
import org.acegisecurity.acls.sid.PrincipalSid;

/**
 * {@link AuthorizationStrategy} implementation that emulates the legacy behavior.
 * @author Kohsuke Kawaguchi
 */
public final class LegacyAuthorizationStrategy extends AuthorizationStrategy {
    private static final ACL LEGACY_ACL = new SparseACL(null) {{
        add(EVERYONE,Permission.READ,true);
        add(new PrincipalSid("admin"),Permission.FULL_CONTROL,true);
    }};

    public ACL getRootACL() {
        return LEGACY_ACL;
    }

    public Descriptor<AuthorizationStrategy> getDescriptor() {
        return DESCRIPTOR;
    }

    public static final Descriptor<AuthorizationStrategy> DESCRIPTOR = new DescriptorImpl();

    public static final class DescriptorImpl extends Descriptor<AuthorizationStrategy> {
        private DescriptorImpl() {
            super(LegacyAuthorizationStrategy.class);
        }

        public String getDisplayName() {
            return Messages.LegacyAuthorizationStrategy_DisplayName();
        }

        public String getHelpFile() {
            return "/help/security/legacy-auth-strategy.html";
        }
    }

    static {
        LIST.add(DESCRIPTOR);
    }

}
