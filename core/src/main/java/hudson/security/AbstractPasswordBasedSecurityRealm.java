package hudson.security;

import hudson.Util;
import jenkins.model.Jenkins;
import jenkins.security.ImpersonatingUserDetailsService2;
import jenkins.security.SecurityListener;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.Beta;
import org.springframework.security.authentication.AnonymousAuthenticationProvider;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.RememberMeAuthenticationProvider;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.dao.AbstractUserDetailsAuthenticationProvider;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

/**
 * Partial implementation of {@link SecurityRealm} for username/password based authentication.
 * This is a convenience base class if all you are trying to do is to check the given username
 * and password with the information stored in somewhere else, and you don't want to do anything
 * with Spring Security.
 *
 * <p>
 * This {@link SecurityRealm} uses the standard login form (and a few other optional mechanisms like BASIC auth)
 * to gather the username/password information. Subtypes are responsible for authenticating this information.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.317
 */
public abstract class AbstractPasswordBasedSecurityRealm extends SecurityRealm {
    @Override
    public SecurityComponents createSecurityComponents() {
        // this does all the hard work.
        Authenticator authenticator = new Authenticator();
        // these providers apply everywhere
        RememberMeAuthenticationProvider rmap = new RememberMeAuthenticationProvider(Jenkins.get().getSecretKey());
        // this doesn't mean we allow anonymous access.
        // we just authenticate2 anonymous users as such,
        // so that later authorization can reject them if so configured
        AnonymousAuthenticationProvider aap = new AnonymousAuthenticationProvider("anonymous");
        AuthenticationManager authenticationManager = new ProviderManager(authenticator, rmap, aap);
        return new SecurityComponents(
                authenticationManager,
                new ImpersonatingUserDetailsService2(this::loadUserByUsername2));
    }

    /**
     * Authenticate a login attempt.
     * This method is the heart of a {@link AbstractPasswordBasedSecurityRealm}.
     *
     * <p>
     * If the user name and the password pair matches, retrieve the information about this user and
     * return it as a {@link UserDetails} object. {@link org.springframework.security.core.userdetails.User} is a convenient
     * implementation to use, but if your backend offers additional data, you may want to use your own subtype
     * so that the rest of Hudson can use those additional information (such as e-mail address --- see
     * MailAddressResolver.)
     *
     * <p>
     * Properties like {@link UserDetails#getPassword()} make no sense, so just return an empty value from it.
     * The only information that you need to pay real attention is {@link UserDetails#getAuthorities()}, which
     * is a list of roles/groups that the user is in. At minimum, this must contain {@link #AUTHENTICATED_AUTHORITY}
     * (which indicates that this user is authenticated and not anonymous), but if your backend supports a notion
     * of groups, you should make sure that the authorities contain one entry per one group. This enables
     * users to control authorization based on groups.
     *
     * <p>
     * If the user name and the password pair doesn't match, throw {@link AuthenticationException} to reject the login
     * attempt.
     * @since 2.266
     */
    protected UserDetails authenticate2(String username, String password) throws AuthenticationException {
        if (Util.isOverridden(AbstractPasswordBasedSecurityRealm.class, getClass(), "authenticate", String.class, String.class)) {
            try {
                return authenticate(username, password).toSpring();
            } catch (org.acegisecurity.AcegiSecurityException x) {
                throw x.toSpring();
            }
        } else {
            throw new AbstractMethodError("Implement authenticate2");
        }
    }

    /**
     * A public alias of @{link {@link #authenticate2(String, String)}.
     * @since 2.444
     */
    @Restricted(Beta.class)
    public final UserDetails authenticateByPassword(String username, String password) throws AuthenticationException {
        return authenticate2(username, password);
    }

    /**
     * @deprecated use {@link #authenticate2}
     */
    @Deprecated
    protected org.acegisecurity.userdetails.UserDetails authenticate(String username, String password) throws org.acegisecurity.AuthenticationException {
        try {
            return org.acegisecurity.userdetails.UserDetails.fromSpring(authenticate2(username, password));
        } catch (AuthenticationException x) {
            throw org.acegisecurity.AuthenticationException.fromSpring(x);
        }
    }

    private UserDetails doAuthenticate(String username, String password) throws AuthenticationException {
        try {
            UserDetails user = authenticate2(username, password);
            SecurityListener.fireAuthenticated2(user);
            return user;
        } catch (AuthenticationException x) {
            SecurityListener.fireFailedToAuthenticate(username);
            throw x;
        }
    }

    /**
     * Retrieves information about an user by its name.
     *
     * <p>
     * This method is used, for example, to validate if the given token is a valid user name when the user is configuring an ACL.
     * This is an optional method that improves the user experience. If your backend doesn't support
     * a query like this, just always throw {@link UsernameNotFoundException}.
     */
    @Override
    public UserDetails loadUserByUsername2(String username) throws UsernameNotFoundException {
        if (Util.isOverridden(AbstractPasswordBasedSecurityRealm.class, getClass(), "loadUserByUsername", String.class)) {
            try {
                return loadUserByUsername(username).toSpring();
            } catch (org.acegisecurity.AcegiSecurityException x) {
                throw x.toSpring();
            } catch (org.springframework.dao.DataAccessException x) {
                throw x.toSpring();
            }
        } else {
            throw new AbstractMethodError("Implement loadUserByUsername2");
        }
    }

    /**
     * @deprecated use {@link #loadUserByUsername2}
     */
    @Deprecated
    @Override
    public org.acegisecurity.userdetails.UserDetails loadUserByUsername(String username) throws org.acegisecurity.userdetails.UsernameNotFoundException, org.springframework.dao.DataAccessException {
        try {
            return org.acegisecurity.userdetails.UserDetails.fromSpring(loadUserByUsername2(username));
        } catch (AuthenticationException x) {
            throw org.acegisecurity.AuthenticationException.fromSpring(x);
        }
    }

    /**
     * Retrieves information about a group by its name.
     *
     * This method is the group version of the {@link #loadUserByUsername2(String)}.
     */
    @Override
    public GroupDetails loadGroupByGroupname2(String groupname, boolean fetchMembers) throws UsernameNotFoundException {
        if (Util.isOverridden(AbstractPasswordBasedSecurityRealm.class, getClass(), "loadGroupByGroupname", String.class)) {
            try {
                return loadGroupByGroupname(groupname);
            } catch (org.acegisecurity.AcegiSecurityException x) {
                throw x.toSpring();
            } catch (org.springframework.dao.DataAccessException x) {
                throw x.toSpring();
            }
        } else {
            throw new AbstractMethodError("Implement loadGroupByGroupname2");
        }
    }

    /**
     * @deprecated use {@link #loadGroupByGroupname2}
     */
    @Deprecated
    @Override
    public GroupDetails loadGroupByGroupname(String groupname) throws org.acegisecurity.userdetails.UsernameNotFoundException, org.springframework.dao.DataAccessException {
        try {
            return loadGroupByGroupname2(groupname, false);
        } catch (AuthenticationException x) {
            throw org.acegisecurity.AuthenticationException.fromSpring(x);
        }
    }

    class Authenticator extends AbstractUserDetailsAuthenticationProvider {
        @Override
        protected void additionalAuthenticationChecks(UserDetails userDetails, UsernamePasswordAuthenticationToken authentication) throws AuthenticationException {
            // Authentication is done in the retrieveUser method. Note that this method being a no-op is only safe
            // because we use Spring Security's default NullUserCache. If caching was enabled, it would be possible to
            // log in as any cached user with any password unless we updated this method to check the provided
            // authentication as recommended in the superclass method's documentation, so be careful reusing this code.
        }

        @Override
        protected UserDetails retrieveUser(String username, UsernamePasswordAuthenticationToken authentication) throws AuthenticationException {
            return doAuthenticate(username, authentication.getCredentials().toString());
        }
    }

}
