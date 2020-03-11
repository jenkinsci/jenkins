package jenkins.security;

import hudson.model.User;
import hudson.security.SecurityRealm;
import hudson.security.UserMayOrMayNotExistException;
import org.acegisecurity.userdetails.UserDetails;
import org.acegisecurity.userdetails.UserDetailsService;
import org.acegisecurity.userdetails.UsernameNotFoundException;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.springframework.dao.DataAccessException;

import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * {@link UserDetailsService} for those {@link SecurityRealm}
 * that doesn't allow query of other users.
 *
 * When the backend responds with {@link UserMayOrMayNotExistException}, we try to replace that with
 * information stored in {@link LastGrantedAuthoritiesProperty}.
 *
 * @author Kohsuke Kawaguchi
 * @deprecated use {@link ImpersonatingUserDetailsService2}
 */
@Deprecated
public class ImpersonatingUserDetailsService implements UserDetailsService {
    
    private static final Logger LOGGER = Logger.getLogger(ImpersonatingUserDetailsService.class.getName());
    
    private final UserDetailsService base;

    @Restricted(NoExternalUse.class)
    private static boolean DISABLE_CACHE_FOR_IMPERSONATION = 
            Boolean.getBoolean(ImpersonatingUserDetailsService.class.getName() + ".disableCacheForImpersonation");

    public ImpersonatingUserDetailsService(UserDetailsService base) {
        this.base = base;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException, DataAccessException {
        UserDetails userDetails;
        if (!DISABLE_CACHE_FOR_IMPERSONATION) {
            try {
                userDetails = UserDetailsCache.get().loadUserByUsername(username);
                return userDetails;
            } catch (ExecutionException ex) {
                LOGGER.log(Level.INFO, "Failure to retrieve {0} from cache", username);
                userDetails = loadUserByUsernameFromBase(username);
            } catch (DataAccessException | UserMayOrMayNotExistException e) {
                // Those Exception originates from the SecurityRealm and tell us that the User may or may not exists
                userDetails = attemptToImpersonate(username, e);
            }
        } else {
            userDetails = loadUserByUsernameFromBase(username);
        }
        return userDetails;
    }

    protected UserDetails loadUserByUsernameFromBase(String username) throws UsernameNotFoundException, DataAccessException {
        UserDetails userDetails;
        try {
            userDetails = base.loadUserByUsername(username);
        } catch (UserMayOrMayNotExistException | DataAccessException e) {
            userDetails = attemptToImpersonate(username, e);
        }
        return userDetails;
    }

    protected UserDetails attemptToImpersonate(String username, RuntimeException e) {
        // this backend cannot tell if the user name exists or not. so substitute by what we know
        User u = User.getById(username, false);
        if (u!=null) {
            LastGrantedAuthoritiesProperty p = u.getProperty(LastGrantedAuthoritiesProperty.class);
            if (p!=null)
                return new org.acegisecurity.userdetails.User(username,"",true,true,true,true,
                        p.getAuthorities());
        }

        throw e;
    }
}
