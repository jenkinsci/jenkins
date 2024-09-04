package jenkins.security;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.Descriptor.FormException;
import hudson.model.User;
import hudson.model.UserProperty;
import hudson.model.UserPropertyDescriptor;
import hudson.model.userproperty.UserPropertyCategory;
import hudson.security.SecurityRealm;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import org.jenkinsci.Symbol;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.StaplerRequest2;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * Remembers the set of {@link GrantedAuthority}s that was obtained the last time the user has logged in.
 *
 * This allows us to implement {@link User#impersonate2()} with proper set of groups.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.556
 * @see ImpersonatingUserDetailsService
 */
public class LastGrantedAuthoritiesProperty extends UserProperty {
    private volatile String[] roles;
    private long timestamp;

    /**
     * Stick to the same object since there's no UI for this.
     */
    @Override
    public UserProperty reconfigure(StaplerRequest2 req, JSONObject form) throws FormException {
        req.bindJSON(this, form);
        return this;
    }

    /**
     * @since 2.266
     */
    public Collection<? extends GrantedAuthority> getAuthorities2() {
        String[] roles = this.roles;    // capture to a variable for immutability

        if (roles == null) {
            return Set.of(SecurityRealm.AUTHENTICATED_AUTHORITY2);
        }

        String authenticatedRole = SecurityRealm.AUTHENTICATED_AUTHORITY2.getAuthority();
        List<GrantedAuthority> grantedAuthorities = new ArrayList<>(roles.length + 1);
        grantedAuthorities.add(new SimpleGrantedAuthority(authenticatedRole));

        for (String role : roles) {
            // to avoid having twice that role
            if (!authenticatedRole.equals(role)) {
                grantedAuthorities.add(new SimpleGrantedAuthority(role));
            }
        }

        return grantedAuthorities;
    }

    /**
     * @deprecated use {@link #getAuthorities2}
     */
    @Deprecated
    public org.acegisecurity.GrantedAuthority[] getAuthorities() {
        return org.acegisecurity.GrantedAuthority.fromSpring(getAuthorities2());
    }

    /**
     * Persist the information with the new {@link UserDetails}.
     */
    @Restricted(NoExternalUse.class)
    public void update(@NonNull Authentication auth) throws IOException {
        List<String> roles = new ArrayList<>();
        for (GrantedAuthority ga : auth.getAuthorities()) {
            roles.add(ga.getAuthority());
        }
        String[] a = roles.toArray(new String[0]);
        if (!Arrays.equals(this.roles, a)) {
            this.roles = a;
            this.timestamp = System.currentTimeMillis();
            user.save();
        }
    }

    /**
     * Removes the recorded information
     */
    public void invalidate() throws IOException {
        if (roles != null) {
            roles = null;
            timestamp = System.currentTimeMillis();
            user.save();
        }
    }

    /**
     * Listen to the login success/failure event to persist {@link GrantedAuthority}s properly.
     */
    @Extension
    public static class SecurityListenerImpl extends SecurityListener {
        @Override
        protected void loggedIn(@NonNull String username) {
            try {
                // user should have been created but may not have been saved for some realms
                // but as this is a callback of a successful login we can safely create the user.
                User u = User.getById(username, true);
                LastGrantedAuthoritiesProperty o = u.getProperty(LastGrantedAuthoritiesProperty.class);
                if (o == null)
                    u.addProperty(o = new LastGrantedAuthoritiesProperty());
                Authentication a = Jenkins.getAuthentication2();
                if (a != null && a.getName().equals(username))
                    o.update(a);    // just for defensive sanity checking
            } catch (IOException e) {
                LOGGER.log(Level.WARNING, "Failed to record granted authorities", e);
            }
        }

        @Override
        protected void failedToLogIn(@NonNull String username) {
            // while this initially seemed like a good idea to avoid allowing wrong impersonation for too long,
            // doing this means a malicious user can break the impersonation capability
            // just by failing to login. See ApiTokenFilter that does the following, which seems better:
            /*
                try {
                    Jenkins.get().getSecurityRealm().loadUserByUsername(username);
                } catch (UserMayOrMayNotExistException x) {
                    // OK, give them the benefit of the doubt.
                } catch (UsernameNotFoundException x) {
                    // Not/no longer a user; deny the API token. (But do not leak the information that this happened.)
                    chain.doFilter(request, response);
                    return;
                } catch (DataAccessException x) {
                    throw new ServletException(x);
                }
             */

//            try {
//                User u = User.getById(username,false);
//                LastGrantedAuthoritiesProperty o = u.getProperty(LastGrantedAuthoritiesProperty.class);
//                if (o!=null)
//                    o.invalidate();
//            } catch (IOException e) {
//                LOGGER.log(Level.WARNING, "Failed to record granted authorities",e);
//            }
        }
    }

    @Extension @Symbol("lastGrantedAuthorities")
    public static final class DescriptorImpl extends UserPropertyDescriptor {
        @Override
        public boolean isEnabled() {
            return false;
        }

        @Override
        public UserProperty newInstance(User user) {
            return null;
        }

        @Override
        public @NonNull UserPropertyCategory getUserPropertyCategory() {
            return UserPropertyCategory.get(UserPropertyCategory.Invisible.class);
        }
    }

    private static final Logger LOGGER = Logger.getLogger(LastGrantedAuthoritiesProperty.class.getName());
}
