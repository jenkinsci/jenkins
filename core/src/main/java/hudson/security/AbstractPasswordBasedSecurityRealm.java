package hudson.security;

import groovy.lang.Binding;
import hudson.util.spring.BeanBuilder;
import jenkins.model.Jenkins;
import jenkins.security.ImpersonatingUserDetailsService;
import jenkins.security.SecurityListener;
import org.acegisecurity.AuthenticationException;
import org.acegisecurity.AuthenticationManager;
import org.acegisecurity.providers.UsernamePasswordAuthenticationToken;
import org.acegisecurity.providers.dao.AbstractUserDetailsAuthenticationProvider;
import org.acegisecurity.userdetails.UserDetails;
import org.acegisecurity.userdetails.UserDetailsService;
import org.acegisecurity.userdetails.UsernameNotFoundException;
import org.springframework.dao.DataAccessException;
import org.springframework.web.context.WebApplicationContext;

/**
 * Partial implementation of {@link SecurityRealm} for username/password based authentication.
 * This is a convenience base class if all you are trying to do is to check the given username
 * and password with the information stored in somewhere else, and you don't want to do anything
 * with Acegi.
 *
 * <p>
 * This {@link SecurityRealm} uses the standard login form (and a few other optional mechanisms like BASIC auth)
 * to gather the username/password information. Subtypes are responsible for authenticating this information.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.317
 */
public abstract class AbstractPasswordBasedSecurityRealm extends SecurityRealm implements UserDetailsService {
    @Override
    public SecurityComponents createSecurityComponents() {
        Binding binding = new Binding();
        binding.setVariable("authenticator", new Authenticator());

        BeanBuilder builder = new BeanBuilder();
        builder.parse(Jenkins.get().servletContext.getResourceAsStream("/WEB-INF/security/AbstractPasswordBasedSecurityRealm.groovy"),binding);
        WebApplicationContext context = builder.createApplicationContext();
        return new SecurityComponents(
                findBean(AuthenticationManager.class, context),
                new ImpersonatingUserDetailsService(this));
    }

    /**
     * Authenticate a login attempt.
     * This method is the heart of a {@link AbstractPasswordBasedSecurityRealm}.
     *
     * <p>
     * If the user name and the password pair matches, retrieve the information about this user and
     * return it as a {@link UserDetails} object. {@link org.acegisecurity.userdetails.User} is a convenient
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
     */
    protected abstract UserDetails authenticate(String username, String password) throws AuthenticationException;

    private UserDetails doAuthenticate(String username, String password) throws AuthenticationException {
        try {
            UserDetails user = authenticate(username, password);
            SecurityListener.fireAuthenticated(user);
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
    public abstract UserDetails loadUserByUsername(String username) throws UsernameNotFoundException, DataAccessException;

    /**
     * Retrieves information about a group by its name.
     *
     * This method is the group version of the {@link #loadUserByUsername(String)}.
     */
    @Override
    public abstract GroupDetails loadGroupByGroupname(String groupname) throws UsernameNotFoundException, DataAccessException;

    class Authenticator extends AbstractUserDetailsAuthenticationProvider {
        protected void additionalAuthenticationChecks(UserDetails userDetails, UsernamePasswordAuthenticationToken authentication) throws AuthenticationException {
            // authentication is assumed to be done already in the retrieveUser method
        }

        protected UserDetails retrieveUser(String username, UsernamePasswordAuthenticationToken authentication) throws AuthenticationException {
            return doAuthenticate(username,authentication.getCredentials().toString());
        }
    }

}
