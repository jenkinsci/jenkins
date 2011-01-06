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
 * Your implementation would do protocol specific thing to verify that the user indeed owns the claimed identifier,
 * create a {@link FederatedIdentity} instance,
 * then call {@link FederatedIdentity#addToCurrentUser()} to record such association.
 *
 * <li>
 * In the login page, instead of entering the username and password, the user opts for authenticating
 * via other services. Think of OpenID, OAuth, your corporate SSO service, etc.
 * The user proves (by your protocol specific way) that they own some identifier, then
 * create a {@link FederatedIdentity} instance, and invoke {@link FederatedIdentity#signin()} to sign in that user.
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
     * Identity information as obtained from {@link FederatedLoginService}.
     */
    public abstract class FederatedIdentity {
        /**
         * Gets the string representation of the identity in the form that makes sense to the enclosing
         * {@link FederatedLoginService}, such as full OpenID URL.
         *
         * @return must not be null.
         */
        public abstract String getIdentifier();

        /**
         * Gets a short ID of this user, as a suitable candidate for {@link User#getId()}.
         * This should be Unix username like token.
         *
         * @return null if this information is not available.
         */
        public abstract String getNickname();

        /**
         * Gets a human readable full name of this user. Maps to {@link User#getDisplayName()}
         *
         * @return null if this information is not available.
         */
        public abstract String getFullName();

        /**
         * Gets the e-mail address of this user, like "abc@def.com"
         *
         * @return null if this information is not available.
         */
        public abstract String getEmailAddress();

        /**
         * Locates the user who owns this identifier.
         */
        public final User locateUser() {
            Class<? extends FederatedLoginServiceUserProperty> pt = getUserPropertyClass();
            String id = getIdentifier();

            for (User u : User.getAll()) {
                if (u.getProperty(pt).has(id))
                    return u;
            }
            return null;
        }

        /**
         * Call this method to authenticate the user when you confirmed (via your protocol specific work) that
         * the current HTTP request indeed owns this identifier.
         *
         * <p>
         * This method will locate the user who owns this identifier, associate the credential with
         * the current session. IOW, it signs in the user.
         *
         * @throws UnclaimedIdentityException
         *      If this identifier is not claimed by anyone.
         */
        public final void signin() throws UnclaimedIdentityException {
            User u = locateUser();
            if (u!=null) {
                // login as this user
                UserDetails d = Hudson.getInstance().getSecurityRealm().loadUserByUsername(u.getId());

                UsernamePasswordAuthenticationToken token = new UsernamePasswordAuthenticationToken(d,"",d.getAuthorities());
                token.setDetails(d);
                SecurityContextHolder.getContext().setAuthentication(token);
            } else {
                // unassociated identity
                throw new UnclaimedIdentityException(this);
            }
        }

        /**
         * Your implementation will call this method to add this identifier to the current user
         * of an already authenticated session.
         *
         * <p>
         * This method will record the identifier in {@link FederatedLoginServiceUserProperty} so that
         * in the future the user can login to Hudson with the identifier.
         */
        public void addToCurrentUser() throws IOException {
            User u = User.current();
            if (u==null)    throw new IllegalStateException("Current request is unauthenticated");

            FederatedLoginServiceUserProperty p = u.getProperty(getUserPropertyClass());
            if (p==null) {
                p = (FederatedLoginServiceUserProperty) UserProperty.all().find(getUserPropertyClass()).newInstance(u);
                u.addProperty(p);
            }
            p.addIdentifier(getIdentifier());
        }

        @Override
        public String toString() {
            return getIdentifier();
        }
    }

    public static class UnclaimedIdentityException extends Exception {
        public final FederatedIdentity identity;
        public UnclaimedIdentityException(FederatedIdentity identity) {
            this.identity = identity;
        }
    }

    public static ExtensionList<FederatedLoginService> all() {
        return Hudson.getInstance().getExtensionList(FederatedLoginService.class);
    }
}
