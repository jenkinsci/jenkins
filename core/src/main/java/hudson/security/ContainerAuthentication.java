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

import jakarta.servlet.ServletRequest;
import jakarta.servlet.http.HttpServletRequest;
import java.security.Principal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import jenkins.model.Jenkins;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/**
 * {@link Authentication} implementation for {@link Principal}
 * given through {@link HttpServletRequest}.
 *
 * <p>
 * This is used to plug the container authentication to Spring Security,
 * for backward compatibility with Hudson &lt; 1.160.
 *
 * @author Kohsuke Kawaguchi
 */
@Restricted(NoExternalUse.class)
public final class ContainerAuthentication implements Authentication {
    private final Principal principal;
    private Collection<? extends GrantedAuthority> authorities;

    /**
     * Servlet container can tie a {@link ServletRequest} to the request handling thread,
     * so we need to capture all the information upfront to allow {@link Authentication}
     * to be passed to other threads, like update center does. See JENKINS-5382.
     */
    public ContainerAuthentication(HttpServletRequest request) {
        this.principal = request.getUserPrincipal();
        if (principal == null)
            throw new IllegalStateException(); // for anonymous users, we just don't call SecurityContextHolder.getContext().setAuthentication.

        // Servlet API doesn't provide a way to list up all roles the current user
        // has, so we need to ask AuthorizationStrategy what roles it is going to check against.
        List<GrantedAuthority> l = new ArrayList<>();
        for (String g : Jenkins.get().getAuthorizationStrategy().getGroups()) {
            if (request.isUserInRole(g))
                l.add(new SimpleGrantedAuthority(g));
        }
        l.add(SecurityRealm.AUTHENTICATED_AUTHORITY2);
        authorities = l;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public Object getCredentials() {
        return null;
    }

    @Override
    public Object getDetails() {
        return null;
    }

    @Override
    public String getPrincipal() {
        return principal.getName();
    }

    @Override
    public boolean isAuthenticated() {
        return true;
    }

    @Override
    public void setAuthenticated(boolean isAuthenticated) throws IllegalArgumentException {
        // noop
    }

    @Override
    public String getName() {
        return getPrincipal();
    }
}
