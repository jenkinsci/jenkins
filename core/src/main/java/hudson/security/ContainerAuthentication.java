package hudson.security;

import org.acegisecurity.Authentication;
import org.acegisecurity.GrantedAuthority;
import org.acegisecurity.GrantedAuthorityImpl;

import javax.servlet.http.HttpServletRequest;
import java.security.Principal;
import java.util.List;
import java.util.ArrayList;

import hudson.model.Hudson;

/**
 * {@link Authentication} implementation for {@link Principal}
 * given through {@link HttpServletRequest}.
 *
 * <p>
 * This is used to plug the container authentication to Acegi,
 * for backward compatibility with Hudson &lt; 1.160.
 *
 * @author Kohsuke Kawaguchi
 */
public final class ContainerAuthentication implements Authentication {
    private final HttpServletRequest request;
    private GrantedAuthority[] authorities;

    public ContainerAuthentication(HttpServletRequest request) {
        this.request = request;
    }

    public GrantedAuthority[] getAuthorities() {
        if(authorities==null) {
            // Servlet API doesn't provide a way to list up all roles the current user
            // has, so we need to ask AuthorizationStrategy what roles it is going to check against.
            List<GrantedAuthority> l = new ArrayList<GrantedAuthority>();
            for( String g : Hudson.getInstance().getAuthorizationStrategy().getGroups()) {
                if(request.isUserInRole(g))
                    l.add(new GrantedAuthorityImpl(g));
            }
            l.add(AUTHENTICATED);
            authorities = l.toArray(new GrantedAuthority[l.size()]);
        }
        return authorities;
    }

    public Object getCredentials() {
        return null;
    }

    public Object getDetails() {
        return null;
    }

    public String getPrincipal() {
        return request.getUserPrincipal().getName();
    }

    public boolean isAuthenticated() {
        return true;
    }

    public void setAuthenticated(boolean isAuthenticated) throws IllegalArgumentException {
        // noop
    }

    public String getName() {
        return getPrincipal();
    }

    private static final GrantedAuthorityImpl AUTHENTICATED = new GrantedAuthorityImpl("authenticated");
}
