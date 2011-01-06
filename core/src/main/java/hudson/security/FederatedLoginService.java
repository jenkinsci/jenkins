package hudson.security;

import hudson.ExtensionList;
import hudson.ExtensionPoint;
import hudson.model.Hudson;
import hudson.model.User;
import hudson.model.UserProperty;
import org.acegisecurity.context.SecurityContextHolder;
import org.acegisecurity.providers.UsernamePasswordAuthenticationToken;
import org.acegisecurity.userdetails.UserDetails;

import java.io.IOException;

/**
 * Abstraction for a login mechanism through external authenticator/identity provider
 * (instead of username/password.)
 *
 * <p>
 * This extension point adds additional login mechanism for {@link SecurityRealm}s that
 * authenticate the user via username/password (which typically extends from {@link AbstractPasswordBasedSecurityRealm}.)
 * The intended use case is protocols like OpenID, OAuth, and other SSO-like services.
 *
 * <p>
 * The basic abstraction is that:
 *
 * <ul>
 * <li>
 * The user can have (possibly multiple, possibly zero) opaque strings to their {@linkplain User} object.
 * Such opaque strings are called "identifiers."
 * Think of them as OpenID URLs, twitter account names, etc.
 * Identifiers are only comparable within the same {@link FederatedLoginService} implementation.
 *
 * <li>
 * After getting authenticated by some means, the user can add additional identifiers to their account.
 * Your implementation would do protocol specific thing to verify that the user indeed owns those identifiers,
 * then call {@link #onAssociated(String)} to record such association.
 *
 * <li>
 * In the login page, instead of entering the username and password, the user opts for authenticating
 * via other services. Think of OpenID, OAuth, your corporate SSO service, etc.
 * The user proves (by your protocol specific way) that they own some identifier, then you call
 * {@link #onIdentified(String)} to sign in that user.
 *
 * </ul>
 *
 *
 * <h2>Views</h2>
 * <dl>
 * <dt>loginFragment.jelly
 * <dd>
 * Injected into the login form page, after the default "login" button but before
 * the "create account" link. Use this to generate a button or a link so that the user
 * can initiate login via your federated login service.
 * </dl>
 *
 * <h2>URL Binding</h2>
 * <p>
 * Each {@link FederatedLoginService} is exposed to the URL space via {@link Hudson#getFederatedLoginService(String)}.
 * So for example if your {@linkplain #getUrlName() url name} is "openid", this object gets
 * "/federatedLoginService/openid" as the URL.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.393
 */
public abstract class FederatedLoginService implements ExtensionPoint {
    /**
     * Returns the url name that determines where this {@link FederatedLoginService} is mapped to in the URL space.
     *
     * <p>
     * The object is bound to /federatedLoginService/URLNAME/. The url name needs to be unique among all
     * {@link FederatedLoginService}s.
     */
    public abstract String getUrlName();

    /**
     * Returns your implementation of {@link FederatedLoginServiceUserProperty} that stores
     * opaque identifiers.
     */
    public abstract Class<? extends FederatedLoginServiceUserProperty> getUserPropertyClass();

    /**
     * Locates the user who owns a particular identifier.
     */
    public User findUserByIdentifier(String identifier) {
        Class<? extends FederatedLoginServiceUserProperty> pt = getUserPropertyClass();
        for (User u : User.getAll()) {
            if (u.getProperty(pt).has(identifier))
                return u;
        }
        return null;
    }

    /**
     * Your implementation will call this method to authenticate the user when you confirmed that
     * the current HTTP request owns the given identifier.
     *
     * <p>
     * This method will locate the user who owns this identifier, associate the credential with
     * the current session, and returns true. IOW, it signs in the user.
     *
     * @return
     *      true if the user who owns the identifier is discovered and the user is signed in.
     *      false if the given identifier is not claimed by anyone.
     */
    protected boolean onIdentified(String identifier) {
        User u = findUserByIdentifier(identifier);
        if (u!=null) {
            // login as this user
            UserDetails d = Hudson.getInstance().getSecurityRealm().loadUserByUsername(u.getId());

            UsernamePasswordAuthenticationToken token = new UsernamePasswordAuthenticationToken(d,"",d.getAuthorities());
            token.setDetails(d);
            SecurityContextHolder.getContext().setAuthentication(token);
            return true;
        } else {
            // unassociated identity
            return false;
        }
    }

    /**
     * Your implementation will call this method when you confirmed that
     * the current user owns the given identifier.
     * <p>
     * This method will record the identifier in {@link FederatedLoginServiceUserProperty} so that
     * in the future the user can login to Hudson with the identifier.
     */
    protected void onAssociated(String identifier) throws IOException {
        User u = User.current();
        if (u==null)    throw new IllegalStateException("Current request is unauthenticated");

        FederatedLoginServiceUserProperty p = u.getProperty(getUserPropertyClass());
        if (p==null) {
            p = (FederatedLoginServiceUserProperty) UserProperty.all().find(getUserPropertyClass()).newInstance(u);
            u.addProperty(p);
        }
        p.addIdentifier(identifier);
    }

    public static ExtensionList<FederatedLoginService> all() {
        return Hudson.getInstance().getExtensionList(FederatedLoginService.class);
    }
}
