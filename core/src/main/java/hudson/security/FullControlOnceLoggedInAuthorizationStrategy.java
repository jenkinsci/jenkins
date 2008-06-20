package hudson.security;

import hudson.model.Descriptor;

import java.util.Collections;
import java.util.List;

import net.sf.json.JSONObject;

import org.kohsuke.stapler.StaplerRequest;

/**
 * {@link AuthorizationStrategy} that grants full-control to authenticated user
 * (other than anonymous users.)
 *
 * @author Kohsuke Kawaguchi
 */
public class FullControlOnceLoggedInAuthorizationStrategy extends AuthorizationStrategy {
    @Override
    public ACL getRootACL() {
        return THE_ACL;
    }

    public List<String> getGroups() {
        return Collections.emptyList();
    }

    private static final SparseACL THE_ACL = new SparseACL(null);

    static {
        THE_ACL.add(ACL.EVERYONE,Permission.FULL_CONTROL,true);
        THE_ACL.add(ACL.ANONYMOUS,Permission.FULL_CONTROL,false);
        THE_ACL.add(ACL.ANONYMOUS,Permission.READ,true);
    }

    public Descriptor<AuthorizationStrategy> getDescriptor() {
        return DESCRIPTOR;
    }

    public static final Descriptor<AuthorizationStrategy> DESCRIPTOR = new Descriptor<AuthorizationStrategy>(FullControlOnceLoggedInAuthorizationStrategy.class) {
        public String getDisplayName() {
            return Messages.FullControlOnceLoggedInAuthorizationStrategy_DisplayName();
        }

        public AuthorizationStrategy newInstance(StaplerRequest req, JSONObject formData) throws FormException {
            return new FullControlOnceLoggedInAuthorizationStrategy();
        }

        public String getHelpFile() {
            return "/help/security/full-control-once-logged-in.html";
        }
    };

    static {
        LIST.add(DESCRIPTOR);
    }
}
