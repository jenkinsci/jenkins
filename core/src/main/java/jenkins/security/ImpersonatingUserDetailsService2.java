package jenkins.security;

import hudson.model.User;
import hudson.security.SecurityRealm;
import hudson.security.UserMayOrMayNotExistException2;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

/**
 * {@link UserDetailsService} for those {@link SecurityRealm}
 * that doesn't allow query of other users.
 *
 * When the backend responds with {@link UserMayOrMayNotExistException2}, we try to replace that with
 * information stored in {@link LastGrantedAuthoritiesProperty}.
 *
 * @author Kohsuke Kawaguchi
 */
public class ImpersonatingUserDetailsService2 implements UserDetailsService {
    private final UserDetailsService base;

    public ImpersonatingUserDetailsService2(UserDetailsService base) {
        this.base = base;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        try {
            return base.loadUserByUsername(username);
        } catch (UserMayOrMayNotExistException2 e) {
            return attemptToImpersonate(username, e);
        }
    }

    protected UserDetails attemptToImpersonate(String username, RuntimeException e) {
        // this backend cannot tell if the user name exists or not. so substitute by what we know
        User u = User.getById(username, false);
        if (u != null) {
            LastGrantedAuthoritiesProperty p = u.getProperty(LastGrantedAuthoritiesProperty.class);
            if (p != null) {
                return new org.springframework.security.core.userdetails.User(username, "", true, true, true, true, p.getAuthorities2());
            }
        }
        throw e;
    }
}
