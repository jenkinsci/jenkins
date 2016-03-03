/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package hudson.security;

import jenkins.model.Jenkins;
import org.acegisecurity.Authentication;
import org.acegisecurity.GrantedAuthority;
import org.acegisecurity.GrantedAuthorityImpl;

import hudson.model.User;

import javax.servlet.ServletRequest;
import javax.servlet.http.HttpServletRequest;
import java.security.Principal;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.ArrayList;

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

    private final static Logger LOG = Logger.getLogger(ContainerAuthentication.class.getName());

    private final SafePrincipal principal;
    private GrantedAuthority[] authorities;

    /**
     * Servlet container can tie a {@link ServletRequest} to the request handling thread,
     * so we need to capture all the information upfront to allow {@link Authentication}
     * to be passed to other threads, like update center does. See JENKINS-5382. 
     */
    public ContainerAuthentication(HttpServletRequest request) {
        Principal originalPrincipal = request.getUserPrincipal();
        if (originalPrincipal==null) {
            throw new IllegalStateException(); // for anonymous users, we just don't call SecurityContextHolder.getContext().setAuthentication.   
        }
        principal = new SafePrincipal(originalPrincipal.getName());
        // Servlet API doesn't provide a way to list up all roles the current user
        // has, so we need to ask AuthorizationStrategy what roles it is going to check against.
        List<GrantedAuthority> l = new ArrayList<GrantedAuthority>();
        for( String g : Jenkins.getInstance().getAuthorizationStrategy().getGroups()) {
            if(request.isUserInRole(g)) {
                l.add(new GrantedAuthorityImpl(g));
            }
        }
        l.add(SecurityRealm.AUTHENTICATED_AUTHORITY);
        authorities = l.toArray(new GrantedAuthority[l.size()]);
        
        fixupUserFullName();
    }

    public GrantedAuthority[] getAuthorities() {
        return authorities;
    }

    public Object getCredentials() {
        return null;
    }

    public Object getDetails() {
        return null;
    }

    public String getPrincipal() {
        return principal.getName();
    }

    public boolean isAuthenticated() {
        return true;
    }

    public void setAuthenticated(boolean isAuthenticated) throws IllegalArgumentException {
        // noop
    }

    public String getName() {
        return principal.getName();
    }

    /**
     * As we potentially munge the users id from containing unsafe characters we want to set
     * the displayname to be the original un munged id (if the user has not changed it).
     */
    private final void fixupUserFullName() {
        User u = User.get(principal.getName());
        LOG.log(Level.FINEST, "u.id: {1}\nu.fullname: {1}, ca.originalName: {2}", new Object[] {u.getId(), u.getFullName(), principal.getOriginalName()});
        if (u.getId().equals(u.getFullName()) /* default is set*/
                                        && !u.getFullName().equals(principal.getOriginalName())) {
            LOG.log(Level.FINE, "Setting users full name to {}", principal.getOriginalName());
            u.setFullName(principal.getOriginalName());
        }
        else {
            LOG.log(Level.FINE, "Not setting users FullName");
        }
    }

    /**
     * A simple Principal that will replace some potentially unsafe characters with ones that are more safe for Direct use.
     */
    private final static class SafePrincipal implements Principal {

        private final static Logger LOG = Logger.getLogger(ContainerAuthentication.class.getName());

        private String originalName;
        private String name;

        SafePrincipal(String principalName) {
            originalName = principalName;
            // replace any unsafe characters \:/ with underscores
            // unsafe here is something we can't pass down to the filesystem  
            // see IdStrategy.filenameOf(String)
            name = principalName.replaceAll("[\\\\:/]", "_");
            LOG.log(Level.FINE, "Mapped user {0} to {1}", new Object[] {principalName, name});
        }

        @Override
        public String getName() {
            return name;
        }

        String getOriginalName() {
            return originalName;
        }

        @Override
        public String toString() {
            return originalName;
        }

        @Override
        public int hashCode() {
            return originalName.hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            SafePrincipal other = (SafePrincipal) obj;
            if (!getClass().equals(other.getClass()))
                return false;
            if (originalName == null) {
                if (other.originalName != null)
                    return false;
            } else if (!originalName.equals(other.originalName))
                return false;
            return true;
        }
    }
}
