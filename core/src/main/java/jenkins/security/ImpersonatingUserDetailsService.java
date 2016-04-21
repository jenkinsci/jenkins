package jenkins.security;

import hudson.model.User;
import hudson.security.SecurityRealm;
import hudson.security.UserMayOrMayNotExistException;
import org.acegisecurity.userdetails.UserDetails;
import org.acegisecurity.userdetails.UserDetailsService;
import org.acegisecurity.userdetails.UsernameNotFoundException;
import org.springframework.dao.DataAccessException;

import static java.util.Collections.*;

/**
 * {@link UserDetailsService} for those {@link SecurityRealm}
 * that doesn't allow query of other users.
 *
 * When the backend responds with {@link UserMayOrMayNotExistException}, we try to replace that with
 * information stored in {@link LastGrantedAuthoritiesProperty}.
 *
 * @author Kohsuke Kawaguchi
 */
public class ImpersonatingUserDetailsService implements UserDetailsService {
    private final UserDetailsService base;

    public ImpersonatingUserDetailsService(UserDetailsService base) {
        this.base = base;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException, DataAccessException {
        try {
            return base.loadUserByUsername(username);
        } catch (UserMayOrMayNotExistException e) {
            return attemptToImpersonate(username, e);
        } catch (DataAccessException e) {
            return attemptToImpersonate(username, e);
        }
    }

    protected UserDetails attemptToImpersonate(String username, RuntimeException e) {
        // this backend cannot tell if the user name exists or not. so substitute by what we know
        User u = User.get(username, false, emptyMap()); // TODO why false? use getById?
        if (u!=null) {
            LastGrantedAuthoritiesProperty p = u.getProperty(LastGrantedAuthoritiesProperty.class);
            if (p!=null)
                return new org.acegisecurity.userdetails.User(username,"",true,true,true,true,
                        p.getAuthorities());
        }

        throw e;
    }
}
