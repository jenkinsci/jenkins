package hudson.security;

import hudson.model.Descriptor;
import org.acegisecurity.acls.sid.GrantedAuthoritySid;
import org.kohsuke.stapler.StaplerRequest;
import net.sf.json.JSONObject;

import java.util.Collection;
import java.util.Collections;

/**
 * {@link AuthorizationStrategy} implementation that emulates the legacy behavior.
 * @author Kohsuke Kawaguchi
 */
public final class LegacyAuthorizationStrategy extends AuthorizationStrategy {
    private static final ACL LEGACY_ACL = new SparseACL(null) {{
        add(EVERYONE,Permission.READ,true);
        add(new GrantedAuthoritySid("admin"),Permission.FULL_CONTROL,true);
    }};

    public ACL getRootACL() {
        return LEGACY_ACL;
    }

    public Collection<String> getGroups() {
        return Collections.singleton("admin");
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

        public LegacyAuthorizationStrategy newInstance(StaplerRequest req, JSONObject formData) throws FormException {
            return new LegacyAuthorizationStrategy();
        }
    }

    static {
        LIST.add(DESCRIPTOR);
    }

}
