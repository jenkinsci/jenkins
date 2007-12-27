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

    public static final Descriptor<AuthorizationStrategy> DESCRIPTOR = new Descriptor<AuthorizationStrategy>(LegacyAuthorizationStrategy.class) {
        public String getDisplayName() {
            return "Legacy mode";
        }
    };

    static {
        LIST.add(DESCRIPTOR);
    }
}
