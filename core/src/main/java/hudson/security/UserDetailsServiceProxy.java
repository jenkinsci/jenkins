package hudson.security;

import org.acegisecurity.userdetails.UserDetails;
import org.acegisecurity.userdetails.UserDetailsService;
import org.acegisecurity.userdetails.UsernameNotFoundException;
import org.springframework.dao.DataAccessException;

/**
 * {@link UserDetailsService} proxy that delegates to another instance.
 * 
 * @author Kohsuke Kawaguchi
 */
public class UserDetailsServiceProxy implements UserDetailsService {
    private volatile UserDetailsService delegate;

    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException, DataAccessException {
        UserDetailsService uds = delegate;  // fix the reference for concurrency support

        if(uds ==null)
            throw new UserMayOrMayNotExistException(Messages.UserDetailsServiceProxy_UnableToQuery(username));
        return uds.loadUserByUsername(username);
    }

    public void setDelegate(UserDetailsService core) {
        this.delegate = core;
    }

}
