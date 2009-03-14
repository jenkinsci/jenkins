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

import org.acegisecurity.AccessDeniedException;
import org.acegisecurity.Authentication;
import org.acegisecurity.providers.UsernamePasswordAuthenticationToken;
import org.acegisecurity.acls.sid.PrincipalSid;
import org.acegisecurity.acls.sid.Sid;
import hudson.model.Hudson;
import hudson.model.Executor;

/**
 * Gate-keeper that controls access to Hudson's model objects.
 *
 * @author Kohsuke Kawaguchi
 * @see http://hudson.gotdns.com/wiki/display/HUDSON/Making+your+plugin+behave+in+secured+Hudson
 */
public abstract class ACL {
    /**
     * Checks if the current security principal has this permission.
     *
     * <p>
     * This is just a convenience function.
     *
     * @throws AccessDeniedException
     *      if the user doesn't have the permission.
     */
    public final void checkPermission(Permission p) {
        Authentication a = Hudson.getAuthentication();
        if(!hasPermission(a,p))
            throw new AccessDeniedException2(a,p);
    }

    /**
     * Checks if the current security principal has this permission.
     *
     * @return false
     *      if the user doesn't have the permission.
     */
    public final boolean hasPermission(Permission p) {
        return hasPermission(Hudson.getAuthentication(),p);
    }

    /**
     * Checks if the given principle has the given permission.
     *
     * <p>
     * Note that {@link #SYSTEM} can be passed in as the authentication parameter,
     * in which case you should probably just assume it has every permission.
     */
    public abstract boolean hasPermission(Authentication a, Permission permission);

    //
    // Sid constants
    //

    /**
     * Special {@link Sid} that represents "everyone", even including anonymous users.
     *
     * <p>
     * This doesn't need to be included in {@link Authentication#getAuthorities()},
     * but {@link ACL} is responsible for checking it nontheless, as if it was the
     * last entry in the granted authority.
     */
    public static final Sid EVERYONE = new Sid() {};

    /**
     * {@link Sid} that represents the anonymous unauthenticated users.
     * <p>
     * {@link HudsonFilter} sets this up, so this sid remains the same
     * regardless of the current {@link SecurityRealm} in use.
     */
    public static final Sid ANONYMOUS = new PrincipalSid("anonymous");

    /**
     * {@link Sid} that represents the Hudson itself.
     * <p>
     * This is used when Hudson is performing computation for itself, instead
     * of acting on behalf of an user, such as doing builds.
     *
     * <p>
     * (Note that one of the features being considered is to keep track of who triggered
     * a build &mdash; so in a future, perhaps {@link Executor} will run on behalf of
     * the user who triggered a build.)
     */
    public static final Authentication SYSTEM = new UsernamePasswordAuthenticationToken("SYSTEM","SYSTEM");
}
